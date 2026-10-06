package dev.galaxyexplorer.ingestion.connector.simbad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.galaxyexplorer.catalog.Epistemic;
import dev.galaxyexplorer.common.Texts;
import dev.galaxyexplorer.ingestion.connector.FetchedBatch;
import dev.galaxyexplorer.ingestion.connector.NormalizationOutcome;
import dev.galaxyexplorer.ingestion.connector.NormalizedIdentifier;
import dev.galaxyexplorer.ingestion.connector.NormalizedMeasurement;
import dev.galaxyexplorer.ingestion.connector.NormalizedObject;
import dev.galaxyexplorer.ingestion.connector.NormalizedPosition;
import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.ResolvedName;
import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.tap.TapColumn;
import dev.galaxyexplorer.ingestion.connector.tap.TapJsonParser;
import dev.galaxyexplorer.ingestion.connector.tap.TapResult;
import dev.galaxyexplorer.ingestion.connector.tap.TapRow;

/**
 * Respostas cruas do SIMBAD -> modelo canonico. PURO: sem rede, banco ou relogio.
 *
 * <p>Regra de descarte: nenhum valor que a fonte mandou some em silencio. Todo
 * campo numerico passa por {@link #numeric} (texto nao numerico -> {@code issue})
 * e toda incerteza por {@link #nonNegativeError} (negativa -> {@code issue}); a
 * incerteza como veio fica em {@code originalError} mesmo quando descartada, para
 * a leitura nao dizer "sem incerteza publicada" quando a fonte publicou uma invalida.
 *
 * <p>Fatos do dado real (fixtures de 2026-10-05/06) que este codigo respeita:
 * <ul>
 *   <li>{@code rvz_type}: {@code 'v'} (velocidade medida), {@code 'z'} (redshift
 *       medido) ou {@code 'c'} (cz medido). O valor medido vira {@code observed}
 *       com {@code rvz_err}; o outro, {@code derived_by_source} sem erro.</li>
 *   <li>{@code 'c'} = cz: o SIMBAD guarda z = cz/c em {@code rvz_redshift} e a
 *       velocidade por conversao RELATIVISTICA de z, nao cz. M51: z = 0.0017445402,
 *       c*z = 523.0 km/s, mas c((1+z)^2-1)/((1+z)^2+1) = 522.5438 = rvz_radvel.
 *       Logo o medido e o redshift; a velocidade e derivada (vale tambem para
 *       {@code 'z'}: M82, z = 7.3E-4 -> 218.7686 = rvz_radvel).</li>
 *   <li>{@code rvz_err} tem {@code unit = km.s-1} no metadata, mas com
 *       {@code rvz_type} {@code 'z'} ou {@code 'c'} o valor e erro em z (M82:
 *       9.0E-5; M51: 6.671282E-6). Por isso so e aplicado a quantidade medida.</li>
 *   <li>{@code mesDistance.unit} e {@code method} vem com padding
 *       ({@code 'Mpc '}, {@code 'T-RGB   '}) e {@code minus_err} vem
 *       NEGATIVO ({@code -0.08} / {@code +0.08}); guardamos a magnitude.</li>
 *   <li>Uma mesma referencia pode publicar duas distancias pelo mesmo metodo
 *       (M31, 2015MNRAS.451..724W, Cep: 731 e 797 kpc): mantemos a primeira e
 *       registramos {@code issue}.</li>
 * </ul>
 */
public final class SimbadMapper {

    static final String OBJECT_URL = "https://simbad.cds.unistra.fr/simbad/sim-id?Ident=";
    static final String RELATIVISTIC_FROM_Z = "relativistic conversion from z by SIMBAD";

    private static final String BASIC = SimbadQueries.DS_BASIC;

    private SimbadMapper() {
    }

    public static List<NormalizationOutcome> normalize(FetchedBatch batch) {
        Map<String, TapResult> results = new LinkedHashMap<>();
        List<String> unitProblems = new ArrayList<>();
        for (String dataset : List.of(SimbadQueries.DS_BASIC, SimbadQueries.DS_IDENT, SimbadQueries.DS_FLUX,
                SimbadQueries.DS_MES_DISTANCE)) {
            RawPayload payload = batch.payloadsByDataset().get(dataset);
            if (payload == null) {
                throw new SourceQueryException("lote SIMBAD sem o payload '" + dataset + "'");
            }
            TapResult result = TapJsonParser.parse(payload.body());
            unitProblems.addAll(SimbadSchemaExpectations.unitProblems(dataset, result));
            results.put(dataset, result);
        }
        List<NormalizationOutcome> outcomes = new ArrayList<>();
        if (!unitProblems.isEmpty()) {
            // Melhor rejeitar o lote do que gravar valor 1000x errado.
            String reason = String.join("; ", unitProblems);
            batch.resolved().forEach(r -> outcomes.add(new NormalizationOutcome.Rejected(r, reason)));
            return outcomes;
        }
        Map<Long, TapRow> basicByOid = new HashMap<>();
        for (TapRow row : results.get(SimbadQueries.DS_BASIC).rows()) {
            row.integer("oid").ifPresent(oid -> basicByOid.put(oid, row));
        }
        Map<Long, List<TapRow>> identByOid = groupByOidref(results.get(SimbadQueries.DS_IDENT));
        Map<Long, List<TapRow>> fluxByOid = groupByOidref(results.get(SimbadQueries.DS_FLUX));
        Map<Long, List<TapRow>> distByOid = groupByOidref(results.get(SimbadQueries.DS_MES_DISTANCE));
        String radvelUnit = results.get(SimbadQueries.DS_BASIC).column("rvz_radvel").map(TapColumn::unit)
                .orElse(null);

        for (ResolvedName name : batch.resolved()) {
            long oid = name.ref().sourceInternalId();
            TapRow basic = basicByOid.get(oid);
            if (basic == null) {
                outcomes.add(new NormalizationOutcome.Rejected(name, "oid " + oid + " ausente na resposta basic"));
                continue;
            }
            List<String> fatal = SimbadValidator.validateBasic(basic);
            if (!fatal.isEmpty()) {
                outcomes.add(new NormalizationOutcome.Rejected(name, String.join("; ", fatal)));
                continue;
            }
            outcomes.add(new NormalizationOutcome.Accepted(toObject(name, basic,
                    identByOid.getOrDefault(oid, List.of()), fluxByOid.getOrDefault(oid, List.of()),
                    distByOid.getOrDefault(oid, List.of()), radvelUnit)));
        }
        return outcomes;
    }

    private static NormalizedObject toObject(ResolvedName name, TapRow basic, List<TapRow> idents,
                                             List<TapRow> fluxes, List<TapRow> distances, String radvelUnit) {
        String mainId = basic.text("main_id").orElseThrow(); // validado em validateBasic
        String label = Texts.collapseWhitespace(mainId);
        List<String> issues = new ArrayList<>();
        List<NormalizedMeasurement> measurements = new ArrayList<>();

        Optional<NormalizedPosition> position = position(basic, label, issues);
        parallax(basic, label, measurements, issues);
        velocityAndRedshift(basic, radvelUnit, label, measurements, issues);
        text(basic, "sp_type", "sp_bibcode", "spectral_type", measurements);
        text(basic, "morph_type", "morph_bibcode", "morph_type", measurements);
        angularSize(basic, "galdim_majaxis", "ang_size_major", label, measurements, issues);
        angularSize(basic, "galdim_minaxis", "ang_size_minor", label, measurements, issues);
        magnitudes(fluxes, label, measurements, issues);
        distances(distances, label, measurements, issues);

        List<NormalizedIdentifier> identifiers = new ArrayList<>();
        for (TapRow ident : idents) {
            ident.text("id").ifPresent(id -> identifiers.add(new NormalizedIdentifier(id, id.startsWith("NAME "))));
        }
        return new NormalizedObject(name.ref(), mainId, name.queries(), basic.text("otype").orElseThrow(),
                basic.text("morph_type").orElse(null), objectUrl(mainId), identifiers, position, measurements,
                issues);
    }

    static String objectUrl(String mainId) {
        return OBJECT_URL + Texts.urlEncode(mainId);
    }

    // ── extracao com descarte explicito ─────────────────────────────────────

    /** Numero da coluna; valor presente mas nao numerico vira {@code issue} (nunca some em silencio). */
    static Optional<Double> numeric(TapRow row, String column, String label, List<String> issues) {
        Optional<Double> n = row.number(column);
        if (n.isEmpty() && row.text(column).isPresent()) {
            issues.add(label + ": " + column + " descartado: valor não numérico '" + row.rawText(column).orElse("")
                    + "'");
        }
        return n;
    }

    /** Incerteza da coluna; negativa ou nao numerica vira {@code issue} e {@code null}. */
    static Double nonNegativeError(TapRow row, String column, String label, List<String> issues) {
        Double err = numeric(row, column, label, issues).orElse(null);
        if (err != null && err < 0) {
            issues.add(label + ": " + column + " negativo descartado: " + row.rawText(column).orElse(""));
            return null;
        }
        return err;
    }

    private static String rawOrNull(TapRow row, String column) {
        return row.rawText(column).filter(s -> !s.isBlank()).orElse(null);
    }

    private static Optional<NormalizedPosition> position(TapRow basic, String label, List<String> issues) {
        Optional<Double> ra = numeric(basic, "ra", label, issues);
        Optional<Double> dec = numeric(basic, "dec", label, issues);
        if (ra.isEmpty() || dec.isEmpty()) {
            issues.add(label + ": posição ausente em basic");
            return Optional.empty();
        }
        List<String> problems = new ArrayList<>();
        SimbadValidator.checkRa(ra.get()).ifPresent(problems::add);
        SimbadValidator.checkDec(dec.get()).ifPresent(problems::add);
        if (!problems.isEmpty()) {
            issues.add(label + ": posição descartada: " + String.join("; ", problems));
            return Optional.empty();
        }
        return Optional.of(new NormalizedPosition(ra.get(), dec.get(), "ICRS", "J2000",
                basic.text("coo_bibcode").orElse(null), BASIC));
    }

    private static void parallax(TapRow basic, String label, List<NormalizedMeasurement> out, List<String> issues) {
        Optional<Double> plx = numeric(basic, "plx_value", label, issues);
        if (plx.isEmpty()) {
            return;
        }
        out.add(NormalizedMeasurement.builder("parallax", Epistemic.OBSERVED, BASIC, BASIC)
                .value(plx.get())
                .symmetricError(nonNegativeError(basic, "plx_err", label, issues))
                .original(basic.rawText("plx_value").orElseThrow(), "mas")
                .originalError(rawOrNull(basic, "plx_err"))
                .bibcode(basic.text("plx_bibcode").orElse(null))
                .build());
    }

    private static void velocityAndRedshift(TapRow basic, String radvelUnit, String label,
                                            List<NormalizedMeasurement> out, List<String> issues) {
        Optional<Double> z = numeric(basic, "rvz_redshift", label, issues);
        Optional<Double> v = numeric(basic, "rvz_radvel", label, issues);
        if (z.isEmpty() && v.isEmpty()) {
            return;
        }
        String type = basic.text("rvz_type").orElse(null);
        boolean zMeasured = "z".equals(type) || "c".equals(type); // 'c' = cz: o medido e z (ver Javadoc)
        boolean vMeasured = "v".equals(type);
        if (!zMeasured && !vMeasured) {
            issues.add(label + ": redshift/velocidade descartados: rvz_type '" + type + "' desconhecido");
            return;
        }
        Double err = nonNegativeError(basic, "rvz_err", label, issues);
        String rawErr = rawOrNull(basic, "rvz_err");
        String bibcode = basic.text("rvz_bibcode").orElse(null);
        String method = "SIMBAD rvz_type=" + type;
        if (z.isPresent()) {
            Optional<String> problem = SimbadValidator.checkRedshift(z.get());
            if (problem.isPresent()) {
                issues.add(label + ": " + problem.get());
            } else {
                out.add(NormalizedMeasurement.builder("redshift",
                                zMeasured ? Epistemic.OBSERVED : Epistemic.DERIVED_BY_SOURCE, BASIC, BASIC)
                        .value(z.get())
                        .symmetricError(zMeasured ? err : null)
                        .original(basic.rawText("rvz_redshift").orElseThrow(), null)
                        .originalError(zMeasured ? rawErr : null)
                        .method(method)
                        .bibcode(bibcode)
                        .build());
            }
        }
        if (v.isPresent()) {
            SimbadUnits.canonicalUnitFor(radvelUnit); // valida a grafia; km.s-1 ja e a unidade canonica (km/s)
            out.add(NormalizedMeasurement.builder("radial_velocity",
                            vMeasured ? Epistemic.OBSERVED : Epistemic.DERIVED_BY_SOURCE, BASIC, BASIC)
                    .value(v.get())
                    .symmetricError(vMeasured ? err : null)
                    .original(basic.rawText("rvz_radvel").orElseThrow(), radvelUnit)
                    .originalError(vMeasured ? rawErr : null)
                    .method(zMeasured ? RELATIVISTIC_FROM_Z + " (rvz_type=" + type + ")" : method)
                    .bibcode(bibcode)
                    .build());
        }
    }

    private static void text(TapRow basic, String column, String bibcodeColumn, String property,
                             List<NormalizedMeasurement> out) {
        basic.text(column).ifPresent(value -> out.add(
                NormalizedMeasurement.builder(property, Epistemic.OBSERVED, BASIC, BASIC)
                        .text(value)
                        .original(basic.rawText(column).orElseThrow(), null)
                        .bibcode(basic.text(bibcodeColumn).orElse(null))
                        .build()));
    }

    private static void angularSize(TapRow basic, String column, String property, String label,
                                    List<NormalizedMeasurement> out, List<String> issues) {
        numeric(basic, column, label, issues).ifPresent(value -> out.add(
                NormalizedMeasurement.builder(property, Epistemic.OBSERVED, BASIC, BASIC)
                        .value(value)
                        .original(basic.rawText(column).orElseThrow(), "arcmin")
                        .bibcode(basic.text("galdim_bibcode").orElse(null))
                        .build()));
    }

    private static void magnitudes(List<TapRow> fluxes, String label, List<NormalizedMeasurement> out,
                                   List<String> issues) {
        Set<String> seenBands = new HashSet<>();
        for (TapRow flux : fluxes) {
            Optional<String> band = flux.text("filter");
            if (band.isEmpty()) {
                issues.add(label + ": flux sem filtro descartado");
                continue;
            }
            String where = label + " [" + band.get() + "]";
            Optional<Double> mag = numeric(flux, "flux", where, issues);
            if (mag.isEmpty()) {
                continue;
            }
            Optional<String> range = SimbadValidator.checkMagnitude(band.get(), mag.get());
            if (range.isPresent()) {
                issues.add(label + ": " + range.get());
                continue;
            }
            if (!seenBands.add(band.get())) {
                issues.add(label + ": flux " + band.get() + " duplicado; mantido o primeiro");
                continue;
            }
            out.add(NormalizedMeasurement.builder("app_mag", Epistemic.OBSERVED, SimbadQueries.DS_FLUX,
                            SimbadQueries.DS_FLUX)
                    .qualifier(band.get())
                    .value(mag.get())
                    .symmetricError(nonNegativeError(flux, "flux_err", where, issues))
                    .original(flux.rawText("flux").orElseThrow(), null)
                    .originalError(rawOrNull(flux, "flux_err"))
                    .bibcode(flux.text("bibcode").orElse(null))
                    .build());
        }
    }

    private static void distances(List<TapRow> rows, String label, List<NormalizedMeasurement> out,
                                  List<String> issues) {
        Set<String> seenDatasets = new HashSet<>();
        for (TapRow row : rows) {
            String unit = row.text("unit").orElse(null);
            String bibcode = row.text("bibcode").orElse(null);
            String method = row.text("method").orElse(null);
            String where = label + " mesDistance " + bibcode + (method == null ? "" : " (" + method + ")");
            Optional<Double> dist = numeric(row, "dist", where, issues);
            if (dist.isEmpty()) {
                if (row.text("dist").isEmpty()) {
                    issues.add(where + ": sem valor");
                }
                continue;
            }
            if (dist.get() <= 0.0) {
                issues.add(where + " descartada: distância não positiva " + dist.get());
                continue;
            }
            Optional<Double> pc = SimbadUnits.distanceToParsecs(dist.get(), unit);
            if (pc.isEmpty()) {
                issues.add(where + " descartada: unidade desconhecida '" + unit + "'");
                continue;
            }
            String dataset = "mesDistance:" + (bibcode == null ? "" : bibcode) + ":" + (method == null ? "" : method);
            if (!seenDatasets.add(dataset)) {
                issues.add(where + " repetida na mesma referência/método; mantida a primeira");
                continue;
            }
            double factor = pc.get() / dist.get();
            // minus_err vem negativo por convencao do SIMBAD: a magnitude e o erro; nao passa por nonNegativeError
            Double errMinus = numeric(row, "minus_err", where, issues).map(e -> Math.abs(e) * factor).orElse(null);
            Double errPlus = numeric(row, "plus_err", where, issues).map(e -> Math.abs(e) * factor).orElse(null);
            out.add(NormalizedMeasurement.builder("distance", Epistemic.DERIVED_BY_SOURCE, dataset,
                            SimbadQueries.DS_MES_DISTANCE)
                    .value(pc.get())
                    .errors(errMinus, errPlus)
                    .original(row.rawText("dist").orElseThrow(), unit)
                    .originalError(asymmetricOriginal(row))
                    .method(method)
                    .bibcode(bibcode)
                    .build());
        }
    }

    /** {@code 'minus_err=-30; plus_err=30'}; {@code null} se a fonte nao publicou nenhum dos dois. */
    private static String asymmetricOriginal(TapRow row) {
        String minus = rawOrNull(row, "minus_err");
        String plus = rawOrNull(row, "plus_err");
        if (minus == null && plus == null) {
            return null;
        }
        return "minus_err=" + (minus == null ? "" : minus) + "; plus_err=" + (plus == null ? "" : plus);
    }

    private static Map<Long, List<TapRow>> groupByOidref(TapResult result) {
        Map<Long, List<TapRow>> byOid = new LinkedHashMap<>();
        for (TapRow row : result.rows()) {
            row.integer("oidref").ifPresent(oid -> byOid.computeIfAbsent(oid, k -> new ArrayList<>()).add(row));
        }
        return byOid;
    }
}

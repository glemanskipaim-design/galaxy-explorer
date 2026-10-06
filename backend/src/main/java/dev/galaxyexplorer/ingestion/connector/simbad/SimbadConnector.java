package dev.galaxyexplorer.ingestion.connector.simbad;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.common.Texts;
import dev.galaxyexplorer.ingestion.connector.FetchedBatch;
import dev.galaxyexplorer.ingestion.connector.NormalizationOutcome;
import dev.galaxyexplorer.ingestion.connector.RawPayload;
import dev.galaxyexplorer.ingestion.connector.Resolution;
import dev.galaxyexplorer.ingestion.connector.ResolvedName;
import dev.galaxyexplorer.ingestion.connector.SchemaCheck;
import dev.galaxyexplorer.ingestion.connector.SourceConnector;
import dev.galaxyexplorer.ingestion.connector.SourceObjectRef;
import dev.galaxyexplorer.ingestion.connector.SourceQueryException;
import dev.galaxyexplorer.ingestion.connector.UnresolvedQuery;
import dev.galaxyexplorer.ingestion.connector.tap.TapClient;
import dev.galaxyexplorer.ingestion.connector.tap.TapJsonParser;

/**
 * Conector do SIMBAD.
 * <ul>
 *   <li>resolucao de nomes pelo Sesame do CDS (uma requisicao por lote) —
 *       o {@code ident.id} do SIMBAD tem padding proprio ({@code 'M   1'}),
 *       entao comparar texto no ADQL nao funciona para nomes "naturais";</li>
 *   <li>dados por TAP, sempre filtrando por oid numerico (texto do usuario
 *       nunca entra no ADQL).</li>
 * </ul>
 */
@Component
public class SimbadConnector implements SourceConnector {

    public static final String VERSION = "simbad-tap/2";
    static final String NOT_FOUND = "não encontrado em SIMBAD (resolvedor Sesame do CDS)";

    private final TapClient tap;
    private final SesameClient sesame;
    private final SimbadProperties props;

    public SimbadConnector(@Qualifier("simbadTapClient") TapClient tap,
                           @Qualifier("simbadSesameClient") SesameClient sesame, SimbadProperties props) {
        this.tap = tap;
        this.sesame = sesame;
        this.props = props;
    }

    @Override
    public SourceCode code() {
        return SourceCode.SIMBAD;
    }

    @Override
    public String connectorVersion() {
        return VERSION;
    }

    @Override
    public SchemaCheck checkSchema() {
        return SimbadSchemaExpectations.evaluate(tap.query(SimbadQueries.DS_TAP_SCHEMA, SimbadQueries.schemaCheck()));
    }

    @Override
    public Resolution resolve(List<String> userQueries) {
        List<UnresolvedQuery> unresolved = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (String query : new LinkedHashSet<>(userQueries)) {
            if (query == null || query.isBlank()) {
                unresolved.add(new UnresolvedQuery(String.valueOf(query), "consulta vazia"));
            } else if (query.chars().anyMatch(Character::isISOControl)) {
                unresolved.add(new UnresolvedQuery(query, "consulta com caractere de controle"));
            } else {
                names.add(query.trim());
            }
        }
        if (names.isEmpty()) {
            throw new SourceQueryException("nenhuma consulta válida para resolver no Sesame");
        }
        RawPayload raw = sesame.resolve(names);
        List<SesameTarget> targets = SesameXmlParser.parse(raw.body());
        if (targets.size() != names.size()) {
            throw new SourceQueryException("Sesame devolveu " + targets.size() + " alvos para " + names.size()
                    + " nomes");
        }
        Map<Long, List<String>> queriesByOid = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            SesameTarget target = targets.get(i);
            if (!sameName(name, target.name())) {
                throw new SourceQueryException("Sesame fora de ordem: pedido '" + name + "', veio '" + target.name()
                        + "'");
            }
            if (!target.found()) {
                unresolved.add(new UnresolvedQuery(name, NOT_FOUND));
                continue;
            }
            queriesByOid.computeIfAbsent(target.oid(), k -> new ArrayList<>()).add(name);
        }
        // So o oid sai da resolucao. O nome principal vem de basic.main_id (validado no mapper);
        // o <oname> do Sesame fica no raw_payload para diagnostico.
        List<ResolvedName> resolved = new ArrayList<>();
        queriesByOid.forEach((oid, queries) ->
                resolved.add(new ResolvedName(queries, new SourceObjectRef(SourceCode.SIMBAD, oid))));
        return new Resolution(resolved, unresolved, raw);
    }

    @Override
    public FetchedBatch fetch(List<ResolvedName> resolved) {
        Set<Long> oids = new LinkedHashSet<>();
        resolved.forEach(r -> oids.add(r.ref().sourceInternalId()));
        Map<String, RawPayload> payloads = new LinkedHashMap<>();
        payloads.put(SimbadQueries.DS_BASIC, checked(tap.query(SimbadQueries.DS_BASIC, SimbadQueries.basic(oids))));
        payloads.put(SimbadQueries.DS_IDENT,
                checked(tap.query(SimbadQueries.DS_IDENT, SimbadQueries.identifiers(oids))));
        payloads.put(SimbadQueries.DS_FLUX, checked(tap.query(SimbadQueries.DS_FLUX, SimbadQueries.fluxes(oids))));
        payloads.put(SimbadQueries.DS_MES_DISTANCE,
                checked(tap.query(SimbadQueries.DS_MES_DISTANCE, SimbadQueries.distances(oids))));
        return new FetchedBatch(resolved, payloads);
    }

    @Override
    public List<NormalizationOutcome> normalize(FetchedBatch batch) {
        return SimbadMapper.normalize(batch);
    }

    /** Resultado com exatamente MAXREC linhas foi (provavelmente) cortado: nunca aceitar como completo. */
    private RawPayload checked(RawPayload payload) {
        int rows = TapJsonParser.parse(payload.body()).size();
        if (rows >= props.maxRec()) {
            throw new SourceQueryException("resultado '" + payload.dataset() + "' truncado em MAXREC=" + props.maxRec()
                    + " — reduza galaxy.ingestion.batch-size");
        }
        return payload;
    }

    private static boolean sameName(String asked, String echoed) {
        return echoed != null && collapse(asked).equals(collapse(echoed));
    }

    private static String collapse(String s) {
        return Texts.collapseWhitespace(s);
    }
}

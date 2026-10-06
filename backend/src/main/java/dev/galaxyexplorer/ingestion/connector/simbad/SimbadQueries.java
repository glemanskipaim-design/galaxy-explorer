package dev.galaxyexplorer.ingestion.connector.simbad;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import dev.galaxyexplorer.ingestion.connector.tap.AdqlLiterals;

/**
 * Consultas ADQL do SIMBAD. Puras e deterministicas (uma linha; IN-lists de
 * oids em ordem crescente), para bater com os {@code .adql} das fixtures.
 *
 * <p>Fatos do servico observados ao vivo: {@code ORDER BY} com nome
 * qualificado ({@code b.oid}) e recusado; nomes simples funcionam.
 */
public final class SimbadQueries {

    public static final String DS_TAP_SCHEMA = "tap_schema";
    public static final String DS_BASIC = "basic";
    public static final String DS_IDENT = "ident";
    public static final String DS_FLUX = "flux";
    public static final String DS_MES_DISTANCE = "mesDistance";

    public static final List<String> BASIC_COLUMNS = List.of("oid", "main_id", "otype", "ra", "dec", "coo_bibcode",
            "plx_value", "plx_err", "plx_bibcode", "rvz_redshift", "rvz_radvel", "rvz_type", "rvz_err", "rvz_bibcode",
            "sp_type", "sp_bibcode", "morph_type", "morph_bibcode", "galdim_majaxis", "galdim_minaxis",
            "galdim_bibcode");
    public static final List<String> IDENT_COLUMNS = List.of("oidref", "id");
    public static final List<String> FLUX_COLUMNS = List.of("oidref", "filter", "flux", "flux_err", "bibcode");
    public static final List<String> MES_DISTANCE_COLUMNS = List.of("oidref", "dist", "unit", "minus_err",
            "plus_err", "method", "bibcode");

    /** Tabela SIMBAD -> colunas que consultamos. A checagem de esquema confere todas. */
    public static final Map<String, List<String>> COLUMNS_BY_TABLE = Map.of(
            DS_BASIC, BASIC_COLUMNS,
            DS_IDENT, IDENT_COLUMNS,
            DS_FLUX, FLUX_COLUMNS,
            DS_MES_DISTANCE, MES_DISTANCE_COLUMNS);

    private static final List<String> TABLES = List.of(DS_BASIC, DS_IDENT, DS_FLUX, DS_MES_DISTANCE);

    private SimbadQueries() {
    }

    public static String schemaCheck() {
        return "SELECT table_name, column_name, unit, datatype FROM TAP_SCHEMA.columns WHERE table_name IN "
                + AdqlLiterals.inList(TABLES) + " ORDER BY table_name, column_name";
    }

    public static String basic(Collection<Long> oids) {
        return "SELECT " + String.join(", ", BASIC_COLUMNS) + " FROM basic WHERE oid IN " + sorted(oids);
    }

    public static String identifiers(Collection<Long> oids) {
        return "SELECT " + String.join(", ", IDENT_COLUMNS) + " FROM ident WHERE oidref IN " + sorted(oids)
                + " ORDER BY oidref, id";
    }

    public static String fluxes(Collection<Long> oids) {
        return "SELECT " + String.join(", ", FLUX_COLUMNS) + " FROM flux WHERE oidref IN " + sorted(oids)
                + " ORDER BY oidref, filter";
    }

    public static String distances(Collection<Long> oids) {
        return "SELECT " + String.join(", ", MES_DISTANCE_COLUMNS) + " FROM mesDistance WHERE oidref IN "
                + sorted(oids) + " ORDER BY oidref, bibcode, method";
    }

    private static String sorted(Collection<Long> oids) {
        return AdqlLiterals.inListNumeric(new TreeSet<>(oids));
    }
}

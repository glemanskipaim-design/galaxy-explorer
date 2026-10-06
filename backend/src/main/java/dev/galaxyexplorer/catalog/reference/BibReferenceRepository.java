package dev.galaxyexplorer.catalog.reference;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acesso a {@code bib_reference}. No Bloco 1 so o bibcode e conhecido
 * (titulo/autores/DOI ficam NULL ate existir integracao com ADS).
 */
@Repository
public class BibReferenceRepository {

    private final JdbcClient jdbc;

    public BibReferenceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Garante uma linha por bibcode e devolve {@code bibcode -> id}. O
     * {@code DO UPDATE} que nao muda nada e o truque para o {@code RETURNING}
     * devolver o id tambem quando a linha ja existia.
     */
    public Map<String, Long> upsertBibcodes(Set<String> bibcodes) {
        Map<String, Long> ids = new HashMap<>();
        for (String bibcode : new TreeSet<>(bibcodes)) {
            Long id = jdbc.sql("""
                    INSERT INTO bib_reference (bibcode) VALUES (:bibcode)
                    ON CONFLICT (bibcode) DO UPDATE SET bibcode = EXCLUDED.bibcode
                    RETURNING id
                    """)
                    .param("bibcode", bibcode)
                    .query(Long.class)
                    .single();
            ids.put(bibcode, id);
        }
        return ids;
    }
}

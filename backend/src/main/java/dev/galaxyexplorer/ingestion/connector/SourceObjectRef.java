package dev.galaxyexplorer.ingestion.connector;

import dev.galaxyexplorer.catalog.SourceCode;

/**
 * Referencia a um objeto dentro de uma fonte. Nao carrega nome: o nome
 * principal vem do payload de dados validado ({@link NormalizedObject#mainId()}),
 * nao da resolucao.
 *
 * @param sourceInternalId id do objeto na fonte (oid do SIMBAD). Usado para
 *                         juntar tabelas no mesmo run e, como texto, persistido
 *                         em {@code object_source_type.source_ref} (identidade
 *                         entre execucoes).
 */
public record SourceObjectRef(SourceCode source, long sourceInternalId) {

    public String sourceRef() {
        return Long.toString(sourceInternalId);
    }
}

package dev.galaxyexplorer.ingestion.connector.simbad;

/**
 * Um {@code <Target>} da resposta do Sesame.
 *
 * @param oid   oid do SIMBAD (o mesmo de {@code basic.oid}); {@code null} = nao encontrado
 * @param oname identificador principal no SIMBAD ({@code main_id}); {@code null} se nao encontrado
 * @param otype pode ser {@code null}
 * @param info  texto de {@code <INFO>} do alvo (ex. {@code *** NNNothing found ***}); pode ser {@code null}
 */
public record SesameTarget(String name, Long oid, String oname, String otype, String info) {

    public boolean found() {
        return oid != null;
    }
}

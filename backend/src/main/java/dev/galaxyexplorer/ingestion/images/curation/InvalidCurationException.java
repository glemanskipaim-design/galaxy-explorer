package dev.galaxyexplorer.ingestion.images.curation;

import java.util.List;

/** {@code images/curation.csv} invalido: o comando nao roda (lista completa de problemas na mensagem). */
public class InvalidCurationException extends RuntimeException {

    private final transient List<String> problems;

    public InvalidCurationException(List<String> problems) {
        super("curadoria de imagens inválida:\n  " + String.join("\n  ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}

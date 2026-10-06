package dev.galaxyexplorer.catalog.read;

/** Por que uma propriedade nao tem valor. Ausencia e estado de primeira classe, nunca "0" ou "—". */
public enum AbsenceReason {
    NOT_PROVIDED_BY_SOURCE("Não disponível na fonte"),
    PARALLAX_SNR_BELOW_5("Paralaxe com S/N abaixo de 5 — distância não calculada"),
    PARALLAX_WITHOUT_UNCERTAINTY("Paralaxe sem incerteza publicada — distância não calculada"),
    /** A fonte publicou uma incerteza, mas invalida (ex. negativa): foi descartada na ingestao. */
    PARALLAX_UNCERTAINTY_INVALID("Incerteza da paralaxe publicada pela fonte é inválida — distância não calculada"),
    PARALLAX_NON_POSITIVE("Paralaxe não positiva — distância não calculada");

    private final String messagePt;

    AbsenceReason(String messagePt) {
        this.messagePt = messagePt;
    }

    public String messagePt() {
        return messagePt;
    }
}

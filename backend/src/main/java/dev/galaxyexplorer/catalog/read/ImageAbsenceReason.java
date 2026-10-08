package dev.galaxyexplorer.catalog.read;

/** Por que um objeto nao tem imagem. "Imagem não disponível" explicito, nunca um desenho no lugar. */
public enum ImageAbsenceReason {
    NO_POSITION("Imagem não disponível: objeto sem posição catalogada — não há como gerar um recorte"),
    NOT_AVAILABLE("Imagem não disponível: nenhuma imagem com crédito e licença verificados foi obtida para este objeto");

    private final String messagePt;

    ImageAbsenceReason(String messagePt) {
        this.messagePt = messagePt;
    }

    public String messagePt() {
        return messagePt;
    }
}

package dev.galaxyexplorer.media;

/** Um arquivo gravado por {@link ImageFileStore}: chave relativa ({@code ab/<sha>.jpg}), sha256 e tamanho. */
public record StoredFile(String storageKey, String sha256, int byteSize) {
}

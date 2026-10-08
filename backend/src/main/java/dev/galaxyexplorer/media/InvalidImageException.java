package dev.galaxyexplorer.media;

/** Bytes que nao sao um JPEG aceitavel (tipo falso, cabecalho ilegivel, dimensoes acima do limite). */
public class InvalidImageException extends RuntimeException {

    public InvalidImageException(String message) {
        super(message);
    }

    public InvalidImageException(String message, Throwable cause) {
        super(message, cause);
    }
}

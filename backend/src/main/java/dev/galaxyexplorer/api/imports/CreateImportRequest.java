package dev.galaxyexplorer.api.imports;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Termo a importar. Charset restrito: letras/digitos Unicode, espaco e
 * {@code . + - * ' _ ( ) / [ ] :} (cobre nomes reais como {@code Sgr A*},
 * {@code Barnard's Star}, {@code 2MASS J05551028+0724255}). O termo nunca entra
 * em ADQL — vai so ao Sesame, URL-encoded —, mas a validacao limita a superficie.
 */
public record CreateImportRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "^[\\p{L}\\p{N} .+\\-*'_()/\\[\\]:]+$",
                message = "use apenas letras, dígitos, espaço e . + - * ' _ ( ) / [ ] :")
        String query) {
}

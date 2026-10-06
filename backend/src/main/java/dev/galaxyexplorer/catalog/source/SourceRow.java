package dev.galaxyexplorer.catalog.source;

/**
 * Uma fonte de dados com o credito exigido por ela.
 *
 * @param termsUrl       pode ser {@code null}
 * @param acknowledgment pode ser {@code null}
 */
public record SourceRow(short id, String code, String name, String homepageUrl, String termsUrl,
                        String acknowledgment) {
}

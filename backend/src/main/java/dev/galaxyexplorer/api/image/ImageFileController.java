package dev.galaxyexplorer.api.image;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.galaxyexplorer.api.error.ApiErrorCode;
import dev.galaxyexplorer.api.error.ApiException;
import dev.galaxyexplorer.catalog.image.ObjectImageRepository;
import dev.galaxyexplorer.media.ImageFileStore;

/**
 * Arquivos de imagem hospedados.
 *
 * <p>{@code GET /api/v1/images/files/{sha256}.jpg}
 * <ul>
 *   <li>nome fora de {@code ^[0-9a-f]{64}\.jpg$} -> 404 {@code IMAGE_NOT_FOUND} sem consultar banco nem disco;</li>
 *   <li>sha que {@code object_image} nao conhece (original ou miniatura) -> 404: nunca serve arquivo que o
 *       banco nao registrou;</li>
 *   <li>linha existe mas o arquivo sumiu -> 404 + log ERROR (inconsistencia);</li>
 *   <li>200 {@code image/jpeg}, imutavel (a URL contem o sha), {@code ETag} = sha (304 com
 *       {@code If-None-Match}), {@code nosniff}, CSP {@code default-src 'none'}, CORP same-origin.</li>
 * </ul>
 * O caminho do arquivo vem SO do banco ({@code storage_key}), via {@link ImageFileStore#resolve}.
 */
@RestController
@RequestMapping("/api/v1/images/files")
public class ImageFileController {

    private static final Pattern FILE = Pattern.compile("^([0-9a-f]{64})\\.jpg$");
    private static final Logger log = LoggerFactory.getLogger(ImageFileController.class);

    private final ObjectImageRepository images;
    private final ImageFileStore files;

    public ImageFileController(ObjectImageRepository images, ImageFileStore files) {
        this.images = images;
        this.files = files;
    }

    @GetMapping("/{file}")
    public ResponseEntity<Resource> get(@PathVariable("file") String file,
                                        @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false)
                                        String ifNoneMatch) {
        Matcher m = FILE.matcher(file);
        if (!m.matches()) {
            throw notFound();
        }
        String sha = m.group(1);
        Optional<String> key = images.findStorageKeyBySha(sha);
        if (key.isEmpty()) {
            throw notFound();
        }
        Optional<Path> path = files.resolve(key.get());
        if (path.isEmpty()) {
            log.error("Imagem {} registrada em object_image, mas o arquivo {} não existe em {}", sha, key.get(),
                    files.root());
            throw notFound();
        }
        String etag = "\"" + sha + "\"";
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(etag);
        headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Cross-Origin-Resource-Policy", "same-origin");
        headers.set("Content-Security-Policy", "default-src 'none'");
        if (ifNoneMatch != null && (ifNoneMatch.contains(etag) || ifNoneMatch.trim().equals("*"))) {
            return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
        }
        headers.setContentType(MediaType.IMAGE_JPEG);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline");
        return new ResponseEntity<>(new FileSystemResource(path.get()), headers, HttpStatus.OK);
    }

    private static ApiException notFound() {
        return new ApiException(ApiErrorCode.IMAGE_NOT_FOUND, "imagem não encontrada", null);
    }
}

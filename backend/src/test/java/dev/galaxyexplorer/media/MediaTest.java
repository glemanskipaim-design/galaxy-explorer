package dev.galaxyexplorer.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.galaxyexplorer.support.Fixtures;

/** Sobre JPGs REAIS: o recorte hips2fits de M51 (160x120) e o "screen" ESA de heic0506a (1280x888). */
class MediaTest {

    @TempDir
    Path dir;

    @Test
    void inspectorReadsRealDimensionsFromTheHeader() {
        JpegInspector inspector = new JpegInspector(40_000_000);
        assertThat(inspector.inspect(Fixtures.readBytes("hips2fits/m51_panstarrs_160x120.jpg")))
                .isEqualTo(new JpegInfo(160, 120));
        assertThat(inspector.inspect(Fixtures.readBytes("esa/hubble_screen_heic0506a.jpg")))
                .isEqualTo(new JpegInfo(1280, 888));
    }

    @Test
    void inspectorRefusesNonJpegAndTooManyPixels() {
        byte[] html = Fixtures.read("esa/webb_search_m_16_noresults.html").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> new JpegInspector(40_000_000).inspect(html)).isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("FF D8 FF");
        assertThatThrownBy(() -> new JpegInspector(160 * 120 - 1)
                .inspect(Fixtures.readBytes("hips2fits/m51_panstarrs_160x120.jpg")))
                .isInstanceOf(InvalidImageException.class).hasMessageContaining("limite");
    }

    @Test
    void thumbnailKeepsAspectAndIsJpeg() {
        byte[] thumb = new Thumbnailer(320).thumbnail(Fixtures.readBytes("esa/hubble_screen_heic0506a.jpg"));
        assertThat(new JpegInspector(40_000_000).inspect(thumb)).isEqualTo(new JpegInfo(320, 222));
        byte[] small = new Thumbnailer(320).thumbnail(Fixtures.readBytes("hips2fits/m51_panstarrs_160x120.jpg"));
        assertThat(new JpegInspector(40_000_000).inspect(small)).isEqualTo(new JpegInfo(160, 120)); // nunca amplia
    }

    @Test
    void storeIsContentAddressedIdempotentAndResolveStaysInsideRoot() throws Exception {
        ImageFileStore store = new ImageFileStore(dir);
        byte[] bytes = Fixtures.readBytes("hips2fits/m51_panstarrs_160x120.jpg");
        StoredFile a = store.store(bytes);
        StoredFile b = store.store(bytes);
        assertThat(a).isEqualTo(b);
        assertThat(a.storageKey()).isEqualTo(a.sha256().substring(0, 2) + "/" + a.sha256() + ".jpg");
        assertThat(Files.readAllBytes(store.resolve(a.storageKey()).orElseThrow())).isEqualTo(bytes);
        try (var files = Files.list(dir.resolve(a.sha256().substring(0, 2)))) {
            assertThat(files).hasSize(1); // nenhum temporario sobrando
        }
        assertThat(store.resolve("../" + a.storageKey())).isEmpty();
        assertThat(store.resolve("ab/../../etc/passwd")).isEmpty();
        assertThat(store.resolve("00/" + "0".repeat(64) + ".jpg")).isEmpty(); // formato ok, arquivo inexistente
    }
}

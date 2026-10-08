package dev.galaxyexplorer.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.ingestion.images.ImageIngestionRequest;
import dev.galaxyexplorer.ingestion.images.ImageIngestionService;
import dev.galaxyexplorer.ingestion.images.ImageSource;
import dev.galaxyexplorer.ingestion.pipeline.IngestionPipeline;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRequest;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;
import dev.galaxyexplorer.media.ImageFileStore;
import dev.galaxyexplorer.support.Fixtures;

/** Contrato de imagens em /objects, /search, /categories e o servico de arquivos. */
class ImageApiIntegrationTest extends IntegrationTestBase {

    static final String HERO_SHA = ImageFileStore.sha256Hex(Fixtures.readBytes("esa/hubble_screen_heic0506a.jpg"));

    @Autowired
    MockMvc mvc;
    @Autowired
    IngestionPipeline pipeline;
    @Autowired
    ImageIngestionService images;

    @BeforeEach
    void seed() {
        pipeline.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.SEED, List.of("M51", "M81", "M94")));
        UUID m51 = jdbc.sql("SELECT id FROM celestial_object WHERE slug = 'messier-51'").query(UUID.class).single();
        images.run(new ImageIngestionRequest(Set.of(ImageSource.CUTOUT, ImageSource.ESA_HUBBLE), List.of(m51),
                RunTrigger.MANUAL, false));
    }

    @Test
    void objectDetailListsHeroFirstWithLiteralCreditLicenseAndSources() throws Exception {
        mvc.perform(get("/api/v1/objects/messier-51"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageAbsence").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.images.length()").value(2))
                .andExpect(jsonPath("$.images[0].hero").value(true))
                .andExpect(jsonPath("$.images[0].kind").value("processed_composite"))
                .andExpect(jsonPath("$.images[0].url").value("/api/v1/images/files/" + HERO_SHA + ".jpg"))
                .andExpect(jsonPath("$.images[0].credit")
                        .value("NASA, ESA, S. Beckwith (STScI), and The Hubble Heritage Team (STScI/AURA)"))
                .andExpect(jsonPath("$.images[0].license.url").value("https://creativecommons.org/licenses/by/4.0/"))
                .andExpect(jsonPath("$.images[0].releaseDate").value("2005-04-25"))
                .andExpect(jsonPath("$.images[0].cutout").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.images[1].kind").value("survey_cutout"))
                .andExpect(jsonPath("$.images[1].acknowledgment")
                        .value("This research made use of hips2fits, a service provided by CDS."))
                .andExpect(jsonPath("$.images[1].termsUrl").value("http://panstarrs.stsci.edu/"))
                .andExpect(jsonPath("$.images[1].cutout.hipsId").value("CDS/P/PanSTARRS/DR1/color-z-zg-g"))
                .andExpect(jsonPath("$.sources[*].code", Matchers.hasItems("CDS_HIPS2FITS", "ESA_HUBBLE", "SIMBAD")));
    }

    @Test
    void objectWithoutImageSaysWhy() throws Exception {
        mvc.perform(get("/api/v1/objects/messier-81"))
                .andExpect(jsonPath("$.images.length()").value(0))
                .andExpect(jsonPath("$.imageAbsence.code").value("NOT_AVAILABLE"))
                .andExpect(jsonPath("$.imageAbsence.message", Matchers.startsWith("Imagem não disponível")));
    }

    @Test
    void searchAndCategoryCarryTheHeroThumbnailWithShortProvider() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "M51"))
                .andExpect(jsonPath("$.hits[0].thumbnail.provider").value("ESA/Hubble"))
                .andExpect(jsonPath("$.hits[0].thumbnail.licenseCode").value("CC-BY-4.0"))
                .andExpect(jsonPath("$.hits[0].thumbnail.url", Matchers.startsWith("/api/v1/images/files/")));
        mvc.perform(get("/api/v1/categories/galaxy/objects"))
                .andExpect(jsonPath("$.items[?(@.slug == 'messier-51')].thumbnail.kind").value("processed_composite"))
                .andExpect(jsonPath("$.items[?(@.slug == 'messier-81')].thumbnail").value(Matchers.hasItem(Matchers.nullValue())));
    }

    @Test
    void fileIsServedWithSafeImmutableHeadersAndEtag() throws Exception {
        String url = "/api/v1/images/files/" + HERO_SHA + ".jpg";
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", Matchers.containsString("immutable")))
                .andExpect(header().string("ETag", "\"" + HERO_SHA + "\""))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(content().bytes(Fixtures.readBytes("esa/hubble_screen_heic0506a.jpg")));
        mvc.perform(get(url).header("If-None-Match", "\"" + HERO_SHA + "\"")).andExpect(status().isNotModified());
    }

    @Test
    void thumbnailIsServedToo() throws Exception {
        String thumb = jdbc.sql("SELECT thumb_sha256 FROM object_image WHERE is_hero").query(String.class).single();
        mvc.perform(get("/api/v1/images/files/" + thumb + ".jpg")).andExpect(status().isOk());
    }

    @Test
    void unknownOrMalformedFileIs404() throws Exception {
        mvc.perform(get("/api/v1/images/files/" + "0".repeat(64) + ".jpg"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("IMAGE_NOT_FOUND"));
        mvc.perform(get("/api/v1/images/files/..%2F..%2Fetc%2Fpasswd")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/images/files/" + HERO_SHA.toUpperCase() + ".jpg")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/images/files/" + HERO_SHA + ".png")).andExpect(status().isNotFound());
    }
}

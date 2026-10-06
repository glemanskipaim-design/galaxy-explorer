package dev.galaxyexplorer.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.ingestion.pipeline.IngestionPipeline;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRequest;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;
import dev.galaxyexplorer.support.Samples;

class ObjectApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    IngestionPipeline pipeline;

    @BeforeEach
    void ingestSample() {
        pipeline.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.SEED, Samples.QUERIES));
    }

    @Test
    void m31DetailWithProvenanceAndAbsence() throws Exception {
        mvc.perform(get("/api/v1/objects/messier-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("M 31"))
                .andExpect(jsonPath("$.category.code").value("galaxy_spiral"))
                .andExpect(jsonPath("$.category.basis", Matchers.containsString("morph_type=SA(s)b")))
                .andExpect(jsonPath("$.sourceTypes[0].sourceType").value("AGN"))
                .andExpect(jsonPath("$.sourceTypes[0].sourceRef").value("1575544"))
                .andExpect(jsonPath("$.identifiers[?(@.identifier == 'NAME Andromeda')].isCommonName").value(true))
                .andExpect(jsonPath("$.position.frame").value("ICRS"))
                .andExpect(jsonPath("$.position.reference.bibcode").value("2006AJ....131.1163S"))
                // radial_velocity: medida (rvz_type=v), com fonte e link para conferir
                .andExpect(jsonPath("$.properties[?(@.code == 'radial_velocity')].preferred.value").value(-300.0))
                .andExpect(jsonPath("$.properties[?(@.code == 'radial_velocity')].preferred.unit").value("km/s"))
                .andExpect(jsonPath("$.properties[?(@.code == 'radial_velocity')].preferred.epistemic")
                        .value("observed"))
                .andExpect(jsonPath("$.properties[?(@.code == 'radial_velocity')].preferred.sourceUrl")
                        .value("https://simbad.cds.unistra.fr/simbad/sim-id?Ident=M%20%2031"))
                // varias distancias publicadas: uma preferida, as outras como alternativas
                .andExpect(jsonPath("$.properties[?(@.code == 'distance')].alternatives",
                        Matchers.hasItem(Matchers.hasSize(Matchers.greaterThan(5)))))
                // M31 nao tem paralaxe no SIMBAD -> ausente com motivo, nunca 0
                .andExpect(jsonPath("$.properties[?(@.code == 'parallax')].available").value(false))
                .andExpect(jsonPath("$.properties[?(@.code == 'parallax')].absence.code")
                        .value("NOT_PROVIDED_BY_SOURCE"))
                .andExpect(jsonPath("$.properties[?(@.code == 'parallax')].absence.message")
                        .value("Não disponível na fonte"))
                .andExpect(jsonPath("$.sources[0].code").value("SIMBAD"))
                .andExpect(jsonPath("$.sources[0].acknowledgment",
                        Matchers.startsWith("This research has made use of the SIMBAD database")));
    }

    @Test
    void betelgeuseComputedDistanceAndEncodedAdsLink() throws Exception {
        mvc.perform(get("/api/v1/objects/betelgeuse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Betelgeuse"))
                .andExpect(jsonPath("$.properties[?(@.code == 'distance')].preferred.epistemic")
                        .value("computed_by_us"))
                .andExpect(jsonPath("$.properties[?(@.code == 'distance')].preferred.method").value("1/plx"))
                .andExpect(jsonPath("$.properties[?(@.code == 'parallax')].preferred.reference.adsUrl")
                        .value("https://ui.adsabs.harvard.edu/abs/2007A%26A...474..653V/abstract"));
    }

    @Test
    void sgrAStarNamedFromMainIdAndClassifiedAsXraySource() throws Exception {
        mvc.perform(get("/api/v1/objects/sgr-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Sgr A*"))
                .andExpect(jsonPath("$.category.code").value("x_ray_source"))
                .andExpect(jsonPath("$.properties[?(@.code == 'distance')].available").value(false));
    }

    @Test
    void unknownOrMalformedSlugIs404() throws Exception {
        mvc.perform(get("/api/v1/objects/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("OBJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.details").value(Matchers.nullValue()));
        mvc.perform(get("/api/v1/objects/M31_x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("OBJECT_NOT_FOUND"));
    }
}

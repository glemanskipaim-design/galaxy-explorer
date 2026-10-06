package dev.galaxyexplorer.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.catalog.SourceCode;
import dev.galaxyexplorer.ingestion.pipeline.IngestionPipeline;
import dev.galaxyexplorer.ingestion.pipeline.IngestionRequest;
import dev.galaxyexplorer.ingestion.pipeline.RunTrigger;
import dev.galaxyexplorer.support.Samples;

class SearchApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    IngestionPipeline pipeline;

    @BeforeEach
    void ingestSample() {
        pipeline.run(new IngestionRequest(SourceCode.SIMBAD, RunTrigger.SEED, Samples.QUERIES));
    }

    @ParameterizedTest
    @ValueSource(strings = {"M31", "m 31", "M  31", "NGC 224", "Andromeda", "Andrômeda"})
    void spellingsOfM31FindIt(String q) throws Exception {
        mvc.perform(get("/api/v1/search").param("q", q))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits[0].slug").value("messier-31"))
                .andExpect(jsonPath("$.hits[0].displayName").value("M 31"))
                .andExpect(jsonPath("$.hits[0].categoryCode").value("galaxy_spiral"))
                .andExpect(jsonPath("$.hits[0].matchType").value("EXACT"))
                .andExpect(jsonPath("$.hits", Matchers.hasSize(1)));
    }

    @Test
    void typoFoundByTrigram() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "Betelgeuze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normalizedQuery").value("betelgeuze"))
                .andExpect(jsonPath("$.hits[0].slug").value("betelgeuse"))
                .andExpect(jsonPath("$.hits[0].matchType").value("FUZZY"));
    }

    @Test
    void noMatchIsEmptyList() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "Vega"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits", Matchers.hasSize(0)));
    }

    @Test
    void blankQueryIsValidationError() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").isString())
                .andExpect(jsonPath("$.error.details[0].field").value("q"));
        mvc.perform(get("/api/v1/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/search").param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest());
    }
}

package dev.galaxyexplorer.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;

import dev.galaxyexplorer.IntegrationTestBase;
import dev.galaxyexplorer.ingestion.connector.SourceUnavailableException;
import dev.galaxyexplorer.support.Samples;

class ImportApiIntegrationTest extends IntegrationTestBase {

    @Autowired
    MockMvc mvc;
    @Autowired
    @Qualifier("importExecutor")
    ThreadPoolTaskExecutor importExecutor;

    private String statusOf(String location) throws Exception {
        String body = mvc.perform(get(location)).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.status");
    }

    private String postImport(String query) throws Exception {
        return mvc.perform(post("/api/v1/imports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + query + "\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getHeader("Location");
    }

    private void awaitFinished(String location) {
        await().atMost(Duration.ofSeconds(30)).until(() -> !Set.of("queued", "running").contains(statusOf(location)));
    }

    @Test
    void successfulImportEndToEnd() throws Exception {
        // respostas reais gravadas: fixtures/sesame/vega.* e fixtures/simbad/vega_*
        String location = postImport("Vega");
        awaitFinished(location);

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"))
                .andExpect(jsonPath("$.resultSlugs", Matchers.contains("vega")))
                .andExpect(jsonPath("$.message").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.runId").isNumber());
        mvc.perform(get("/api/v1/objects/vega"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.code").value("star_variable"));
        assertThat(fixtureSesame().callCount()).isEqualTo(1);
        assertThat(fixtureTap().callCount()).isEqualTo(5); // tap_schema + basic, ident, flux, mesDistance
    }

    @Test
    void sourceFailureEndsFailedWithCategoryMessageOnly() throws Exception {
        fixtureSesame().failOnCall(1, () -> new SourceUnavailableException("detalhe interno: 10.0.0.5 recusou"));

        String location = postImport("TRAPPIST-1");
        awaitFinished(location);

        String body = mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.message", Matchers.startsWith("Fonte indisponível no momento")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("10.0.0.5"); // detalhe so no servidor...
        Map<String, Object> run = jdbc.sql("SELECT error_summary FROM ingestion_run ORDER BY id DESC LIMIT 1")
                .query().singleRow();
        assertThat((String) run.get("error_summary")).contains("10.0.0.5"); // ...no error_summary
    }

    @Test
    void unexpectedErrorEndsFailedNeverStuckRunningAndLeaksNothing() throws Exception {
        fixtureTap().failOn("tap_schema", 1,
                () -> new IllegalStateException("PreparedStatementCallback; SQL [INSERT INTO x]; constraint y"));

        String location = postImport("TRAPPIST-1");
        awaitFinished(location);

        String body = mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("failed"))
                .andExpect(jsonPath("$.message", Matchers.matchesPattern("Erro interno \\(run \\d+\\)\\.")))
                .andExpect(jsonPath("$.finishedAt").isString())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("SQL").doesNotContain("constraint");
    }

    @Test
    void fullQueueIs429WithRetryAfterAndPersistsNothing() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        Runnable blocker = () -> {
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        try {
            importExecutor.execute(blocker); // ocupa a unica thread
            await().atMost(Duration.ofSeconds(5)).until(() -> importExecutor.getActiveCount() == 1);
            for (int i = 0; i < importExecutor.getQueueCapacity(); i++) {
                importExecutor.execute(blocker); // enche a fila
            }

            mvc.perform(post("/api/v1/imports").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\":\"Vega\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "30"))
                    .andExpect(jsonPath("$.error.code").value("IMPORT_QUEUE_FULL"))
                    .andExpect(jsonPath("$.error.details").value(Matchers.nullValue()));
            assertThat(count("import_job")).isZero(); // rejeitado ANTES de gravar
        } finally {
            release.countDown();
            await().atMost(Duration.ofSeconds(30))
                    .until(() -> importExecutor.getQueueSize() == 0 && importExecutor.getActiveCount() == 0);
        }
    }

    @Test
    void postReturns202WithLocationAndCanBePolled() throws Exception {
        String location = mvc.perform(post("/api/v1/imports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + Samples.NOT_FOUND_QUERY + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", Matchers.matchesPattern("/api/v1/imports/\\d+")))
                .andExpect(jsonPath("$.query").value(Samples.NOT_FOUND_QUERY))
                .andExpect(jsonPath("$.status").value(Matchers.oneOf("queued", "running", "not_found")))
                .andReturn().getResponse().getHeader("Location");

        await().atMost(Duration.ofSeconds(30)).until(() -> statusOf(location).equals("not_found"));

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Objeto não encontrado em SIMBAD"))
                .andExpect(jsonPath("$.resultSlugs", Matchers.hasSize(0)))
                .andExpect(jsonPath("$.finishedAt").isString());
    }

    @Test
    void invalidBodiesAre400() throws Exception {
        for (String body : new String[] {"{\"query\":\"\"}", "{\"query\":\"M31; DROP\"}", "{}", "not json",
                "{\"query\":\"" + "x".repeat(101) + "\"}"}) {
            mvc.perform(post("/api/v1/imports").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void unknownJobIs404() throws Exception {
        mvc.perform(get("/api/v1/imports/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("IMPORT_NOT_FOUND"));
        mvc.perform(get("/api/v1/imports/abc"))
                .andExpect(status().isBadRequest());
    }
}

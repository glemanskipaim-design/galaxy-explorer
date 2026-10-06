package dev.galaxyexplorer.ingestion.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ImportPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ImportProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void defaultRetentionIsThirtyDays() {
        runner.run(ctx -> assertThat(ctx.getBean(ImportProperties.class).retention()).isEqualTo(Duration.ofDays(30)));
    }

    @Test
    void oneHourIsTheMinimumAccepted() {
        runner.withPropertyValues("galaxy.imports.retention=1h")
                .run(ctx -> assertThat(ctx.getBean(ImportProperties.class).retention()).isEqualTo(Duration.ofHours(1)));
    }

    @Test
    void shorterZeroOrNegativeRetentionFailsAtStartup() {
        for (String value : new String[] {"59m", "0s", "-1d"}) {
            runner.withPropertyValues("galaxy.imports.retention=" + value)
                    .run(ctx -> assertThat(ctx).as(value).hasFailed());
        }
    }
}

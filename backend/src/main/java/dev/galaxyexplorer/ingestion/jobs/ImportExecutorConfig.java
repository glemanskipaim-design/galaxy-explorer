package dev.galaxyexplorer.ingestion.jobs;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Uma thread so: importacoes sao serializadas. O rate limiter do CDS ja e o
 * gargalo; paralelizar nao ganharia nada e complicaria. {@code @EnableScheduling}
 * e para a retencao ({@link ImportRetention}).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(ImportProperties.class)
public class ImportExecutorConfig {

    @Bean
    ThreadPoolTaskExecutor importExecutor(ImportProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(props.queueCapacity());
        executor.setThreadNamePrefix("import-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}

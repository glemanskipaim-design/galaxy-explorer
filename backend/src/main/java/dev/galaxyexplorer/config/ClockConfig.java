package dev.galaxyexplorer.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relogio unico do sistema (UTC). Tudo que grava {@code retrieved_at} /
 * {@code started_at} recebe este bean, para que os testes possam fixar o tempo.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

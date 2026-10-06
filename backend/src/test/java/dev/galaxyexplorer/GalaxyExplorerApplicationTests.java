package dev.galaxyexplorer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import dev.galaxyexplorer.ingestion.connector.simbad.HttpSesameClient;
import dev.galaxyexplorer.ingestion.connector.simbad.SesameClient;
import dev.galaxyexplorer.ingestion.connector.tap.HttpTapClient;
import dev.galaxyexplorer.ingestion.connector.tap.TapClient;
import io.github.resilience4j.ratelimiter.RateLimiter;

/** Contexto com os beans REAIS dos clientes do CDS (nenhuma chamada de rede e feita aqui). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GalaxyExplorerApplicationTests {

	@Autowired
	ApplicationContext context;
	@Autowired
	@Qualifier("simbadTapClient")
	TapClient tap;
	@Autowired
	@Qualifier("simbadSesameClient")
	SesameClient sesame;
	@Autowired
	Environment environment;

	@Test
	void contextLoads() {
	}

	@Test
	void tapAndSesameShareTheSameCdsRateLimiter() {
		// o limite do CDS e por IP, somando os dois servicos: um limitador so no processo
		assertThat(context.getBeansOfType(RateLimiter.class)).hasSize(1);
		RateLimiter shared = context.getBean(RateLimiter.class);
		assertThat(((HttpTapClient) tap).rateLimiter()).isSameAs(shared);
		assertThat(((HttpSesameClient) sesame).rateLimiter()).isSameAs(shared);
		assertThat(shared.getRateLimiterConfig().getLimitForPeriod()).isEqualTo(2);
	}

	@Test
	void serverBindsToLoopbackByDefault() {
		assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
	}
}

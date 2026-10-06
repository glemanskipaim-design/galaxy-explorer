package dev.galaxyexplorer;

import org.springframework.boot.SpringApplication;

public class TestGalaxyExplorerApplication {

	public static void main(String[] args) {
		SpringApplication.from(GalaxyExplorerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

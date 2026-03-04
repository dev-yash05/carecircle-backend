package com.carecircle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class CarecircleBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(CarecircleBackendApplication.class, args);
	}

}

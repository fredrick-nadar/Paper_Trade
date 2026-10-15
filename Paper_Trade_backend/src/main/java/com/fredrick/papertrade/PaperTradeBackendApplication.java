package com.fredrick.papertrade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaperTradeBackendApplication {

	public static void main(String[] args) {
		// Spring Boot starts the web server, JPA repositories, controllers, and services.
		SpringApplication.run(PaperTradeBackendApplication.class, args);
	}

}

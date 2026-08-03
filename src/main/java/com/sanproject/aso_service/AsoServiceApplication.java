package com.sanproject.aso_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Spring Boot entry point — starts the embedded Tomcat server and component scan. */
@SpringBootApplication
public class AsoServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AsoServiceApplication.class, args);
	}

}
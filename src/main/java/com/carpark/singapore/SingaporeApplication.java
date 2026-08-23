package com.carpark.singapore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SingaporeApplication {

	public static void main(String[] args) {
		SpringApplication.run(SingaporeApplication.class, args);
	}

}

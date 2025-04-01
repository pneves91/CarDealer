package com.cardealer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackageClasses = CarDealerApplication.class)
@EnableFeignClients(basePackages = "com.cardealer.clients")
@EnableJpaRepositories(basePackages = "com.cardealer.repositories")
public class CarDealerApplication {

	public static void main(String[] args) {
		SpringApplication.run(CarDealerApplication.class, args);
	}

}

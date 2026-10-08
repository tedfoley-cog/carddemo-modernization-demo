package com.carddemo.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Entry point for the CardDemo nightly batch (one Cloud Run Job execution = one stream run). */
@SpringBootApplication
@ConfigurationPropertiesScan
@EntityScan("com.carddemo.domain.model")
@EnableJpaRepositories("com.carddemo.domain.repository")
public class CardDemoBatchApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(CardDemoBatchApplication.class, args)));
    }
}

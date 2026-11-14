package com.beautyhaat.scraper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShajgojScraperApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShajgojScraperApplication.class, args);
    }
}

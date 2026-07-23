package com.inventra.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class InventraApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventraApiApplication.class, args);
    }
}

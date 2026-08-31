package com.westy.codmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class CodManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodManagerApplication.class, args);
    }
}

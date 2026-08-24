package edu.vt.hokiehub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class HokieHubApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(HokieHubApiApplication.class, args);
    }
}

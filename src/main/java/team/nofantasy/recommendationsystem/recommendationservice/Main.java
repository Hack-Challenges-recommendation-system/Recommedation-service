package team.nofantasy.recommendationsystem.recommendationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main class is the main entrypoint of the recommendation microservice.
 */
@SpringBootApplication
@EnableScheduling
public class Main {
    static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}

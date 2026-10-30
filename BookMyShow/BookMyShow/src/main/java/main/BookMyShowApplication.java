package main;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.time.Clock;

@SpringBootApplication(scanBasePackages = "service")
@EntityScan(basePackages = "entity")
@EnableJpaRepositories(basePackages = "repo")
@EnableScheduling
public class BookMyShowApplication {

    @Bean
    public Clock clock() { return Clock.systemUTC(); }

    public static void main(String[] args) {
        SpringApplication.run(BookMyShowApplication.class, args);
    }
}

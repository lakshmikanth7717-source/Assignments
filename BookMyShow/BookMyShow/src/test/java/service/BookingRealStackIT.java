package service;

import org.redisson.Redisson;
import org.redisson.config.Config;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Explicit real-integration profile: fails when Docker is unavailable rather than silently skipping. */
@Testcontainers
class BookingRealStackIT extends BookingScenarios {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("booking_engine").withUsername("booking").withPassword("test-only-password");
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @Override AnnotationConfigApplicationContext openContext() {
        String jdbcUrl = MYSQL.getJdbcUrl();
        DriverManagerDataSource source = new DriverManagerDataSource(
                jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
                MYSQL.getUsername(), MYSQL.getPassword());
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
        return BookingTestContext.open(source, Redisson.create(config));
    }
}

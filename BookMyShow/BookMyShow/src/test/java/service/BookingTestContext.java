package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

final class BookingTestContext {
    static final String SECRET = "local-test-secret-not-a-production-credential";

    static AnnotationConfigApplicationContext open(DataSource source, RedissonClient redis) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of("booking.webhook-secret", SECRET)));
        context.registerBean(DataSource.class, () -> source);
        context.registerBean(RedissonClient.class, () -> redis, definition -> definition.setDestroyMethodName("shutdown"));
        context.register(Config.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableJpaRepositories(basePackages = "repo")
    @Import({BookingService.class, BookingTransactionService.class, BookingExpiryService.class, PaymentWebhookVerifier.class})
    static class Config {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean MutableClock clock() { return new MutableClock(); }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean(initMethod = "migrate") Flyway flyway(DataSource source) {
            return Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source, Flyway flyway) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setPackagesToScan("entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "validate");
            properties.setProperty("hibernate.jdbc.time_zone", "UTC");
            factory.setJpaProperties(properties);
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    static class MutableClock extends Clock {
        static final Instant INITIAL = Instant.parse("2030-01-01T10:00:00Z");
        private final AtomicReference<Instant> instant = new AtomicReference<>(INITIAL);
        void reset() { instant.set(INITIAL); }
        void advance(Duration duration) { instant.updateAndGet(value -> value.plus(duration)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) {
            if (!zone.equals(ZoneOffset.UTC)) { throw new IllegalArgumentException("Tests use UTC"); }
            return this;
        }
        @Override public Instant instant() { return instant.get(); }
    }

    /** In-process lock double for H2 tests. The Docker suite substitutes an actual RedissonClient. */
    static RedissonClient localLocks() {
        Map<String, RLock> locks = new ConcurrentHashMap<>();
        return (RedissonClient) Proxy.newProxyInstance(RedissonClient.class.getClassLoader(),
                new Class<?>[]{RedissonClient.class}, (client, method, args) -> switch (method.getName()) {
                    case "getLock" -> locks.computeIfAbsent((String) args[0], BookingTestContext::localLock);
                    case "shutdown" -> null;
                    case "toString" -> "In-process test Redis double";
                    case "hashCode" -> System.identityHashCode(client);
                    case "equals" -> client == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static RLock localLock(String name) {
        ReentrantLock lock = new ReentrantLock();
        return (RLock) Proxy.newProxyInstance(RLock.class.getClassLoader(), new Class<?>[]{RLock.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tryLock" -> lock.tryLock((Long) args[0], (java.util.concurrent.TimeUnit) args[1]);
                    case "isHeldByCurrentThread" -> lock.isHeldByCurrentThread();
                    case "unlock" -> { lock.unlock(); yield null; }
                    case "getName", "toString" -> name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}

package com.airtribe.wallet.service;

import com.airtribe.wallet.config.DatabaseConfiguration;
import com.airtribe.wallet.repository.jdbc.JdbcIdempotencyRepository;
import com.airtribe.wallet.repository.jdbc.JdbcLedgerRepository;
import com.airtribe.wallet.repository.jdbc.JdbcRiskRepository;
import com.airtribe.wallet.repository.jdbc.JdbcRiskRuleRepository;
import com.airtribe.wallet.repository.jdbc.JdbcTransferRepository;
import com.airtribe.wallet.repository.jdbc.JdbcWalletRepository;
import com.airtribe.wallet.service.rules.FirstTimeRecipientRule;
import com.airtribe.wallet.service.rules.HistoricalAverageRule;
import com.airtribe.wallet.service.rules.TransactionVelocityRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ServiceIntegrationTestSupport.ServiceConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class ServiceIntegrationTestSupport {
    static final int CLIENTS = 20;
    private static final String APPLICATION_NAME = "wallet-service-it";
    private static PostgreSQLContainer<?> postgres;

    @Autowired TransferService transfers;
    @Autowired IdempotencyService idempotency;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper json;
    @Autowired ChatClient chatClient;

    @DynamicPropertySource
    static synchronized void databaseProperties(DynamicPropertyRegistry properties) {
        String externalUrl = System.getenv("TEST_DATABASE_URL");
        if (externalUrl == null || externalUrl.isBlank()) {
            if (postgres == null) {
                postgres = new PostgreSQLContainer<>("postgres:17-alpine");
                postgres.start();
            }
            // Match the cached Spring context's lifetime: Ryuk cleans up at test JVM exit,
            // rather than stopping the shared database after the first test class.
            properties.add("spring.datasource.url", postgres::getJdbcUrl);
            properties.add("spring.datasource.username", postgres::getUsername);
            properties.add("spring.datasource.password", postgres::getPassword);
        } else {
            properties.add("spring.datasource.url", () -> externalUrl);
            properties.add("spring.datasource.username", () -> System.getenv("TEST_DATABASE_USER"));
            properties.add("spring.datasource.password", () -> System.getenv("TEST_DATABASE_PASSWORD"));
        }
        // Twenty workers, one wallet lock holder, and one lock observer.
        properties.add("spring.datasource.hikari.maximum-pool-size", () -> CLIENTS + 2);
        properties.add("spring.datasource.hikari.connection-timeout", () -> 10_000);
        properties.add("spring.datasource.hikari.data-source-properties.ApplicationName", () -> APPLICATION_NAME);
        properties.add("spring.datasource.hikari.data-source-properties.options",
                () -> "-c lock_timeout=30000 -c statement_timeout=30000");
    }

    @AfterEach
    void noAiCallsWereMade() {
        verifyNoInteractions(chatClient);
    }

    <T> List<T> callConcurrentlyWhileWalletLocked(long walletId, List<Callable<T>> calls) throws Exception {
        int clients = calls.size();
        assertThat(clients).isBetween(1, CLIENTS);
        var workers = Executors.newFixedThreadPool(clients);
        CountDownLatch ready = new CountDownLatch(clients);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try {
            try (Connection blocker = dataSource.getConnection()) {
                blocker.setAutoCommit(false);
                try {
                    try (var statement = blocker.prepareStatement(
                            "SELECT wallet_id FROM wallets WHERE wallet_id = ? FOR NO KEY UPDATE")) {
                        statement.setLong(1, walletId);
                        try (var rows = statement.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                        }
                    }
                    for (Callable<T> call : calls) {
                        futures.add(workers.submit(() -> {
                            ready.countDown();
                            if (!start.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Timed out waiting to start service workers");
                            }
                            return call.call();
                        }));
                    }
                    assertThat(ready.await(10, TimeUnit.SECONDS)).as("all workers are ready").isTrue();
                    start.countDown();
                    // The leader waits on the wallet; duplicate keys wait on its uncommitted claim.
                    // Distinct keys all wait on the wallet. Either way, every request must overlap.
                    await().alias("all service calls waiting on PostgreSQL locks")
                            .atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50))
                            .untilAsserted(() -> assertThat(jdbc.queryForObject("""
                                    SELECT COUNT(*) FROM pg_stat_activity
                                    WHERE datname = current_database() AND application_name = ?
                                      AND wait_event_type = 'Lock'
                                    """, Long.class, APPLICATION_NAME)).isEqualTo((long) clients));
                } finally {
                    blocker.rollback();
                }
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                // Unexpected worker exceptions fail the test instead of being silently ignored.
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            futures.forEach(future -> future.cancel(true));
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).as("service workers terminated").isTrue();
        }
    }

    long createUser(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users(name, email, phone) VALUES (?, ?, ?) RETURNING user_id
                """, Long.class, name, name + "-" + UUID.randomUUID() + "@integration.test", "9000000000");
    }

    long createWallet(long userId, String name) {
        return jdbc.queryForObject("""
                INSERT INTO wallets(user_id, wallet_name) VALUES (?, ?) RETURNING wallet_id
                """, Long.class, userId, name);
    }

    long balance(long walletId) {
        return jdbc.queryForObject("SELECT balance FROM wallets WHERE wallet_id = ?", Long.class, walletId);
    }

    // Only service and JDBC collaborators: no controllers, scheduler, or OpenAI auto-configuration.
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
            FlywayAutoConfiguration.class, JacksonAutoConfiguration.class})
    @Import({DatabaseConfiguration.class, TransferService.class, IdempotencyService.class, RulesEngineService.class,
            JdbcWalletRepository.class, JdbcTransferRepository.class, JdbcLedgerRepository.class,
            JdbcIdempotencyRepository.class, JdbcRiskRepository.class, JdbcRiskRuleRepository.class,
            TransactionVelocityRule.class, HistoricalAverageRule.class, FirstTimeRecipientRule.class})
    static class ServiceConfiguration {
        @Bean
        ChatClient chatClient() {
            return mock(ChatClient.class);
        }

        @Bean
        ChatClient.Builder chatClientBuilder(ChatClient client) {
            ChatClient.Builder builder = mock(ChatClient.Builder.class);
            when(builder.build()).thenReturn(client);
            return builder;
        }
    }
}

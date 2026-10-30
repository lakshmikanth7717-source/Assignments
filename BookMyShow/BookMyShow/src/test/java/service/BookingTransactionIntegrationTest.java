package service;

import entity.Booking;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import repo.BookingRepository;
import repo.PaymentRepository;
import repo.SeatRepository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Real Spring proxies and JDBC transactions over H2. Repository mocks write event rows
 * through JdbcTemplate to verify commit/rollback boundaries without the production schema.
 */
class BookingTransactionIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private SeatRepository seats;
    private OuterWorkflow outer;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        jdbc = context.getBean(JdbcTemplate.class);
        jdbc.execute("CREATE TABLE transaction_events (event_name VARCHAR(40) PRIMARY KEY)");
        seats = context.getBean(SeatRepository.class);
        outer = context.getBean(OuterWorkflow.class);
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void standaloneHoldCommitsBeforeRedisUnlock() throws Exception {
        RedissonClient redis = context.getBean(RedissonClient.class);
        RLock lock = interfaceMock(RLock.class);
        when(redis.getLock("lock:seat:1")).thenReturn(lock);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(seats.holdSeatIfAvailable(eq(1L), eq(2L), any(), any()))
                .thenAnswer(call -> record("hold"));
        doAnswer(call -> {
            // A separate connection must see the committed hold before Redis is unlocked.
            try (var connection = context.getBean(DataSource.class).getConnection();
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery(
                         "SELECT COUNT(*) FROM transaction_events WHERE event_name = 'hold'")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
            return null;
        }).when(lock).unlock();

        assertTrue(context.getBean(BookingService.class).holdSeatForFiveMinutes(1L, 2L).success());

        assertEquals(List.of("hold"), events());
        verify(lock).unlock();
    }

    @Test
    void holdJoinsOuterTransactionAndRollsBackWithIt() throws Exception {
        RedissonClient redis = context.getBean(RedissonClient.class);
        RLock lock = interfaceMock(RLock.class);
        when(redis.getLock("lock:seat:1")).thenReturn(lock);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(seats.holdSeatIfAvailable(eq(1L), eq(2L), any(), any()))
                .thenAnswer(call -> record("hold"));

        assertThrows(IllegalStateException.class, () -> outer.holdThenFail());

        assertTrue(events().isEmpty());
        verify(lock).unlock();
    }

    @Test
    void paymentJoinsOuterTransactionAndRollsBackWithIt() {
        preparePayment();
        when(seats.confirmSeatsForBooking("booking")).thenAnswer(call -> record("seats"));

        assertThrows(IllegalStateException.class, () -> outer.paymentThenFail());

        assertTrue(events().isEmpty());
    }

    @Test
    void caughtPaymentFailureMarksOuterTransactionForRollback() {
        preparePayment();
        when(seats.confirmSeatsForBooking("booking"))
                .thenThrow(new IllegalStateException("Seat confirmation failed"));

        assertThrows(UnexpectedRollbackException.class, () -> outer.paymentThenContinue());
        assertTrue(events().isEmpty());
    }

    private void preparePayment() {
        BookingRepository bookings = context.getBean(BookingRepository.class);
        PaymentRepository payments = context.getBean(PaymentRepository.class);
        Booking booking = new Booking();
        booking.setId("booking");
        when(bookings.findByIdForUpdate("booking")).thenReturn(Optional.of(booking));
        when(payments.save(any())).thenAnswer(call -> {
            record("payment");
            return call.getArgument(0);
        });
        when(bookings.save(any())).thenAnswer(call -> {
            record("booking");
            return call.getArgument(0);
        });
    }

    private int record(String event) {
        return jdbc.update("INSERT INTO transaction_events(event_name) VALUES (?)", event);
    }

    private List<String> events() {
        return jdbc.queryForList("SELECT event_name FROM transaction_events ORDER BY event_name", String.class);
    }

    private static <T> T interfaceMock(Class<T> type) {
        return mock(type, withSettings().mockMaker("mock-maker-proxy"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @Import({BookingService.class, BookingTransactionService.class, OuterWorkflow.class})
    static class TestConfiguration {
        @Bean(destroyMethod = "shutdown")
        EmbeddedDatabase dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true)
                    .setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean(destroyMethod = "")
        RedissonClient redissonClient() { return interfaceMock(RedissonClient.class); }

        @Bean
        SeatRepository seatRepository() { return interfaceMock(SeatRepository.class); }

        @Bean
        PaymentRepository paymentRepository() { return interfaceMock(PaymentRepository.class); }

        @Bean
        BookingRepository bookingRepository() { return interfaceMock(BookingRepository.class); }
    }

    public static class OuterWorkflow {
        private final BookingService service;
        private final JdbcTemplate jdbc;

        public OuterWorkflow(BookingService service, JdbcTemplate jdbc) {
            this.service = service;
            this.jdbc = jdbc;
        }

        @Transactional
        public void holdThenFail() {
            recordOuter();
            assertTrue(service.holdSeatForFiveMinutes(1L, 2L).success());
            throw new IllegalStateException("Outer workflow failed after processing the hold");
        }

        @Transactional
        public void paymentThenFail() {
            recordOuter();
            assertTrue(service.processPaymentWebhookIdempotently("evt", "booking", "txn", 100.0).success());
            throw new IllegalStateException("Outer workflow failed after processing the payment");
        }

        @Transactional
        public WebhookResult paymentThenContinue() {
            WebhookResult result = service.processPaymentWebhookIdempotently("evt", "booking", "txn", 100.0);
            recordOuter();
            return result;
        }

        private void recordOuter() {
            jdbc.update("INSERT INTO transaction_events(event_name) VALUES ('outer')");
        }
    }
}

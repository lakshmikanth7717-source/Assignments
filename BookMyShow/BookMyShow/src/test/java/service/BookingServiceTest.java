package service;

import entity.Booking;
import entity.Payment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import repo.BookingRepository;
import repo.PaymentRepository;
import repo.SeatRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    // Interface proxies also work on JDKs newer than Mockito's bundled bytecode agent.
    @Mock(mockMaker = "mock-maker-proxy") RedissonClient redis;
    @Mock(mockMaker = "mock-maker-proxy") RLock lock;
    @Mock(mockMaker = "mock-maker-proxy") SeatRepository seats;
    @Mock(mockMaker = "mock-maker-proxy") PaymentRepository payments;
    @Mock(mockMaker = "mock-maker-proxy") BookingRepository bookings;
    @Mock(mockMaker = "mock-maker-proxy") PlatformTransactionManager manager;
    @Mock(mockMaker = "mock-maker-proxy") TransactionStatus transaction;
    BookingService service;

    @BeforeEach
    void setUp() {
        // Use Spring's annotation interceptor: constructing the target alone bypasses @Transactional.
        var advice = new TransactionInterceptor();
        advice.setTransactionManager(manager);
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(new BookingTransactionService(seats, payments, bookings));
        proxy.addAdvice(advice);
        var transactionalService = (BookingTransactionService) proxy.getProxy();
        service = new BookingService(redis, transactionalService);
        lenient().when(manager.getTransaction(any())).thenReturn(transaction);
    }

    @Test
    void holdCommitsBeforeUnlockAndUsesFiveMinuteExpiry() throws Exception {
        when(redis.getLock("lock:seat:1")).thenReturn(lock);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(seats.holdSeatIfAvailable(eq(1L), eq(2L), any(), any())).thenAnswer(call -> {
            LocalDateTime expiry = call.getArgument(2);
            LocalDateTime now = call.getArgument(3);
            assertEquals(Duration.ofMinutes(5), Duration.between(now, expiry));
            return 1;
        });

        assertTrue(service.holdSeatForFiveMinutes(1L, 2L).success());

        var order = inOrder(manager, seats, lock);
        order.verify(manager).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED));
        order.verify(seats).holdSeatIfAvailable(eq(1L), eq(2L), any(), any());
        order.verify(manager).commit(transaction);
        order.verify(lock).unlock();
    }

    @Test
    void lockContentionDoesNotTouchDatabaseOrUnlock() throws Exception {
        when(redis.getLock("lock:seat:1")).thenReturn(lock);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(false);

        assertFalse(service.holdSeatForFiveMinutes(1L, 2L).success());

        verifyNoInteractions(seats, manager);
        verify(lock, never()).unlock();
    }

    @Test
    void interruptionPreservesThreadFlag() throws Exception {
        when(redis.getLock("lock:seat:1")).thenReturn(lock);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        try {
            assertFalse(service.holdSeatForFiveMinutes(1L, 2L).success());
            assertTrue(Thread.currentThread().isInterrupted());
            verifyNoInteractions(seats, manager);
        } finally {
            assertTrue(Thread.interrupted(), "Clear the expected interrupted flag after the test");
        }
    }

    @Test
    void holdFailureRollsBackAndReleasesLock() throws Exception {
        when(redis.getLock("lock:seat:1")).thenReturn(lock);
        when(lock.tryLock(10, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(seats.holdSeatIfAvailable(eq(1L), eq(2L), any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(IllegalStateException.class, () -> service.holdSeatForFiveMinutes(1L, 2L));

        var order = inOrder(manager, lock);
        order.verify(manager).rollback(transaction);
        order.verify(lock).unlock();
    }

    @Test
    void successfulWebhookPersistsPaymentAndConfirmsBooking() {
        Booking booking = pendingBooking();
        assertTrue(service.processPaymentWebhookIdempotently("evt", "booking", "txn", 100.0).success());

        verify(payments).save(argThat(payment -> "evt".equals(payment.getIdempotencyKey())
                && "booking".equals(payment.getBookingId()) && "SUCCESS".equals(payment.getStatus())
                && "txn".equals(payment.getGatewayTransactionId())
                && payment.getAmount().doubleValue() == 100.0));
        assertEquals("CONFIRMED", booking.getStatus());
        verify(bookings).save(booking);
        verify(seats).confirmSeatsForBooking("booking");
        verify(manager).commit(transaction);
        verify(manager).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED));
    }

    @Test
    void webhookCommitFailureIsCaughtOutsideTransactionalProxy() {
        pendingBooking();
        doThrow(new UnexpectedRollbackException("commit failed")).when(manager).commit(transaction);

        WebhookResult result = service.processPaymentWebhookIdempotently("evt", "booking", "txn", 100.0);

        assertFalse(result.success());
        assertTrue(result.message().contains("commit failed"));
    }

    @Test
    void duplicateWebhookDoesNotMutatePaymentOrSeats() {
        pendingBooking();
        Payment payment = new Payment();
        payment.setBookingId("booking");
        payment.setStatus("SUCCESS");
        when(payments.findByIdempotencyKey("evt")).thenReturn(payment);

        assertTrue(service.processPaymentWebhookIdempotently("evt", "booking", "txn", 100.0).success());

        verify(payments, never()).save(any());
        verify(bookings, never()).save(any());
        verifyNoInteractions(seats);
    }

    @Test
    void seatConfirmationFailureRollsBackEntireWebhook() {
        pendingBooking();
        when(seats.confirmSeatsForBooking("booking")).thenThrow(new IllegalStateException("update failed"));

        assertFalse(service.processPaymentWebhookIdempotently("evt", "booking", "txn", 100.0).success());

        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }

    @Test
    void idempotencyKeyCannotBeReusedForAnotherBooking() {
        pendingBooking();
        Payment payment = new Payment();
        payment.setBookingId("another-booking");
        payment.setStatus("SUCCESS");
        when(payments.findByIdempotencyKey("evt")).thenReturn(payment);

        assertFalse(service.processPaymentWebhookIdempotently("evt", "booking", "txn", 100.0).success());

        verify(manager).rollback(transaction);
        verifyNoInteractions(seats);
    }

    private Booking pendingBooking() {
        Booking booking = new Booking();
        booking.setId("booking");
        when(bookings.findByIdForUpdate("booking")).thenReturn(Optional.of(booking));
        return booking;
    }
}

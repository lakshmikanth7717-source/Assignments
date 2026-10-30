package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingServiceTest {
    private RedissonClient redis;
    private RLock first;
    private RLock second;
    private RecordingDatabase database;
    private BookingService service;

    @BeforeEach
    void setUp() {
        redis = mock(RedissonClient.class, withSettings().mockMaker("mock-maker-proxy"));
        first = mock(RLock.class, withSettings().mockMaker("mock-maker-proxy"));
        second = mock(RLock.class, withSettings().mockMaker("mock-maker-proxy"));
        database = new RecordingDatabase();
        service = new BookingService(redis, database, new PaymentWebhookVerifier(new ObjectMapper(), "test"));
    }

    @Test
    void contentionDoesNotCallDatabaseOrUnlockAnUnacquiredLock() throws Exception {
        when(redis.getLock("lock:show:1:seat:1")).thenReturn(first);
        when(first.tryLock(anyLong(), any())).thenReturn(false);
        assertFalse(service.holdSeatForFiveMinutes(1L, 1L, 10L).success());
        assertEquals(0, database.calls);
        verify(first, never()).unlock();
    }

    @Test
    void overlappingMultiSeatRequestsAcquireInSortedOrderAndReleaseOnFailure() throws Exception {
        when(redis.getLock("lock:show:1:seat:1")).thenReturn(first);
        when(redis.getLock("lock:show:1:seat:2")).thenReturn(second);
        when(first.tryLock(anyLong(), any())).thenReturn(true);
        when(first.isHeldByCurrentThread()).thenReturn(true);
        when(second.tryLock(anyLong(), any())).thenReturn(false);
        assertFalse(service.holdSeatsForFiveMinutes(1L, List.of(2L, 1L), 10L).success());
        var order = inOrder(first, second);
        order.verify(first).tryLock(anyLong(), any());
        order.verify(second).tryLock(anyLong(), any());
        order.verify(first).unlock();
        verify(second, never()).unlock();
        assertEquals(0, database.calls);
    }

    @Test
    void interruptionPreservesThreadFlagAndDoesNotCallDatabase() throws Exception {
        when(redis.getLock("lock:show:1:seat:1")).thenReturn(first);
        when(first.tryLock(anyLong(), any())).thenThrow(new InterruptedException());
        try {
            assertFalse(service.holdSeatForFiveMinutes(1L, 1L, 10L).success());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, database.calls);
        } finally {
            assertTrue(Thread.interrupted(), "Clear the expected interrupted flag");
        }
    }

    @Test
    void internalDatabaseDetailsAreNotReturnedToCaller() throws Exception {
        when(redis.getLock("lock:show:1:seat:1")).thenReturn(first);
        when(first.tryLock(anyLong(), any())).thenReturn(true);
        when(first.isHeldByCurrentThread()).thenReturn(true);
        database.failure = new IllegalStateException("internal JDBC credentials and SQL");
        BookingResult result = service.holdSeatForFiveMinutes(1L, 1L, 10L);
        assertFalse(result.success());
        assertFalse(result.message().contains("JDBC"));
        assertFalse(result.message().contains("credentials"));
        verify(first).unlock();
    }

    @Test
    void absentWebhookSecretFailsClosed() {
        var verifier = new PaymentWebhookVerifier(new ObjectMapper(), "");
        assertThrows(BookingException.class, () -> verifier.verify("{}", "0".repeat(64)));
    }

    private static class RecordingDatabase extends BookingTransactionService {
        private int calls;
        private RuntimeException failure;
        RecordingDatabase() { super(null, null, null, null, null, null, Clock.systemUTC()); }
        @Override public BookingResult holdSeats(Long showId, List<Long> seatIds, Long userId) {
            calls++;
            if (failure != null) { throw failure; }
            return BookingResult.failed("Test database response");
        }
    }
}

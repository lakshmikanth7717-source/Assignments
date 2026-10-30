package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import entity.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionTemplate;
import repo.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** The same behavioral contract runs on migrated H2 and, with the explicit profile, MySQL + Redis. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class BookingScenarios {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private BookingService service;
    private BookingTestContext.MutableClock clock;
    private BookingRepository bookings;
    private PaymentRepository payments;
    private ShowSeatRepository inventory;
    private ObjectMapper mapper;

    abstract AnnotationConfigApplicationContext openContext();

    @BeforeAll
    void start() {
        context = openContext();
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(BookingService.class);
        clock = context.getBean(BookingTestContext.MutableClock.class);
        bookings = context.getBean(BookingRepository.class);
        payments = context.getBean(PaymentRepository.class);
        inventory = context.getBean(ShowSeatRepository.class);
        mapper = context.getBean(ObjectMapper.class);
    }

    @AfterAll
    void stop() {
        if (context != null) { context.close(); }
    }

    @BeforeEach
    void seed() {
        clock.reset();
        for (String table : List.of("payments", "booking_items", "show_seats", "bookings", "seats",
                "shows", "movies", "screens", "venues")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO venues(venue_id,name,city,address) VALUES (1,'Demo','Bengaluru','Test address')");
        jdbc.update("INSERT INTO screens(screen_id,venue_id,name) VALUES (1,1,'Screen 1'),(2,1,'Screen 2')");
        jdbc.update("INSERT INTO movies(movie_id,title,language,duration_minutes) VALUES (1,'Demo Movie','English',120)");
        LocalDateTime now = LocalDateTime.now(clock);
        for (long showId = 1; showId <= 3; showId++) {
            jdbc.update("INSERT INTO shows(show_id,movie_id,screen_id,start_time,end_time,status) VALUES (?,1,?,?,?,'ACTIVE')",
                    showId, showId == 3 ? 2 : 1, now.plusHours(showId * 3),
                    now.plusHours(showId * 3 + 2));
        }
        jdbc.update("INSERT INTO seats(seat_id,screen_id,row_identifier,seat_number,seat_category) " +
                "VALUES (1,1,'A',1,'NORMAL'),(2,1,'A',2,'NORMAL'),(3,1,'A',3,'VIP'),(4,2,'A',1,'NORMAL')");
        jdbc.update("INSERT INTO show_seats(show_id,seat_id,price,status,version) VALUES " +
                "(1,1,100.00,'AVAILABLE',0),(1,2,100.00,'AVAILABLE',0),(1,3,150.00,'AVAILABLE',0)," +
                "(2,1,110.00,'AVAILABLE',0),(2,2,110.00,'AVAILABLE',0),(3,4,200.00,'AVAILABLE',0)");
    }

    @Test
    void holdCreatesBookingItemsAndFiveMinuteDeadline() {
        BookingResult result = reserve(1, 1, 10);
        assertEquals(LocalDateTime.now(clock).plusMinutes(5), result.heldUntil());
        assertEquals(new BigDecimal("100.00"), result.totalAmount());
        assertEquals(1, count("booking_items"));
        assertEquals(result.bookingId(), slot(1, 1).getBookingId());
        assertEquals(InventoryStatus.HOLD, slot(1, 1).getStatus());
    }

    @Test
    void competingHoldsHaveExactlyOneWinner() throws Exception {
        int clients = Integer.getInteger("booking.test.clients", 16);
        assertTrue(clients >= 2 && clients <= 1000, "Use between 2 and 1000 clients");
        long started = System.nanoTime();
        List<BookingResult> results = race(IntStream.range(0, clients)
                .<Callable<BookingResult>>mapToObj(i -> () -> service.holdSeatForFiveMinutes(1L, 1L, 100L + i)).toList());
        assertEquals(1, results.stream().filter(BookingResult::success).count());
        assertEquals(1, count("bookings"));
        assertEquals(1, count("booking_items"));
        System.out.printf("Hold contention: %d requests, 1 winner, %.3f seconds%n",
                clients, (System.nanoTime() - started) / 1_000_000_000.0);
    }

    @Test
    void samePhysicalSeatCanBeHeldForDifferentShows() throws Exception {
        List<BookingResult> results = race(List.of(
                () -> service.holdSeatForFiveMinutes(1L, 1L, 10L),
                () -> service.holdSeatForFiveMinutes(2L, 1L, 20L)));
        assertTrue(results.stream().allMatch(BookingResult::success));
        assertNotEquals(slot(1, 1).getBookingId(), slot(2, 1).getBookingId());
    }

    @Test
    void databaseGuardPreventsDoubleHoldEvenWithoutRedis() throws Exception {
        BookingTransactionService database = context.getBean(BookingTransactionService.class);
        List<BookingResult> results = race(IntStream.range(0, 12)
                .<Callable<BookingResult>>mapToObj(i -> () -> {
                    try { return database.holdSeats(1L, List.of(1L), 100L + i); }
                    catch (BookingException e) { return BookingResult.failed(e.getMessage()); }
                }).toList());
        assertEquals(1, results.stream().filter(BookingResult::success).count());
        assertEquals(1, count("bookings"));
        assertEquals(1, count("booking_items"));
    }

    @Test
    void reusedSeatLabelsAcrossScreensAreAllowed() {
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM seats WHERE row_identifier='A' AND seat_number=1", Integer.class));
        assertTrue(service.holdSeatForFiveMinutes(3L, 4L, 10L).success());
    }

    @Test
    void seatFromAnotherScreenIsRejected() {
        assertFalse(service.holdSeatForFiveMinutes(1L, 4L, 10L).success());
        assertEquals(0, count("bookings"));
    }

    @Test
    void multiSeatFailureRollsBackEarlierClaimsAndItems() {
        reserve(1, 2, 10);
        assertFalse(service.holdSeatsForFiveMinutes(1L, List.of(1L, 2L), 20L).success());
        assertEquals(InventoryStatus.AVAILABLE, slot(1, 1).getStatus());
        assertEquals(1, count("bookings"));
        assertEquals(1, count("booking_items"));
    }

    @Test
    void duplicateSeatIdsAreRejected() {
        assertFalse(service.holdSeatsForFiveMinutes(1L, List.of(1L, 1L), 10L).success());
        assertEquals(0, count("bookings"));
    }

    @Test
    void expiredHoldCanBeReacquiredWithoutWaitingForWorker() {
        BookingResult old = reserve(1, 1, 10);
        clock.advance(Duration.ofMinutes(5));
        BookingResult replacement = reserve(1, 1, 20);
        assertNotEquals(old.bookingId(), replacement.bookingId());
        assertEquals(2, count("booking_items"), "Expired allocation history must not block a new booking");
        context.getBean(BookingExpiryService.class).expireBookings();
        assertEquals(BookingStatus.EXPIRED, booking(old).getStatus());
        assertEquals(replacement.bookingId(), slot(1, 1).getBookingId());
        assertEquals(InventoryStatus.HOLD, slot(1, 1).getStatus());
    }

    @Test
    void expiryWorkerReleasesOnlyItsOwnAllocationAndIsIdempotent() {
        BookingResult result = reserve(1, 1, 10);
        clock.advance(Duration.ofMinutes(5));
        context.getBean(BookingExpiryService.class).expireBookings();
        context.getBean(BookingExpiryService.class).expireBookings();
        assertEquals(BookingStatus.EXPIRED, booking(result).getStatus());
        assertEquals(InventoryStatus.AVAILABLE, slot(1, 1).getStatus());
        assertNull(slot(1, 1).getBookingId());
    }

    @Test
    void successfulPaymentConfirmsEveryReservedSeat() throws Exception {
        BookingResult result = service.holdSeatsForFiveMinutes(1L, List.of(1L, 2L), 10L);
        assertTrue(result.success());
        WebhookResult paid = pay(result, "event-1", "txn-1", result.totalAmount(), "INR", PaymentStatus.SUCCESS);
        assertTrue(paid.success());
        assertTrue(paid.paymentApplied());
        assertEquals(BookingStatus.CONFIRMED, booking(result).getStatus());
        assertEquals(InventoryStatus.BOOKED, slot(1, 1).getStatus());
        assertEquals(InventoryStatus.BOOKED, slot(1, 2).getStatus());
        assertEquals(1, count("payments"));
    }

    @Test
    void concurrentDuplicateCallbacksAreAppliedOnce() throws Exception {
        BookingResult result = reserve(1, 1, 10);
        String body = payload(result, "event-1", "txn-1", result.totalAmount(), "INR", PaymentStatus.SUCCESS);
        String signature = sign(body);
        List<WebhookResult> responses = race(IntStream.range(0, 12)
                .<Callable<WebhookResult>>mapToObj(i -> () -> service.processPaymentWebhookIdempotently(body, signature)).toList());
        assertTrue(responses.stream().allMatch(WebhookResult::paymentApplied));
        assertEquals(1, count("payments"));
        assertEquals(BookingStatus.CONFIRMED, booking(result).getStatus());
    }

    @Test
    void sameTransactionWithAnotherEventIdDoesNotApplyTwice() throws Exception {
        BookingResult result = reserve(1, 1, 10);
        assertTrue(pay(result, "event-1", "txn-1", result.totalAmount(), "INR", PaymentStatus.SUCCESS).paymentApplied());
        assertTrue(pay(result, "event-2", "txn-1", result.totalAmount(), "INR", PaymentStatus.SUCCESS).paymentApplied());
        assertEquals(1, count("payments"));
    }

    @Test
    void crossBookingIdempotencyCollisionCannotConfirmBothBookings() throws Exception {
        BookingResult first = reserve(1, 1, 10);
        BookingResult second = reserve(1, 2, 20);
        List<WebhookResult> results = race(List.of(
                () -> pay(first, "shared-event", "txn-1", first.totalAmount(), "INR", PaymentStatus.SUCCESS),
                () -> pay(second, "shared-event", "txn-2", second.totalAmount(), "INR", PaymentStatus.SUCCESS)));
        assertEquals(1, results.stream().filter(WebhookResult::paymentApplied).count());
        assertEquals(1, count("payments"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE status='CONFIRMED'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM show_seats WHERE status='HOLD'", Integer.class));
    }

    @Test
    void oneGatewayTransactionCannotPayTwoBookings() throws Exception {
        BookingResult first = reserve(1, 1, 10);
        BookingResult second = reserve(1, 2, 20);
        List<WebhookResult> results = race(List.of(
                () -> pay(first, "event-1", "shared-txn", first.totalAmount(), "INR", PaymentStatus.SUCCESS),
                () -> pay(second, "event-2", "shared-txn", second.totalAmount(), "INR", PaymentStatus.SUCCESS)));
        assertEquals(1, results.stream().filter(WebhookResult::paymentApplied).count());
        assertEquals(1, count("payments"));
    }

    @Test
    void wrongAmountOrCurrencyDoesNotChangeBooking() throws Exception {
        BookingResult result = reserve(1, 1, 10);
        assertFalse(pay(result, "event-1", "txn-1", new BigDecimal("99.99"), "INR", PaymentStatus.SUCCESS).success());
        assertFalse(pay(result, "event-2", "txn-2", result.totalAmount(), "USD", PaymentStatus.SUCCESS).success());
        assertEquals(0, count("payments"));
        assertEquals(BookingStatus.PENDING, booking(result).getStatus());
        assertEquals(InventoryStatus.HOLD, slot(1, 1).getStatus());
    }

    @Test
    void invalidSignatureAndTamperingAreRejectedBeforeWrites() throws Exception {
        BookingResult result = reserve(1, 1, 10);
        String body = payload(result, "event-1", "txn-1", result.totalAmount(), "INR", PaymentStatus.SUCCESS);
        assertFalse(service.processPaymentWebhookIdempotently(body, "0".repeat(64)).success());
        assertFalse(service.processPaymentWebhookIdempotently(body.replace("100.00", "1.00"), sign(body)).success());
        assertEquals(0, count("payments"));
        assertEquals(BookingStatus.PENDING, booking(result).getStatus());
    }

    @Test
    void failedPaymentReleasesHoldAndRetryIsIdempotent() throws Exception {
        BookingResult result = reserve(1, 1, 10);
        assertTrue(pay(result, "event-1", "txn-1", result.totalAmount(), "INR", PaymentStatus.FAILED).success());
        assertTrue(pay(result, "event-1", "txn-1", result.totalAmount(), "INR", PaymentStatus.FAILED).success());
        assertEquals(BookingStatus.CANCELLED, booking(result).getStatus());
        assertEquals(InventoryStatus.AVAILABLE, slot(1, 1).getStatus());
        assertEquals(PaymentStatus.FAILED, payments.findByIdempotencyKey("event-1").getStatus());
        assertEquals(1, count("payments"));
    }

    @Test
    void latePaymentRecordsRefundWithoutTakingReplacementHold() throws Exception {
        BookingResult old = reserve(1, 1, 10);
        clock.advance(Duration.ofMinutes(5));
        BookingResult replacement = reserve(1, 1, 20);
        WebhookResult result = pay(old, "event-1", "txn-1", old.totalAmount(), "INR", PaymentStatus.SUCCESS);
        assertTrue(result.success());
        assertTrue(result.refundRequired());
        assertFalse(result.paymentApplied());
        assertEquals(BookingStatus.EXPIRED, booking(old).getStatus());
        assertEquals(replacement.bookingId(), slot(1, 1).getBookingId());
        assertEquals(InventoryStatus.HOLD, slot(1, 1).getStatus());
        assertEquals(PaymentResolution.REFUND_REQUIRED, payments.findByIdempotencyKey("event-1").getResolution());
    }

    @Test
    void cancelledBookingCannotBeRevivedByPayment() throws Exception {
        BookingResult result = reserve(1, 1, 10);
        assertFalse(service.cancelBooking(result.bookingId(), 99L).success());
        assertTrue(service.cancelBooking(result.bookingId(), 10L).success());
        assertTrue(pay(result, "event-1", "txn-1", result.totalAmount(), "INR", PaymentStatus.SUCCESS).refundRequired());
        assertEquals(BookingStatus.CANCELLED, booking(result).getStatus());
        assertEquals(InventoryStatus.AVAILABLE, slot(1, 1).getStatus());
    }

    @Test
    void cancelledShowProducesRefundObligationInsteadOfConfirmation() throws Exception {
        BookingResult result = reserve(1, 1, 10);
        jdbc.update("UPDATE shows SET status='CANCELLED' WHERE show_id=1");
        assertTrue(pay(result, "event-1", "txn-1", result.totalAmount(), "INR", PaymentStatus.SUCCESS).refundRequired());
        assertEquals(InventoryStatus.AVAILABLE, slot(1, 1).getStatus());
        assertNotEquals(BookingStatus.CONFIRMED, booking(result).getStatus());
    }

    @Test
    void paymentRacingExpiryCannotReviveAnExpiredBooking() throws Exception {
        BookingResult result = reserve(1, 1, 10);
        clock.advance(Duration.ofMinutes(5));
        List<Boolean> responses = race(List.of(
                () -> pay(result, "event-1", "txn-1", result.totalAmount(), "INR", PaymentStatus.SUCCESS).refundRequired(),
                () -> { context.getBean(BookingExpiryService.class).expireBookings(); return true; }));
        assertTrue(responses.stream().allMatch(Boolean::booleanValue));
        assertEquals(BookingStatus.EXPIRED, booking(result).getStatus());
        assertEquals(InventoryStatus.AVAILABLE, slot(1, 1).getStatus());
        assertEquals(1, count("payments"));
    }

    @Test
    void bookedSeatCannotBeReclaimedAfterOriginalHoldDeadline() throws Exception {
        BookingResult result = reserve(1, 1, 10);
        assertTrue(pay(result, "event-1", "txn-1", result.totalAmount(), "INR", PaymentStatus.SUCCESS).paymentApplied());
        clock.advance(Duration.ofMinutes(6));
        context.getBean(BookingExpiryService.class).expireBookings();
        assertFalse(service.holdSeatForFiveMinutes(1L, 1L, 20L).success());
        assertEquals(InventoryStatus.BOOKED, slot(1, 1).getStatus());
    }

    @Test
    void missingAllocationCannotProduceConfirmedBooking() throws Exception {
        BookingResult result = service.holdSeatsForFiveMinutes(1L, List.of(1L, 2L), 10L);
        jdbc.update("UPDATE show_seats SET status='AVAILABLE',booking_id=NULL,held_by_user_id=NULL,held_until=NULL WHERE show_id=1 AND seat_id=2");
        WebhookResult response = pay(result, "event-1", "txn-1", result.totalAmount(), "INR", PaymentStatus.SUCCESS);
        assertTrue(response.refundRequired());
        assertEquals(BookingStatus.EXPIRED, booking(result).getStatus());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM show_seats WHERE status='BOOKED'", Integer.class));
    }

    @Test
    void duplicateEventWithChangedPayloadIsRejected() throws Exception {
        BookingResult result = reserve(1, 1, 10);
        assertTrue(pay(result, "event-1", "txn-1", result.totalAmount(), "INR", PaymentStatus.SUCCESS).paymentApplied());
        assertFalse(pay(result, "event-1", "txn-1", new BigDecimal("1.00"), "INR", PaymentStatus.SUCCESS).success());
        assertEquals(1, count("payments"));
    }

    @Test
    void moneyRejectsNullAndFractionalCents() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentWebhook("event", "booking", "txn", null, "INR", PaymentStatus.SUCCESS));
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentWebhook("event", "booking", "txn", new BigDecimal("0.30000000000000004"), "INR", PaymentStatus.SUCCESS));
        assertEquals(new BigDecimal("0.30"),
                new PaymentWebhook("event", "booking", "txn", new BigDecimal("0.30"), "INR", PaymentStatus.SUCCESS).amount());
    }

    @Test
    void databaseEnforcesPaymentForeignKeyAndStatusConstraint() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO payments(booking_id,gateway_transaction_id,idempotency_key,amount,currency,status,resolution) " +
                "VALUES ('missing','txn','event',1.00,'INR','SUCCESS','APPLIED')"));
        BookingResult result = reserve(1, 1, 10);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO payments(booking_id,gateway_transaction_id,idempotency_key,amount,currency,status,resolution) " +
                "VALUES (?,'txn','event',100.00,'INR','SUCESS','APPLIED')", result.bookingId()));
    }

    @Test
    void showListingCountsExpiredHoldsWithoutWaitingForSweep() throws Exception {
        String sql = new ClassPathResource("sql/list-shows.sql").getContentAsString(StandardCharsets.UTF_8);
        var query = new NamedParameterJdbcTemplate(jdbc);
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> parameters = new HashMap<>(Map.of("movie_id", 1L, "city", "Bengaluru",
                "day_start", now.toLocalDate().atStartOfDay(), "day_end", now.toLocalDate().plusDays(1).atStartOfDay(), "now", now));
        BookingResult held = reserve(1, 1, 10);
        assertEquals(held.heldUntil(), jdbc.queryForObject(
                "SELECT held_until FROM show_seats WHERE show_id=1 AND seat_id=1", LocalDateTime.class),
                "ORM and direct SQL readers must agree on the UTC deadline");
        List<Map<String, Object>> rows = query.queryForList(sql, parameters);
        assertEquals(3, rows.size());
        assertEquals(2, ((Number) rows.get(0).get("available_seats")).intValue());
        clock.advance(Duration.ofMinutes(5));
        parameters.put("now", LocalDateTime.now(clock));
        rows = query.queryForList(sql, parameters);
        assertEquals(3, ((Number) rows.get(0).get("available_seats")).intValue());
    }

    @Test
    void sampleDataExecutesAgainstTheMigratedSchema() {
        new ResourceDatabasePopulator(new ClassPathResource("sql/sample-data.sql"))
                .execute(context.getBean(DataSource.class));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE booking_id LIKE 'demo-%'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE gateway_transaction_id LIKE 'demo-%'", Integer.class));
        assertEquals(InventoryStatus.BOOKED, slot(101, 1003).getStatus());
    }

    @Test
    void plainTransactionalJoinsOuterRollback() {
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            reserve(1, 1, 10);
            throw new IllegalStateException("Outer workflow failed");
        }));
        assertEquals(0, count("bookings"));
        assertEquals(InventoryStatus.AVAILABLE, slot(1, 1).getStatus());
    }

    @Test
    void caughtInnerFailureMarksSharedTransactionRollbackOnly() {
        BookingResult result = reserve(1, 1, 10);
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThrows(UnexpectedRollbackException.class, () -> transaction.executeWithoutResult(status -> {
            try {
                assertFalse(pay(result, "event-1", "txn-1", new BigDecimal("1.00"), "INR", PaymentStatus.SUCCESS).success());
            } catch (Exception e) { throw new IllegalStateException(e); }
        }));
        assertEquals(0, count("payments"));
        assertEquals(BookingStatus.PENDING, booking(result).getStatus());
    }

    private BookingResult reserve(long show, long seat, long user) {
        BookingResult result = service.holdSeatForFiveMinutes(show, seat, user);
        assertTrue(result.success(), result.message());
        return result;
    }
    private Booking booking(BookingResult result) { return bookings.findById(result.bookingId()).orElseThrow(); }
    private ShowSeat slot(long show, long seat) { return inventory.findById(new ShowSeatId(show, seat)).orElseThrow(); }
    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private String payload(BookingResult booking, String key, String txn, BigDecimal amount, String currency, PaymentStatus outcome) throws Exception {
        return mapper.writeValueAsString(new PaymentWebhook(key, booking.bookingId(), txn, amount, currency, outcome));
    }
    private WebhookResult pay(BookingResult booking, String key, String txn, BigDecimal amount, String currency, PaymentStatus outcome) throws Exception {
        String body = payload(booking, key, txn, amount, currency, outcome);
        return service.processPaymentWebhookIdempotently(body, sign(body));
    }
    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(BookingTestContext.SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
    private <T> List<T> race(List<Callable<T>> requests) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(Math.min(32, requests.size()));
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> request : requests) {
                futures.add(workers.submit(() -> { start.await(); return request.call(); }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) { results.add(future.get(60, TimeUnit.SECONDS)); }
            return results;
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS), "Workers must stop before the database is reset");
        }
    }
}

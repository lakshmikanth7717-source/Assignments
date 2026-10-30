package repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import entity.Seat;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    @Modifying
    @Query("UPDATE Seat s SET s.status = 'HOLD', s.heldByUserId = :userId, s.heldUntil = :heldUntil " +
            "WHERE s.id = :seatId AND (s.status = 'AVAILABLE' OR (s.status = 'HOLD' AND s.heldUntil <= :now))")
    int holdSeatIfAvailable(@Param("seatId") Long seatId, @Param("userId") Long userId,
                            @Param("heldUntil") LocalDateTime heldUntil, @Param("now") LocalDateTime now);

    @Modifying
    @Query(value = "UPDATE show_seats ss JOIN booking_items bi " +
            "ON ss.show_id = bi.show_id AND ss.seat_id = bi.seat_id " +
            "SET ss.status = 'BOOKED', ss.locked_by_user_id = NULL, ss.lock_expires_at = NULL " +
            "WHERE bi.booking_id = :bookingId", nativeQuery = true)
    int confirmSeatsForBooking(@Param("bookingId") String bookingId);
}


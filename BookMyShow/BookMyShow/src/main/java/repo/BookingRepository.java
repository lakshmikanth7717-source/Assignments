package repo;

import entity.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") String bookingId);

    @Query("SELECT b.id FROM Booking b WHERE b.status = entity.BookingStatus.PENDING " +
            "AND b.expiresAt <= :now ORDER BY b.expiresAt, b.id")
    List<String> findExpiredBookingIds(@Param("now") LocalDateTime now, Pageable page);
}

package repo;

import entity.ShowSeat;
import entity.ShowSeatId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, ShowSeatId> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ShowSeat s SET s.status = entity.InventoryStatus.HOLD, " +
            "s.heldByUserId = :userId, s.heldUntil = :expiry, s.bookingId = :bookingId, s.version = s.version + 1 " +
            "WHERE s.id.showId = :showId AND s.id.seatId = :seatId AND " +
            "(s.status = entity.InventoryStatus.AVAILABLE OR " +
            "(s.status = entity.InventoryStatus.HOLD AND s.heldUntil <= :now))")
    int claim(@Param("showId") Long showId, @Param("seatId") Long seatId, @Param("userId") Long userId,
              @Param("bookingId") String bookingId, @Param("expiry") LocalDateTime expiry,
              @Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShowSeat s WHERE s.bookingId = :bookingId ORDER BY s.id.seatId")
    List<ShowSeat> findAllocationForUpdate(@Param("bookingId") String bookingId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ShowSeat s SET s.status = entity.InventoryStatus.BOOKED, s.heldByUserId = null, " +
            "s.heldUntil = null, s.version = s.version + 1 WHERE s.id.showId = :showId " +
            "AND s.bookingId = :bookingId AND s.heldByUserId = :userId " +
            "AND s.status = entity.InventoryStatus.HOLD AND s.heldUntil > :now")
    int confirm(@Param("showId") Long showId, @Param("bookingId") String bookingId,
                @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ShowSeat s SET s.status = entity.InventoryStatus.AVAILABLE, s.heldByUserId = null, " +
            "s.heldUntil = null, s.bookingId = null, s.version = s.version + 1 " +
            "WHERE s.bookingId = :bookingId AND s.status = entity.InventoryStatus.HOLD")
    int release(@Param("bookingId") String bookingId);
}

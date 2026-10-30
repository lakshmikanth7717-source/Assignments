package entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "show_seats")
public class ShowSeat {
    @EmbeddedId
    private ShowSeatId id;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private InventoryStatus status = InventoryStatus.AVAILABLE;

    @Column(name = "held_by_user_id")
    private Long heldByUserId;

    @Column(name = "held_until")
    private LocalDateTime heldUntil;

    @Column(name = "booking_id", length = 64)
    private String bookingId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public ShowSeatId getId() { return id; }
    public void setId(ShowSeatId id) { this.id = id; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public InventoryStatus getStatus() { return status; }
    public void setStatus(InventoryStatus status) { this.status = status; }
    public Long getHeldByUserId() { return heldByUserId; }
    public void setHeldByUserId(Long heldByUserId) { this.heldByUserId = heldByUserId; }
    public LocalDateTime getHeldUntil() { return heldUntil; }
    public void setHeldUntil(LocalDateTime heldUntil) { this.heldUntil = heldUntil; }
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}

package entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "seats")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seat_number", nullable = false, unique = true)
    private String seatNumber;

    @Column(name = "status", nullable = false)
    private String status = "AVAILABLE";

    @Column(name = "held_by_user_id")
    private Long heldByUserId;

    @Column(name = "held_until")
    private LocalDateTime heldUntil;

    public Seat() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getHeldByUserId() { return heldByUserId; }
    public void setHeldByUserId(Long heldByUserId) { this.heldByUserId = heldByUserId; }

    public LocalDateTime getHeldUntil() { return heldUntil; }
    public void setHeldUntil(LocalDateTime heldUntil) { this.heldUntil = heldUntil; }
}


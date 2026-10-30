package entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ShowSeatId implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "show_id", nullable = false)
    private Long showId;
    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    public ShowSeatId() {}
    public ShowSeatId(Long showId, Long seatId) { this.showId = showId; this.seatId = seatId; }
    public Long getShowId() { return showId; }
    public void setShowId(Long showId) { this.showId = showId; }
    public Long getSeatId() { return seatId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }
    @Override public boolean equals(Object other) {
        return other instanceof ShowSeatId id && Objects.equals(showId, id.showId) && Objects.equals(seatId, id.seatId);
    }
    @Override public int hashCode() { return Objects.hash(showId, seatId); }
}

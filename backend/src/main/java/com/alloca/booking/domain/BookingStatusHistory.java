package com.alloca.booking.domain;

import com.alloca.auth.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booking_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingStatusHistory {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 20)
    private BookingStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private BookingStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public BookingStatusHistory(UUID id, Booking booking, BookingStatus oldStatus,
            BookingStatus newStatus, User changedBy, String reason) {
        this(id, booking, oldStatus, newStatus, changedBy, reason, Instant.now());
    }

    public BookingStatusHistory(UUID id, Booking booking, BookingStatus oldStatus,
            BookingStatus newStatus, User changedBy, String reason, Instant now) {
        this.id = id;
        this.booking = booking;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.reason = reason;
        this.changedAt = now;
    }
}

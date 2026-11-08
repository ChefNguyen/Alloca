package com.alloca.booking.domain;

import com.alloca.auth.domain.User;
import com.alloca.resource.domain.Resource;
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
import lombok.Setter;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resource_id", nullable = false)
    private Resource resource;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by")
    private User cancelledBy;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(columnDefinition = "text")
    private String purpose;

    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Booking(UUID id, Resource resource, User user, User createdBy,
                   Instant startTime, Instant endTime, String purpose) {
        this(id, resource, user, createdBy, startTime, endTime, purpose, Instant.now());
    }

    public Booking(UUID id, Resource resource, User user, User createdBy,
                   Instant startTime, Instant endTime, String purpose, Instant now) {
        this.id = id;
        this.resource = resource;
        this.user = user;
        this.createdBy = createdBy;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = BookingStatus.CONFIRMED;
        this.purpose = purpose;
        this.createdAt = now;
        this.updatedAt = this.createdAt;
    }

    /** Trả về false và không thay đổi trạng thái nếu việc hủy không còn được phép (đã qua giờ bắt đầu hoặc không phải CONFIRMED). */
    public boolean cancel(User actor, String reason, Instant now) {
        java.util.Objects.requireNonNull(actor, "Cancellation actor is required");
        java.util.Objects.requireNonNull(now, "Cancellation time is required");
        if (status != BookingStatus.CONFIRMED || !now.isBefore(startTime)) {
            return false;
        }
        status = BookingStatus.CANCELLED;
        cancelledBy = actor;
        cancelReason = reason;
        updatedAt = now;
        return true;
    }

    /** Tiện ích đổi nhanh sang Value Object TimeSlot để tính toán */
    public TimeSlot toTimeSlot() {
        return new TimeSlot(this.startTime, this.endTime);
    }
}

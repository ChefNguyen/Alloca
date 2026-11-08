package com.alloca.notification.repository;

import com.alloca.notification.domain.Notification;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * A booking event has at most one notification per recipient, even on retry.
     * Flush pending JPA booking/history writes before the native insert checks its FK.
     * The database also checks active state at insertion rather than trusting a cached entity.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into notifications
                (id, user_id, booking_id, event_type, dedup_key, title, message, is_read, created_at)
            select :id, u.id, :bookingId, :eventType, :dedupKey, :title, :message, false, :createdAt
            from users u
            where u.id = :recipientId and u.is_active = true
            on conflict (dedup_key) do nothing
            """, nativeQuery = true)
    int insertBookingNotification(
            @Param("id") UUID id,
            @Param("recipientId") UUID recipientId,
            @Param("bookingId") UUID bookingId,
            @Param("eventType") String eventType,
            @Param("dedupKey") String dedupKey,
            @Param("title") String title,
            @Param("message") String message,
            @Param("createdAt") Instant createdAt);
}

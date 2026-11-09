package com.alloca.notification.service;

import com.alloca.auth.domain.User;
import com.alloca.booking.domain.Booking;
import com.alloca.notification.domain.NotificationType;
import com.alloca.notification.repository.NotificationRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores mandatory in-app booking notifications in the caller's transaction.
 * No network delivery occurs while the booking/resource locks are held.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void confirmBooking(Booking booking, Instant now) {
        notifyRecipient(booking, booking.getUser(), NotificationType.BOOKING_CONFIRMED,
                "Booking confirmed", "Your booking has been confirmed.", now);
    }

    public void cancelBooking(Booking booking, Instant now) {
        User owner = booking.getUser();
        User creator = booking.getCreatedBy();
        notifyRecipient(booking, owner, NotificationType.BOOKING_CANCELLED,
                "Booking cancelled", "Your booking has been cancelled.", now);
        if (!owner.getId().equals(creator.getId())) {
            notifyRecipient(booking, creator, NotificationType.BOOKING_CANCELLED,
                    "Booking cancelled", "A booking you created has been cancelled.", now);
        }
    }

    private void notifyRecipient(Booking booking, User recipient, NotificationType type,
                                 String title, String message, Instant now) {
        if (!recipient.isActive()) {
            return;
        }
        String dedupKey = type.name() + ":" + booking.getId() + ":" + recipient.getId();
        notificationRepository.insertBookingNotification(
                UuidCreator.getTimeOrderedEpoch(), recipient.getId(), booking.getId(),
                type.name(), dedupKey, title, message, now);
    }

    public void dispatchUtilizationAlert(UUID alertId, UUID recipientUserId, String resourceName, BigDecimal rate, Instant now) {
        String dedupKey = NotificationType.UTILIZATION_ALERT.name() + ":" + alertId + ":" + recipientUserId;
        String title = "Cảnh báo vượt ngưỡng sử dụng: " + resourceName;
        String message = String.format("Tài nguyên %s có tỷ lệ sử dụng đạt %.1f%% trong 7 ngày qua, vượt ngưỡng quy định.",
                resourceName, rate != null ? rate.doubleValue() : 0.0);
        notificationRepository.insertAlertNotification(
                UuidCreator.getTimeOrderedEpoch(),
                recipientUserId,
                alertId,
                NotificationType.UTILIZATION_ALERT.name(),
                dedupKey,
                title,
                message,
                now);
    }
}

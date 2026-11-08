package com.alloca.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import com.alloca.booking.domain.Booking;
import com.alloca.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2030-06-10T00:00:00Z");

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    @Test
    void confirmationIsSentOnlyToOwnerEvenForDelegatedBooking() {
        User owner = user(UUID.randomUUID());
        User creator = user(UUID.randomUUID());
        Booking booking = booking(owner, creator);

        notificationService.confirmBooking(booking, NOW);

        verifyNotification(booking, owner, "BOOKING_CONFIRMED");
        verifyNoMoreInteractions(notificationRepository);
    }

    @Test
    void cancellationNotifiesOwnerAndOriginalCreatorButNotCancellingActor() {
        User owner = user(UUID.randomUUID());
        User creator = user(UUID.randomUUID());
        Booking booking = booking(owner, creator);
        booking.setCancelledBy(user(UUID.randomUUID()));

        notificationService.cancelBooking(booking, NOW);

        verifyNotification(booking, owner, "BOOKING_CANCELLED");
        verifyNotification(booking, creator, "BOOKING_CANCELLED");
        verifyNoMoreInteractions(notificationRepository);
    }

    @Test
    void cancellationDeduplicatesOwnerAndCreatorByIdRatherThanObjectIdentity() {
        UUID userId = UUID.randomUUID();
        User owner = user(userId);
        Booking booking = booking(owner, user(userId));

        notificationService.cancelBooking(booking, NOW);

        verifyNotification(booking, owner, "BOOKING_CANCELLED");
        verifyNoMoreInteractions(notificationRepository);
    }

    @Test
    void confirmationDoesNotNotifyInactiveOwner() {
        User owner = user(UUID.randomUUID());
        owner.deactivate();

        notificationService.confirmBooking(booking(owner, user(UUID.randomUUID())), NOW);

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void cancellationSkipsInactiveCreatorAndStillNotifiesOwner() {
        User owner = user(UUID.randomUUID());
        User creator = user(UUID.randomUUID());
        creator.deactivate();
        Booking booking = booking(owner, creator);

        notificationService.cancelBooking(booking, NOW);

        verifyNotification(booking, owner, "BOOKING_CANCELLED");
        verifyNoMoreInteractions(notificationRepository);
    }

    @Test
    void cancellationSkipsInactiveOwnerAndStillNotifiesCreator() {
        User owner = user(UUID.randomUUID());
        owner.deactivate();
        User creator = user(UUID.randomUUID());
        Booking booking = booking(owner, creator);

        notificationService.cancelBooking(booking, NOW);

        verifyNotification(booking, creator, "BOOKING_CANCELLED");
        verifyNoMoreInteractions(notificationRepository);
    }

    @Test
    void retriesUseTheSameEventRecipientKeyDespiteNewNotificationIds() {
        User owner = user(UUID.randomUUID());
        Booking booking = booking(owner, owner);

        notificationService.confirmBooking(booking, NOW);
        notificationService.confirmBooking(booking, NOW.plusSeconds(1));
        notificationService.cancelBooking(booking, NOW.plusSeconds(2));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository, times(3)).insertBookingNotification(
                any(UUID.class), eq(owner.getId()), eq(booking.getId()), anyString(), keys.capture(),
                anyString(), anyString(), any(Instant.class));
        assertThat(keys.getAllValues()).containsExactly(
                key(booking, owner, "BOOKING_CONFIRMED"),
                key(booking, owner, "BOOKING_CONFIRMED"),
                key(booking, owner, "BOOKING_CANCELLED"));
    }

    private void verifyNotification(Booking booking, User recipient, String type) {
        verify(notificationRepository).insertBookingNotification(
                any(UUID.class), eq(recipient.getId()), eq(booking.getId()), eq(type),
                eq(key(booking, recipient, type)), anyString(), anyString(), eq(NOW));
    }

    private static String key(Booking booking, User recipient, String type) {
        return type + ":" + booking.getId() + ":" + recipient.getId();
    }

    private static User user(UUID id) {
        return new User(id, "user-" + id, id + "@example.test", "unused-hash", "Test User",
                Role.EMPLOYEE, null);
    }

    private static Booking booking(User owner, User creator) {
        return new Booking(UUID.randomUUID(), null, owner, creator,
                NOW.plusSeconds(3600), NOW.plusSeconds(7200), "Test booking");
    }
}

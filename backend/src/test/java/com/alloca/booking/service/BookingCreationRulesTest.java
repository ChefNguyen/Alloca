package com.alloca.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.alloca.auth.domain.*;
import com.alloca.auth.repository.UserRepository;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.booking.domain.Booking;
import com.alloca.booking.dto.BookingCreateRequest;
import com.alloca.booking.repository.*;
import com.alloca.common.exception.ApiException;
import com.alloca.common.exception.ErrorCode;
import com.alloca.notification.service.NotificationService;
import com.alloca.resource.domain.*;
import com.alloca.resource.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingCreationRulesTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2030-06-10T02:00:00Z"); // local 09:00
    @Mock ResourceRepository resources;
    @Mock ResourceScheduleRepository schedules;
    @Mock BookingRepository bookings;
    @Mock BookingStatusHistoryRepository histories;
    @Mock UserRepository users;
    @Mock NotificationService notifications;
    @Mock Clock clock;
    private BookingService service;
    private User owner;
    private Resource resource;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new BookingService(resources, schedules, bookings, histories, users, notifications, clock, ZONE);
        owner = new User(UUID.randomUUID(), "owner", "owner@test.invalid", "hash", "Owner", Role.EMPLOYEE, null);
        principal = new UserPrincipal(owner.getId(), "owner", Role.EMPLOYEE, null);
        resource = new Resource(UUID.randomUUID(), ResourceStatus.AVAILABLE);
        when(clock.instant()).thenReturn(NOW);
    }

    private BookingCreateRequest request(Instant start) {
        return new BookingCreateRequest(resource.getId(), null, start, start.plusSeconds(3600), null);
    }

    private void stubActorAndLock() {
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(resources.findByIdForUpdate(resource.getId())).thenReturn(Optional.of(resource));
    }

    private ResourceSchedule window(Instant day, int start, int end) {
        return new ResourceSchedule(UUID.randomUUID(), resource.getId(), day.atZone(ZONE).getDayOfWeek(),
                LocalTime.of(start, 0), LocalTime.of(end, 0));
    }

    @Test
    void rejectsTimeThatPassedWhileWaitingForResourceLock() {
        Instant start = NOW.plusSeconds(1800);
        stubActorAndLock();
        when(clock.instant()).thenReturn(NOW, start);
        assertThatThrownBy(() -> service.createBooking(request(start), principal)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(schedules, bookings, histories, notifications);
    }

    @Test
    void horizonUsesExactInstantInsteadOfEndOfLocalDate() {
        Instant start = NOW.plus(Duration.ofDays(30)).plusSeconds(1800);
        assertThatThrownBy(() -> service.createBooking(request(start), principal)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verifyNoInteractions(resources, users, bookings, notifications);
    }

    @Test
    void acceptsExactThirtyDayBoundary() {
        Instant start = NOW.plus(Duration.ofDays(30));
        stubActorAndLock();
        when(schedules.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resource.getId(), (short) start.atZone(ZONE).getDayOfWeek().getValue()))
                .thenReturn(List.of(window(start, 8, 17)));
        when(bookings.save(any(Booking.class))).thenAnswer(call -> call.getArgument(0));
        assertThat(service.createBooking(request(start), principal).startTime()).isEqualTo(start);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void permitsAdjacentWindowsButRejectsCrossingActualBreak(boolean adjacent) {
        Instant start = NOW.plusSeconds(7200); // 11:00 to 13:00 local
        stubActorAndLock();
        when(schedules.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resource.getId(), (short) 1))
                .thenReturn(List.of(window(start, 8, 12), window(start, adjacent ? 12 : 13, 17)));
        BookingCreateRequest request = new BookingCreateRequest(resource.getId(), null, start, start.plusSeconds(7200), null);
        if (adjacent) {
            when(bookings.save(any(Booking.class))).thenAnswer(call -> call.getArgument(0));
            assertThat(service.createBooking(request, principal).endTime()).isEqualTo(start.plusSeconds(7200));
        } else {
            assertThatThrownBy(() -> service.createBooking(request, principal)).isInstanceOfSatisfying(ApiException.class,
                    error -> assertThat(error.getCode()).isEqualTo(ErrorCode.OUTSIDE_OPERATING_HOURS));
            verifyNoInteractions(bookings, histories, notifications);
        }
    }

    @Test
    void staleAdminPrincipalCannotBypassCurrentEmployeeRole() {
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        UserPrincipal stale = new UserPrincipal(owner.getId(), "owner", Role.ADMIN, null);
        BookingCreateRequest request = new BookingCreateRequest(resource.getId(), UUID.randomUUID(),
                NOW.plusSeconds(3600), NOW.plusSeconds(7200), null);
        assertThatThrownBy(() -> service.createBooking(request, stale)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        verifyNoInteractions(resources, bookings, histories, notifications);
    }
}

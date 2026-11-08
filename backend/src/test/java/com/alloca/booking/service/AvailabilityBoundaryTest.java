package com.alloca.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.alloca.auth.repository.UserRepository;
import com.alloca.booking.repository.BookingRepository;
import com.alloca.booking.repository.BookingStatusHistoryRepository;
import com.alloca.notification.service.NotificationService;
import com.alloca.resource.domain.Resource;
import com.alloca.resource.domain.ResourceSchedule;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.repository.ResourceRepository;
import com.alloca.resource.repository.ResourceScheduleRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityBoundaryTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    @Mock ResourceRepository resources;
    @Mock ResourceScheduleRepository schedules;
    @Mock BookingRepository bookings;
    @Mock BookingStatusHistoryRepository histories;
    @Mock UserRepository users;
    @Mock NotificationService notifications;

    private BookingService service(Instant now, Resource resource, LocalDate date, List<ResourceSchedule> hours) {
        when(resources.findById(resource.getId())).thenReturn(Optional.of(resource));
        when(schedules.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resource.getId(), (short) date.getDayOfWeek().getValue()))
                .thenReturn(hours);
        when(bookings.findConfirmedSlotsOverlapping(eq(resource.getId()), any(), any())).thenReturn(List.of());
        return new BookingService(resources, schedules, bookings, histories, users, notifications, Clock.fixed(now, ZONE), ZONE);
    }

    private ResourceSchedule window(Resource resource, LocalDate date, String start, String end) {
        return new ResourceSchedule(UUID.randomUUID(), resource.getId(), date.getDayOfWeek(), LocalTime.parse(start), LocalTime.parse(end));
    }

    @Test
    void lastDayOnlyOffersStartsWithinExactThirtyDayHorizon() {
        Instant now = Instant.parse("2030-06-10T02:00:00Z"); // 09:00 local
        LocalDate lastDate = now.atZone(ZONE).toLocalDate().plusDays(30);
        Resource resource = new Resource(UUID.randomUUID(), ResourceStatus.AVAILABLE);
        var service = service(now, resource, lastDate, List.of(window(resource, lastDate, "08:00", "10:00")));
        assertThat(service.getResourceAvailability(resource.getId(), lastDate, 30).availableSlots())
                .extracting(slot -> slot.startTime().atZone(ZONE).toLocalTime())
                .containsExactly(LocalTime.of(8, 0), LocalTime.of(8, 30), LocalTime.of(9, 0));
    }

    @Test
    void slotCanCrossAnOddMinuteBoundaryBetweenAdjacentOperatingWindows() {
        Instant now = Instant.parse("2030-06-10T00:00:00Z");
        LocalDate date = now.atZone(ZONE).toLocalDate();
        Resource resource = new Resource(UUID.randomUUID(), ResourceStatus.AVAILABLE);
        var service = service(now, resource, date, List.of(
                window(resource, date, "08:15", "09:15"), window(resource, date, "09:15", "10:15")));
        assertThat(service.getResourceAvailability(resource.getId(), date, 30).availableSlots())
                .extracting(slot -> slot.startTime().atZone(ZONE).toLocalTime())
                .containsExactly(LocalTime.of(8, 30), LocalTime.of(9, 0), LocalTime.of(9, 30));
    }
}

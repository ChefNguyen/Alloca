package com.alloca.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.resource.domain.Resource;
import com.alloca.resource.domain.ResourceSchedule;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.dto.ResourceDetailResponse;
import com.alloca.resource.dto.ResourceScheduleReplaceRequest;
import com.alloca.resource.dto.ResourceScheduleReplaceRequest.Window;
import com.alloca.resource.repository.ResourceRepository;
import com.alloca.resource.repository.ResourceRepository.ActiveBooking;
import com.alloca.resource.repository.ResourceScheduleRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;

class ResourceScheduleServiceTest {

    // Monday 2026-10-26 10:00 in Vietnam (UTC+7).
    private static final Instant NOW = Instant.parse("2026-10-26T03:00:00Z");

    private final ResourceRepository resourceRepository = mock(ResourceRepository.class);
    private final ResourceScheduleRepository scheduleRepository = mock(ResourceScheduleRepository.class);
    private ResourceScheduleService service;
    private Resource resource;

    @BeforeEach
    void setUp() {
        service = new ResourceScheduleService(resourceRepository, scheduleRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Asia/Ho_Chi_Minh"));
        resource = resource(ResourceStatus.AVAILABLE);
        when(resourceRepository.findActiveConfirmedBookings(any(), any())).thenReturn(List.of());
        when(scheduleRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(resource.getId()))
                .thenReturn(List.of());
    }

    private Resource resource(ResourceStatus status) {
        Resource created = new Resource(UUID.randomUUID(), status);
        when(resourceRepository.findByIdForUpdate(created.getId())).thenReturn(Optional.of(created));
        when(resourceRepository.findById(created.getId())).thenReturn(Optional.of(created));
        return created;
    }

    private static ResourceScheduleReplaceRequest request(Window... windows) {
        return new ResourceScheduleReplaceRequest(List.of(windows));
    }

    /** Booking on Monday 2026-10-26, local hours. */
    private static ActiveBooking mondayBooking(int startHour, int endHour) {
        Instant midnight = Instant.parse("2026-10-25T17:00:00Z");
        return new ActiveBooking(UUID.randomUUID(),
                midnight.plusSeconds(startHour * 3600L), midnight.plusSeconds(endHour * 3600L));
    }

    @Test
    void replaceDeletesTheOldScheduleThenSavesTheNewWindows() {
        List<ResourceDetailResponse.Schedule> result = service.replace(resource.getId(), request(
                new Window(DayOfWeek.MONDAY, "08:00", "12:00"),
                new Window(DayOfWeek.MONDAY, "13:00", "17:00")));

        InOrder order = inOrder(scheduleRepository);
        order.verify(scheduleRepository).deleteByResourceId(resource.getId());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ResourceSchedule>> saved = ArgumentCaptor.forClass(List.class);
        order.verify(scheduleRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(2)
                .allSatisfy(schedule -> {
                    assertThat(schedule.getResourceId()).isEqualTo(resource.getId());
                    assertThat(schedule.getId()).isNotNull();
                    assertThat((int) schedule.getDayOfWeek()).isEqualTo(1);
                });
        assertThat(result).isEmpty(); // re-read from the (mocked) repository
        verify(resourceRepository).findByIdForUpdate(resource.getId());
    }

    @Test
    void replaceReturnsTheStoredScheduleFormatted() {
        ResourceSchedule stored = new ResourceSchedule(UUID.randomUUID(), resource.getId(), DayOfWeek.FRIDAY,
                java.time.LocalTime.of(8, 0), java.time.LocalTime.of(12, 30));
        when(scheduleRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(resource.getId()))
                .thenReturn(List.of(stored));

        List<ResourceDetailResponse.Schedule> result = service.replace(resource.getId(),
                request(new Window(DayOfWeek.FRIDAY, "08:00", "12:30")));

        assertThat(result).singleElement().satisfies(schedule -> {
            assertThat(schedule.dayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
            assertThat(schedule.startTime()).isEqualTo("08:00:00");
            assertThat(schedule.endTime()).isEqualTo("12:30:00");
        });
    }

    @Test
    void conflictingBookingsBlockTheChangeAndKeepTheOldSchedule() {
        // TC-RESOURCE-01: booking 09:00-11:00, new schedule 13:00-17:00.
        ActiveBooking booking = mondayBooking(9, 11);
        when(resourceRepository.findActiveConfirmedBookings(resource.getId(), NOW)).thenReturn(List.of(booking));

        assertThatThrownBy(() -> service.replace(resource.getId(),
                request(new Window(DayOfWeek.MONDAY, "13:00", "17:00"))))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_SCHEDULE_CONFLICTS_WITH_BOOKINGS);
                    assertThat(ex.getDetails()).isEqualTo(Map.of("conflictingBookingIds", List.of(booking.id())));
                });
        verify(scheduleRepository, never()).deleteByResourceId(any());
        verify(scheduleRepository, never()).saveAll(anyList());
    }

    @Test
    void onlyTheBookingsOutsideTheNewHoursAreReported() {
        ActiveBooking inside = mondayBooking(13, 14);
        ActiveBooking outside = mondayBooking(9, 10);
        when(resourceRepository.findActiveConfirmedBookings(resource.getId(), NOW))
                .thenReturn(List.of(outside, inside));

        assertThatThrownBy(() -> service.replace(resource.getId(),
                request(new Window(DayOfWeek.MONDAY, "13:00", "17:00"))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getDetails())
                                .isEqualTo(Map.of("conflictingBookingIds", List.of(outside.id()))));
    }

    @Test
    void bookingsInsideTheNewHoursDoNotBlockTheChange() {
        when(resourceRepository.findActiveConfirmedBookings(resource.getId(), NOW))
                .thenReturn(List.of(mondayBooking(13, 15)));

        service.replace(resource.getId(), request(new Window(DayOfWeek.MONDAY, "13:00", "17:00")));

        verify(scheduleRepository).saveAll(anyList());
    }

    @Test
    void closingTheDayOfAnExistingBookingIsAConflict() {
        when(resourceRepository.findActiveConfirmedBookings(resource.getId(), NOW))
                .thenReturn(List.of(mondayBooking(13, 15)));

        assertThatThrownBy(() -> service.replace(resource.getId(),
                request(new Window(DayOfWeek.TUESDAY, "08:00", "17:00"))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_SCHEDULE_CONFLICTS_WITH_BOOKINGS));
    }

    @Test
    void anOngoingBookingIsProtectedToo() {
        // Started 09:00, still running at NOW (10:00) until 11:00.
        when(resourceRepository.findActiveConfirmedBookings(resource.getId(), NOW))
                .thenReturn(List.of(mondayBooking(9, 11)));

        assertThatThrownBy(() -> service.replace(resource.getId(),
                request(new Window(DayOfWeek.MONDAY, "10:30", "17:00"))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void validationFailuresDoNotTouchTheDatabaseRows() {
        assertThatThrownBy(() -> service.replace(resource.getId(), request(
                new Window(DayOfWeek.MONDAY, "08:00", "12:00"),
                new Window(DayOfWeek.MONDAY, "11:00", "15:00"))))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        verify(scheduleRepository, never()).deleteByResourceId(any());
    }

    @Test
    void suspendedResourcesCanStillBeScheduled() {
        Resource suspended = resource(ResourceStatus.SUSPENDED);
        when(scheduleRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(suspended.getId()))
                .thenReturn(new ArrayList<>());

        service.replace(suspended.getId(), request(new Window(DayOfWeek.MONDAY, "08:00", "17:00")));

        verify(scheduleRepository).deleteByResourceId(suspended.getId());
    }

    @Test
    void unknownOrDeletedResourcesAreNotFound() {
        UUID unknown = UUID.randomUUID();
        when(resourceRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());
        when(resourceRepository.findById(unknown)).thenReturn(Optional.empty());
        Resource deleted = resource(ResourceStatus.SUSPENDED);
        deleted.markDeleted(NOW);

        ResourceScheduleReplaceRequest anyRequest = request(new Window(DayOfWeek.MONDAY, "08:00", "17:00"));
        for (UUID id : List.of(unknown, deleted.getId())) {
            assertThatThrownBy(() -> service.replace(id, anyRequest)).isInstanceOfSatisfying(ApiException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
            assertThatThrownBy(() -> service.get(id)).isInstanceOfSatisfying(ApiException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }
        verify(scheduleRepository, never()).deleteByResourceId(any());
    }

    @Test
    void getReturnsTheStoredWindows() {
        ResourceSchedule stored = new ResourceSchedule(UUID.randomUUID(), resource.getId(), DayOfWeek.MONDAY,
                java.time.LocalTime.of(8, 0), java.time.LocalTime.of(17, 0));
        when(scheduleRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(resource.getId()))
                .thenReturn(List.of(stored));

        assertThat(service.get(resource.getId())).singleElement()
                .satisfies(schedule -> assertThat(schedule.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY));
    }
}

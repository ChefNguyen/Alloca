package com.alloca.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alloca.auth.domain.Department;
import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import com.alloca.auth.repository.UserRepository;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.booking.domain.Booking;
import com.alloca.booking.domain.BookingStatus;
import com.alloca.booking.domain.BookingStatusHistory;
import com.alloca.booking.domain.TimeSlot;
import com.alloca.booking.dto.BookingCreateRequest;
import com.alloca.booking.dto.BookingResponse;
import com.alloca.booking.dto.ResourceAvailabilityResponse;
import com.alloca.booking.repository.BookingRepository;
import com.alloca.booking.repository.BookingStatusHistoryRepository;
import com.alloca.common.exception.ApiException;
import com.alloca.common.exception.ErrorCode;
import com.alloca.notification.service.NotificationService;
import com.alloca.resource.domain.Resource;
import com.alloca.resource.domain.ResourceSchedule;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.repository.ResourceRepository;
import com.alloca.resource.repository.ResourceScheduleRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 11, 2); // Monday

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private ResourceScheduleRepository scheduleRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    private Clock clock;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        // Mock server now: 2026-11-02 07:00:00 UTC+7 (before operating hours)
        Instant fixedNow = FIXED_DATE.atTime(7, 0).atZone(BUSINESS_ZONE).toInstant();
        clock = Clock.fixed(fixedNow, BUSINESS_ZONE);
        bookingService = new BookingService(
                resourceRepository,
                scheduleRepository,
                bookingRepository,
                bookingStatusHistoryRepository,
                userRepository,
                notificationService,
                clock,
                BUSINESS_ZONE
        );
    }

    private Resource createResource(UUID id, ResourceStatus status) {
        if (status == ResourceStatus.DELETED) {
            Resource r = new Resource(id, ResourceStatus.SUSPENDED);
            r.markDeleted(Instant.now());
            return r;
        }
        return new Resource(id, status);
    }

    private ResourceSchedule createSchedule(UUID resourceId, DayOfWeek day, LocalTime start, LocalTime end) {
        return new ResourceSchedule(UUID.randomUUID(), resourceId, day, start, end);
    }

    private Instant instantAt(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(BUSINESS_ZONE).toInstant();
    }

    @Test
    @DisplayName("Returns available slots excluding confirmed bookings for an available resource")
    void returnsAvailableSlotsExcludingConfirmedBookings() {
        UUID resourceId = UUID.randomUUID();
        Resource resource = createResource(resourceId, ResourceStatus.AVAILABLE);
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));

        // Monday schedule: 08:00 - 12:00
        List<ResourceSchedule> schedules = List.of(
                createSchedule(resourceId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))
        );
        when(scheduleRepository.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, (short) 1))
                .thenReturn(schedules);

        // One confirmed booking: 09:00 - 10:30
        List<TimeSlot> confirmedBookings = List.of(
                new TimeSlot(instantAt(FIXED_DATE, LocalTime.of(9, 0)), instantAt(FIXED_DATE, LocalTime.of(10, 30)))
        );
        when(bookingRepository.findConfirmedSlotsOverlapping(eq(resourceId), any(Instant.class), any(Instant.class)))
                .thenReturn(confirmedBookings);

        ResourceAvailabilityResponse response = bookingService.getResourceAvailability(resourceId, FIXED_DATE, 30);

        assertThat(response.resourceId()).isEqualTo(resourceId);
        assertThat(response.date()).isEqualTo(FIXED_DATE);
        assertThat(response.timezone()).isEqualTo(BUSINESS_ZONE.getId());
        assertThat(response.resourceStatus()).isEqualTo(ResourceStatus.AVAILABLE);

        // Operating windows: 08:00 - 12:00
        assertThat(response.operatingWindows()).hasSize(1);
        assertThat(response.operatingWindows().get(0).startTime()).isEqualTo(instantAt(FIXED_DATE, LocalTime.of(8, 0)));
        assertThat(response.operatingWindows().get(0).endTime()).isEqualTo(instantAt(FIXED_DATE, LocalTime.of(12, 0)));

        // 8 slots total (4 hours = 8 * 30 min), 3 slots booked (09:00-09:30, 09:30-10:00, 10:00-10:30)
        // 5 available slots remaining
        assertThat(response.availableSlots()).hasSize(5);
        assertThat(response.availableSlots()).extracting(s -> s.startTime())
                .containsExactly(
                        instantAt(FIXED_DATE, LocalTime.of(8, 0)),
                        instantAt(FIXED_DATE, LocalTime.of(8, 30)),
                        instantAt(FIXED_DATE, LocalTime.of(10, 30)),
                        instantAt(FIXED_DATE, LocalTime.of(11, 0)),
                        instantAt(FIXED_DATE, LocalTime.of(11, 30))
                );
    }

    @Test
    @DisplayName("Throws 404 RESOURCE_NOT_FOUND when resource does not exist")
    void throwsNotFoundWhenResourceDoesNotExist() {
        UUID resourceId = UUID.randomUUID();
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getResourceAvailability(resourceId, FIXED_DATE, 30))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                });

        verifyNoInteractions(scheduleRepository, bookingRepository);
    }

    @Test
    @DisplayName("Throws 404 RESOURCE_NOT_FOUND when resource is soft-deleted")
    void throwsNotFoundWhenResourceIsDeleted() {
        UUID resourceId = UUID.randomUUID();
        Resource resource = createResource(resourceId, ResourceStatus.DELETED);
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> bookingService.getResourceAvailability(resourceId, FIXED_DATE, 30))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                });

        verifyNoInteractions(scheduleRepository, bookingRepository);
    }

    @Test
    @DisplayName("Returns empty availableSlots when resource is SUSPENDED")
    void returnsEmptyAvailableSlotsWhenResourceIsSuspended() {
        UUID resourceId = UUID.randomUUID();
        Resource resource = createResource(resourceId, ResourceStatus.SUSPENDED);
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));

        List<ResourceSchedule> schedules = List.of(
                createSchedule(resourceId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))
        );
        when(scheduleRepository.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, (short) 1))
                .thenReturn(schedules);

        ResourceAvailabilityResponse response = bookingService.getResourceAvailability(resourceId, FIXED_DATE, 30);

        assertThat(response.resourceStatus()).isEqualTo(ResourceStatus.SUSPENDED);
        assertThat(response.operatingWindows()).hasSize(1);
        assertThat(response.availableSlots()).isEmpty();
        verifyNoInteractions(bookingRepository);
    }

    @Test
    @DisplayName("Returns empty availableSlots and operatingWindows when day is closed")
    void returnsEmptyWhenDayIsClosed() {
        UUID resourceId = UUID.randomUUID();
        Resource resource = createResource(resourceId, ResourceStatus.AVAILABLE);
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));

        when(scheduleRepository.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, (short) 1))
                .thenReturn(List.of());

        ResourceAvailabilityResponse response = bookingService.getResourceAvailability(resourceId, FIXED_DATE, 30);

        assertThat(response.operatingWindows()).isEmpty();
        assertThat(response.availableSlots()).isEmpty();
        verifyNoInteractions(bookingRepository);
    }

    @Test
    @DisplayName("Returns empty availableSlots when requested date is in the past")
    void returnsEmptyAvailableSlotsWhenDateIsInPast() {
        UUID resourceId = UUID.randomUUID();
        Resource resource = createResource(resourceId, ResourceStatus.AVAILABLE);
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));

        LocalDate pastDate = FIXED_DATE.minusDays(1);
        when(scheduleRepository.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, (short) pastDate.getDayOfWeek().getValue()))
                .thenReturn(List.of(createSchedule(resourceId, pastDate.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(17, 0))));

        ResourceAvailabilityResponse response = bookingService.getResourceAvailability(resourceId, pastDate, 30);

        assertThat(response.operatingWindows()).hasSize(1);
        assertThat(response.availableSlots()).isEmpty();
        verifyNoInteractions(bookingRepository);
    }

    @Test
    @DisplayName("Returns empty availableSlots when requested date is beyond 30-day horizon")
    void returnsEmptyAvailableSlotsWhenDateIsBeyondHorizon() {
        UUID resourceId = UUID.randomUUID();
        Resource resource = createResource(resourceId, ResourceStatus.AVAILABLE);
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));

        LocalDate beyondHorizon = FIXED_DATE.plusDays(31);
        when(scheduleRepository.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, (short) beyondHorizon.getDayOfWeek().getValue()))
                .thenReturn(List.of(createSchedule(resourceId, beyondHorizon.getDayOfWeek(), LocalTime.of(8, 0), LocalTime.of(17, 0))));

        ResourceAvailabilityResponse response = bookingService.getResourceAvailability(resourceId, beyondHorizon, 30);

        assertThat(response.operatingWindows()).hasSize(1);
        assertThat(response.availableSlots()).isEmpty();
        verifyNoInteractions(bookingRepository);
    }

    @Test
    @DisplayName("Filters out past slots when querying today and current time is 09:20")
    void filtersOutPastSlotsForToday() {
        UUID resourceId = UUID.randomUUID();
        Resource resource = createResource(resourceId, ResourceStatus.AVAILABLE);
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));

        // Clock set to 09:20 on FIXED_DATE
        Instant nowAt0920 = FIXED_DATE.atTime(9, 20).atZone(BUSINESS_ZONE).toInstant();
        Clock clockAt0920 = Clock.fixed(nowAt0920, BUSINESS_ZONE);
        BookingService serviceWithTime = new BookingService(
                resourceRepository,
                scheduleRepository,
                bookingRepository,
                bookingStatusHistoryRepository,
                userRepository,
                notificationService,
                clockAt0920,
                BUSINESS_ZONE
        );

        // Schedule: 08:00 - 12:00
        List<ResourceSchedule> schedules = List.of(
                createSchedule(resourceId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))
        );
        when(scheduleRepository.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, (short) 1))
                .thenReturn(schedules);

        when(bookingRepository.findConfirmedSlotsOverlapping(eq(resourceId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        ResourceAvailabilityResponse response = serviceWithTime.getResourceAvailability(resourceId, FIXED_DATE, 30);

        // 08:00-08:30 (past), 08:30-09:00 (past), 09:00-09:30 (start 09:00 < now 09:20 -> past)
        // First valid future slot starts at 09:30!
        assertThat(response.availableSlots()).extracting(s -> s.startTime())
                .containsExactly(
                        instantAt(FIXED_DATE, LocalTime.of(9, 30)),
                        instantAt(FIXED_DATE, LocalTime.of(10, 0)),
                        instantAt(FIXED_DATE, LocalTime.of(10, 30)),
                        instantAt(FIXED_DATE, LocalTime.of(11, 0)),
                        instantAt(FIXED_DATE, LocalTime.of(11, 30))
                );
    }

    @Test
    @DisplayName("Rejects invalid slotMinutes with 400 Bad Request")
    void rejectsInvalidSlotMinutes() {
        UUID resourceId = UUID.randomUUID();

        assertThatThrownBy(() -> bookingService.getResourceAvailability(resourceId, FIXED_DATE, 15))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("Snaps odd-minute operating windows to the 30-minute grid")
    void snapsOddOperatingWindowsToGrid() {
        UUID resourceId = UUID.randomUUID();
        Resource resource = createResource(resourceId, ResourceStatus.AVAILABLE);
        when(resourceRepository.findById(resourceId)).thenReturn(Optional.of(resource));

        // Schedule starts at 08:15 and ends at 10:15
        List<ResourceSchedule> schedules = List.of(
                createSchedule(resourceId, DayOfWeek.MONDAY, LocalTime.of(8, 15), LocalTime.of(10, 15))
        );
        when(scheduleRepository.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, (short) 1))
                .thenReturn(schedules);

        when(bookingRepository.findConfirmedSlotsOverlapping(eq(resourceId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        ResourceAvailabilityResponse response = bookingService.getResourceAvailability(resourceId, FIXED_DATE, 30);

        // Snaps start to 08:30. Slots inside [08:30, 10:15]: 08:30-09:00, 09:00-09:30, 09:30-10:00 (10:00-10:30 exceeds 10:15)
        assertThat(response.availableSlots()).extracting(s -> s.startTime())
                .containsExactly(
                        instantAt(FIXED_DATE, LocalTime.of(8, 30)),
                        instantAt(FIXED_DATE, LocalTime.of(9, 0)),
                        instantAt(FIXED_DATE, LocalTime.of(9, 30))
                );
    }

    // =========================================================================
    // ALC-35: Tests for createBooking
    // =========================================================================

    private User createUser(UUID id, String username, Role role, Department department) {
        return new User(id, username, username + "@example.com", "hash", username, role, department);
    }

    @Test
    @DisplayName("ALC-35: Successfully creates booking and records initial status history")
    void createBooking_success_createsBookingAndHistory() {
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        User user = createUser(userId, "john_doe", Role.EMPLOYEE, null);
        UserPrincipal principal = new UserPrincipal(userId, "john_doe", Role.EMPLOYEE, null);

        Resource resource = createResource(resourceId, ResourceStatus.AVAILABLE);
        resource.setName("Meeting Room A");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(resourceRepository.findByIdForUpdate(resourceId)).thenReturn(Optional.of(resource));

        List<ResourceSchedule> schedules = List.of(
                createSchedule(resourceId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(18, 0))
        );
        when(scheduleRepository.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, (short) 1))
                .thenReturn(schedules);

        Instant start = instantAt(FIXED_DATE, LocalTime.of(9, 0));
        Instant end = instantAt(FIXED_DATE, LocalTime.of(10, 30));
        when(bookingRepository.hasConfirmedConflict(resourceId, end, start)).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingCreateRequest request = new BookingCreateRequest(resourceId, null, start, end, "Team standup");

        BookingResponse response = bookingService.createBooking(request, principal);

        assertThat(response).isNotNull();
        assertThat(response.startTime()).isEqualTo(start);
        assertThat(response.endTime()).isEqualTo(end);
        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.purpose()).isEqualTo("Team standup");

        // Verify history saved
        ArgumentCaptor<BookingStatusHistory> historyCaptor = ArgumentCaptor.forClass(BookingStatusHistory.class);
        verify(bookingStatusHistoryRepository).save(historyCaptor.capture());
        BookingStatusHistory history = historyCaptor.getValue();
        assertThat(history.getOldStatus()).isNull();
        assertThat(history.getNewStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(history.getChangedBy().getId()).isEqualTo(userId);
        assertThat(history.getChangedAt()).isEqualTo(clock.instant());
        assertThat(response.createdAt()).isEqualTo(clock.instant());
        verify(notificationService).confirmBooking(history.getBooking(), clock.instant());
        var ordered = org.mockito.Mockito.inOrder(resourceRepository, scheduleRepository, bookingRepository);
        ordered.verify(resourceRepository).findByIdForUpdate(resourceId);
        ordered.verify(scheduleRepository).findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, (short) 1);
        ordered.verify(bookingRepository).hasConfirmedConflict(resourceId, end, start);
        ordered.verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    @DisplayName("ALC-35: Rejects booking not on the 30-minute grid with 400 Bad Request")
    void createBooking_rejectsNonGridTimes() {
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "emp", Role.EMPLOYEE, null);

        // 09:15 is not on grid
        Instant start = instantAt(FIXED_DATE, LocalTime.of(9, 15));
        Instant end = instantAt(FIXED_DATE, LocalTime.of(10, 0));
        BookingCreateRequest request = new BookingCreateRequest(resourceId, null, start, end, null);

        assertThatThrownBy(() -> bookingService.createBooking(request, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                });
    }

    @Test
    @DisplayName("ALC-35: Rejects booking with duration > 8 hours with 400 Bad Request")
    void createBooking_rejectsExcessiveDuration() {
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "emp", Role.EMPLOYEE, null);

        // 08:00 to 17:00 is 9 hours
        Instant start = instantAt(FIXED_DATE, LocalTime.of(8, 0));
        Instant end = instantAt(FIXED_DATE, LocalTime.of(17, 0));
        BookingCreateRequest request = new BookingCreateRequest(resourceId, null, start, end, null);

        assertThatThrownBy(() -> bookingService.createBooking(request, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                });
    }

    @Test
    @DisplayName("ALC-35: Rejects booking in the past with 400 Bad Request")
    void createBooking_rejectsPastBooking() {
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "emp", Role.EMPLOYEE, null);

        // Clock is at 07:00, booking at 06:00 is in the past
        Instant start = instantAt(FIXED_DATE, LocalTime.of(6, 0));
        Instant end = instantAt(FIXED_DATE, LocalTime.of(6, 30));
        BookingCreateRequest request = new BookingCreateRequest(resourceId, null, start, end, null);

        assertThatThrownBy(() -> bookingService.createBooking(request, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                });
    }

    @Test
    @DisplayName("ALC-35: Rejects booking beyond 30 days horizon with 400 Bad Request")
    void createBooking_rejectsBeyond30Days() {
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "emp", Role.EMPLOYEE, null);

        LocalDate futureDate = FIXED_DATE.plusDays(31);
        Instant start = instantAt(futureDate, LocalTime.of(9, 0));
        Instant end = instantAt(futureDate, LocalTime.of(10, 0));
        BookingCreateRequest request = new BookingCreateRequest(resourceId, null, start, end, null);

        assertThatThrownBy(() -> bookingService.createBooking(request, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                });
    }

    @Test
    @DisplayName("ALC-35: Rejects employee attempting to book on behalf of another user with 403 Forbidden")
    void createBooking_rejectsEmployeeDelegatedBooking() {
        UUID empId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        User empUser = createUser(empId, "emp", Role.EMPLOYEE, null);
        UserPrincipal principal = new UserPrincipal(empId, "emp", Role.EMPLOYEE, null);

        when(userRepository.findById(empId)).thenReturn(Optional.of(empUser));

        Instant start = instantAt(FIXED_DATE, LocalTime.of(9, 0));
        Instant end = instantAt(FIXED_DATE, LocalTime.of(10, 0));
        BookingCreateRequest request = new BookingCreateRequest(resourceId, targetId, start, end, null);

        assertThatThrownBy(() -> bookingService.createBooking(request, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });
    }

    @Test
    @DisplayName("ALC-35: Rejects manager booking for user in a different department with 403 Forbidden")
    void createBooking_rejectsManagerBookingForDifferentDepartment() {
        UUID dept1Id = UUID.randomUUID();
        UUID dept2Id = UUID.randomUUID();
        Department dept1 = new Department(dept1Id, "Engineering");
        Department dept2 = new Department(dept2Id, "Marketing");

        UUID mgrId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        User mgrUser = createUser(mgrId, "manager", Role.MANAGER, dept1);
        User targetUser = createUser(targetId, "other_emp", Role.EMPLOYEE, dept2);
        UserPrincipal principal = new UserPrincipal(mgrId, "manager", Role.MANAGER, dept1Id);

        when(userRepository.findById(mgrId)).thenReturn(Optional.of(mgrUser));
        when(userRepository.findById(targetId)).thenReturn(Optional.of(targetUser));

        Instant start = instantAt(FIXED_DATE, LocalTime.of(9, 0));
        Instant end = instantAt(FIXED_DATE, LocalTime.of(10, 0));
        BookingCreateRequest request = new BookingCreateRequest(resourceId, targetId, start, end, null);

        assertThatThrownBy(() -> bookingService.createBooking(request, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });
    }

    @Test
    @DisplayName("ALC-35: Allows manager to book for employee in the same department")
    void createBooking_allowsManagerBookingForSameDepartment() {
        UUID deptId = UUID.randomUUID();
        Department dept = new Department(deptId, "Engineering");

        UUID mgrId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        User mgrUser = createUser(mgrId, "manager", Role.MANAGER, dept);
        User targetUser = createUser(targetId, "emp", Role.EMPLOYEE, dept);
        UserPrincipal principal = new UserPrincipal(mgrId, "manager", Role.MANAGER, deptId);

        Resource resource = createResource(resourceId, ResourceStatus.AVAILABLE);

        when(userRepository.findById(mgrId)).thenReturn(Optional.of(mgrUser));
        when(userRepository.findById(targetId)).thenReturn(Optional.of(targetUser));
        when(resourceRepository.findByIdForUpdate(resourceId)).thenReturn(Optional.of(resource));

        List<ResourceSchedule> schedules = List.of(
                createSchedule(resourceId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(18, 0))
        );
        when(scheduleRepository.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, (short) 1))
                .thenReturn(schedules);

        Instant start = instantAt(FIXED_DATE, LocalTime.of(9, 0));
        Instant end = instantAt(FIXED_DATE, LocalTime.of(10, 0));
        when(bookingRepository.hasConfirmedConflict(resourceId, end, start)).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingCreateRequest request = new BookingCreateRequest(resourceId, targetId, start, end, null);

        BookingResponse response = bookingService.createBooking(request, principal);

        assertThat(response).isNotNull();
        assertThat(response.user().id()).isEqualTo(targetId);
        assertThat(response.createdBy().id()).isEqualTo(mgrId);
    }

    @Test
    @DisplayName("ALC-35: Rejects booking when resource is SUSPENDED with 400 RESOURCE_NOT_AVAILABLE")
    void createBooking_rejectsSuspendedResource() {
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        User user = createUser(userId, "john", Role.EMPLOYEE, null);
        UserPrincipal principal = new UserPrincipal(userId, "john", Role.EMPLOYEE, null);

        Resource resource = createResource(resourceId, ResourceStatus.SUSPENDED);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(resourceRepository.findByIdForUpdate(resourceId)).thenReturn(Optional.of(resource));

        Instant start = instantAt(FIXED_DATE, LocalTime.of(9, 0));
        Instant end = instantAt(FIXED_DATE, LocalTime.of(10, 0));
        BookingCreateRequest request = new BookingCreateRequest(resourceId, null, start, end, null);

        assertThatThrownBy(() -> bookingService.createBooking(request, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_AVAILABLE);
                });
    }

    @Test
    @DisplayName("Resource department is reporting ownership, not a booking access restriction")
    void createBooking_allowsResourceOwnedByAnotherDepartment() {
        UUID dept1Id = UUID.randomUUID();
        UUID dept2Id = UUID.randomUUID();
        Department dept1 = new Department(dept1Id, "Dept 1");
        Department dept2 = new Department(dept2Id, "Dept 2");

        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        User user = createUser(userId, "john", Role.EMPLOYEE, dept1);
        UserPrincipal principal = new UserPrincipal(userId, "john", Role.EMPLOYEE, dept1Id);

        Resource resource = createResource(resourceId, ResourceStatus.AVAILABLE);
        resource.setDepartment(dept2);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(resourceRepository.findByIdForUpdate(resourceId)).thenReturn(Optional.of(resource));
        when(scheduleRepository.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, (short) 1))
                .thenReturn(List.of(createSchedule(resourceId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(17, 0))));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant start = instantAt(FIXED_DATE, LocalTime.of(9, 0));
        Instant end = instantAt(FIXED_DATE, LocalTime.of(10, 0));
        BookingCreateRequest request = new BookingCreateRequest(resourceId, null, start, end, null);

        assertThat(bookingService.createBooking(request, principal).status()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("ALC-35: Rejects booking outside operating hours with 400 OUTSIDE_OPERATING_HOURS")
    void createBooking_rejectsOutsideOperatingHours() {
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        User user = createUser(userId, "john", Role.EMPLOYEE, null);
        UserPrincipal principal = new UserPrincipal(userId, "john", Role.EMPLOYEE, null);

        Resource resource = createResource(resourceId, ResourceStatus.AVAILABLE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(resourceRepository.findByIdForUpdate(resourceId)).thenReturn(Optional.of(resource));

        // Monday operating hours: 08:00 - 12:00
        List<ResourceSchedule> schedules = List.of(
                createSchedule(resourceId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0))
        );
        when(scheduleRepository.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, (short) 1))
                .thenReturn(schedules);

        // Requested booking is 13:00 - 14:00 (outside operating hours)
        Instant start = instantAt(FIXED_DATE, LocalTime.of(13, 0));
        Instant end = instantAt(FIXED_DATE, LocalTime.of(14, 0));
        BookingCreateRequest request = new BookingCreateRequest(resourceId, null, start, end, null);

        assertThatThrownBy(() -> bookingService.createBooking(request, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.OUTSIDE_OPERATING_HOURS);
                });
    }

    @Test
    @DisplayName("ALC-35: Rejects conflicting booking with 409 BOOKING_TIME_CONFLICT")
    void createBooking_rejectsConflictingTimeSlot() {
        UUID userId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        User user = createUser(userId, "john", Role.EMPLOYEE, null);
        UserPrincipal principal = new UserPrincipal(userId, "john", Role.EMPLOYEE, null);

        Resource resource = createResource(resourceId, ResourceStatus.AVAILABLE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(resourceRepository.findByIdForUpdate(resourceId)).thenReturn(Optional.of(resource));

        List<ResourceSchedule> schedules = List.of(
                createSchedule(resourceId, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(18, 0))
        );
        when(scheduleRepository.findByResourceIdAndDayOfWeekOrderByStartTimeAsc(resourceId, (short) 1))
                .thenReturn(schedules);

        Instant start = instantAt(FIXED_DATE, LocalTime.of(9, 0));
        Instant end = instantAt(FIXED_DATE, LocalTime.of(10, 0));
        // Conflict detected!
        when(bookingRepository.hasConfirmedConflict(resourceId, end, start)).thenReturn(true);

        BookingCreateRequest request = new BookingCreateRequest(resourceId, null, start, end, null);

        assertThatThrownBy(() -> bookingService.createBooking(request, principal))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.BOOKING_TIME_CONFLICT);
                });
    }
}

package com.alloca.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.alloca.analytics.dto.BookingTrendReportResponse;
import com.alloca.analytics.dto.UtilizationReportResponse;
import com.alloca.auth.domain.Department;
import com.alloca.auth.domain.Role;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.booking.domain.Booking;
import com.alloca.booking.domain.BookingStatus;
import com.alloca.booking.repository.BookingRepository;
import com.alloca.common.exception.ApiException;
import com.alloca.common.exception.ErrorCode;
import com.alloca.resource.domain.Resource;
import com.alloca.resource.domain.ResourceSchedule;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.ResourceType;
import com.alloca.resource.domain.Zone;
import com.alloca.resource.repository.ResourceRepository;
import com.alloca.resource.repository.ResourceScheduleRepository;
import java.math.BigDecimal;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private ResourceScheduleRepository resourceScheduleRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private Resource testRoom;
    private Zone testZone;
    private Department testDept;
    private UserPrincipal adminPrincipal;
    private UserPrincipal managerPrincipal;
    private UserPrincipal otherManagerPrincipal;
    private UserPrincipal employeePrincipal;

    @BeforeEach
    void setUp() {
        UUID deptId = UUID.randomUUID();
        testDept = new Department(deptId, "Engineering");
        testZone = new Zone(UUID.randomUUID(), "Main Tower", "3", "Floor 3 West");

        testRoom = new Resource(UUID.randomUUID(), ResourceStatus.AVAILABLE);
        testRoom.setName("Boardroom A");
        testRoom.setType(ResourceType.ROOM);
        testRoom.setCapacity(12);
        testRoom.setZone(testZone);
        testRoom.setDepartment(testDept);
        testRoom.setUtilizationThreshold(new BigDecimal("85.00"));

        adminPrincipal = new UserPrincipal(UUID.randomUUID(), "admin", Role.ADMIN, null);
        managerPrincipal = new UserPrincipal(UUID.randomUUID(), "manager1", Role.MANAGER, deptId);
        otherManagerPrincipal = new UserPrincipal(UUID.randomUUID(), "manager2", Role.MANAGER, UUID.randomUUID());
        employeePrincipal = new UserPrincipal(UUID.randomUUID(), "emp", Role.EMPLOYEE, deptId);
    }

    private Booking createBooking(Resource resource, BookingStatus status, Instant start, Instant end) {
        com.alloca.auth.domain.User user = new com.alloca.auth.domain.User(
                UUID.randomUUID(), "testuser", "test@alloca.test", "hash", "Test User", Role.EMPLOYEE, null);
        Booking b = new Booking(UUID.randomUUID(), resource, user, user, start, end, "Purpose");
        b.setStatus(status);
        return b;
    }

    @Test
    @DisplayName("TC-STAT-01: Operating 9h; CONFIRMED 4h + COMPLETED 4h + CANCELLED 1h -> rate is 88.89%")
    void testTcStat01_ConfirmedAndCompletedIncluded_CancelledExcluded() {
        LocalDate from = LocalDate.of(2026, 10, 5); // Monday
        LocalDate to = LocalDate.of(2026, 10, 6);   // Tuesday (1 day report)

        ResourceSchedule schedule = new ResourceSchedule(
                UUID.randomUUID(), testRoom.getId(), DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0));

        Instant d0900 = from.atTime(9, 0).atZone(ZONE).toInstant();
        Instant d1300 = from.atTime(13, 0).atZone(ZONE).toInstant();
        Instant d1700 = from.atTime(17, 0).atZone(ZONE).toInstant();
        Instant d1800 = from.atTime(18, 0).atZone(ZONE).toInstant();

        Booking b1 = createBooking(testRoom, BookingStatus.CONFIRMED, d0900, d1300);
        Booking b2 = createBooking(testRoom, BookingStatus.COMPLETED, d1300, d1700);
        Booking b3 = createBooking(testRoom, BookingStatus.CANCELLED, d1700, d1800);

        when(resourceRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(testRoom));
        when(resourceScheduleRepository.findByResourceIdIn(List.of(testRoom.getId())))
                .thenReturn(List.of(schedule));
        when(bookingRepository.findBookingsIntersectingPeriod(
                eq(List.of(testRoom.getId())), any(), any(), any()))
                .thenReturn(List.of(b1, b2, b3));

        UtilizationReportResponse report = analyticsService.getUtilizationReport(adminPrincipal, from, to, null, null);

        assertThat(report.metrics()).hasSize(1);
        var metric = report.metrics().get(0);
        assertThat(metric.operatingMinutes()).isEqualTo(540);
        assertThat(metric.scheduledMinutes()).isEqualTo(480);
        // (480 / 540) * 100 = 88.888... -> 88.89%
        assertThat(metric.utilizationPercent()).isEqualTo(88.89);
    }

    @Test
    @DisplayName("TC-STAT-02: Report range clips crossing bookings and strictly respects exclusive 'to' boundary")
    void testTcStat02_ClippedBookingsAndAdjacentBoundaries() {
        LocalDate from = LocalDate.of(2026, 10, 6); // Tuesday
        LocalDate to = LocalDate.of(2026, 10, 7);   // Wednesday

        ResourceSchedule schedule = new ResourceSchedule(
                UUID.randomUUID(), testRoom.getId(), DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(20, 0));

        Instant periodEnd = to.atStartOfDay(ZONE).toInstant();

        // Booking 1: starts before period (Mon 22:00) and ends Tue 02:00 -> clipped to Tue 00:00 - 02:00 (120m)
        Instant mon2200 = from.minusDays(1).atTime(22, 0).atZone(ZONE).toInstant();
        Instant tue0200 = from.atTime(2, 0).atZone(ZONE).toInstant();
        Booking b1 = createBooking(testRoom, BookingStatus.CONFIRMED, mon2200, tue0200);

        // Booking 2: starts Tue 22:00 and ends Wed 02:00 -> clipped to Tue 22:00 - Wed 00:00 (120m)
        Instant tue2200 = from.atTime(22, 0).atZone(ZONE).toInstant();
        Instant wed0200 = to.atTime(2, 0).atZone(ZONE).toInstant();
        Booking b2 = createBooking(testRoom, BookingStatus.CONFIRMED, tue2200, wed0200);

        // Booking 3: strictly at Wed 00:00 (adjacent at exclusive 'to' boundary -> 0 min)
        Instant wed0000 = periodEnd;
        Instant wed0100 = to.atTime(1, 0).atZone(ZONE).toInstant();
        Booking b3 = createBooking(testRoom, BookingStatus.CONFIRMED, wed0000, wed0100);

        when(resourceRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(testRoom));
        when(resourceScheduleRepository.findByResourceIdIn(List.of(testRoom.getId())))
                .thenReturn(List.of(schedule));
        when(bookingRepository.findBookingsIntersectingPeriod(
                eq(List.of(testRoom.getId())), any(), any(), any()))
                .thenReturn(List.of(b1, b2, b3));

        UtilizationReportResponse report = analyticsService.getUtilizationReport(adminPrincipal, from, to, null, null);

        var metric = report.metrics().get(0);
        assertThat(metric.operatingMinutes()).isEqualTo(720);
        // 120m from b1 + 120m from b2 = 240m. b3 contributes 0m.
        assertThat(metric.scheduledMinutes()).isEqualTo(240);
        // (240 / 720) * 100 = 33.33%
        assertThat(metric.utilizationPercent()).isEqualTo(33.33);
    }

    @Test
    @DisplayName("TC-STAT-03: Zero operating minutes returns utilizationPercent = null (N/A)")
    void testTcStat03_ZeroOperatingMinutesReturnsNullUtilization() {
        LocalDate from = LocalDate.of(2026, 10, 11); // Sunday
        LocalDate to = LocalDate.of(2026, 10, 12);   // Monday

        when(resourceRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(testRoom));
        when(resourceScheduleRepository.findByResourceIdIn(List.of(testRoom.getId())))
                .thenReturn(List.of());
        when(bookingRepository.findBookingsIntersectingPeriod(any(), any(), any(), any()))
                .thenReturn(List.of());

        UtilizationReportResponse report = analyticsService.getUtilizationReport(adminPrincipal, from, to, null, null);

        var metric = report.metrics().get(0);
        assertThat(metric.operatingMinutes()).isEqualTo(0);
        assertThat(metric.scheduledMinutes()).isEqualTo(0);
        assertThat(metric.utilizationPercent()).isNull();
    }

    @Test
    @DisplayName("ALC-49 / TC-STAT-06: Booking trends aggregate by local startTime date and status")
    void testAlc49_BookingTrendsAggregation() {
        LocalDate from = LocalDate.of(2026, 10, 5); // Monday
        LocalDate to = LocalDate.of(2026, 10, 8);   // Thursday (3 days: 5, 6, 7)

        // Monday: 1 CONFIRMED, 1 COMPLETED
        Booking b1 = createBooking(testRoom, BookingStatus.CONFIRMED,
                from.atTime(10, 0).atZone(ZONE).toInstant(), from.atTime(11, 0).atZone(ZONE).toInstant());
        Booking b2 = createBooking(testRoom, BookingStatus.COMPLETED,
                from.atTime(14, 0).atZone(ZONE).toInstant(), from.atTime(15, 0).atZone(ZONE).toInstant());

        // Tuesday: 1 CANCELLED
        Booking b3 = createBooking(testRoom, BookingStatus.CANCELLED,
                from.plusDays(1).atTime(9, 0).atZone(ZONE).toInstant(), from.plusDays(1).atTime(10, 0).atZone(ZONE).toInstant());

        // Wednesday: 0 bookings

        when(resourceRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(testRoom));
        when(bookingRepository.findBookingsStartingInPeriod(
                eq(List.of(testRoom.getId())), any(), any()))
                .thenReturn(List.of(b1, b2, b3));

        BookingTrendReportResponse report = analyticsService.getBookingTrends(adminPrincipal, from, to, null, null);

        assertThat(report.from()).isEqualTo(from);
        assertThat(report.to()).isEqualTo(to);
        assertThat(report.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(report.points()).hasSize(3);

        // Day 1 (Monday)
        var p1 = report.points().get(0);
        assertThat(p1.date()).isEqualTo(from);
        assertThat(p1.confirmedCount()).isEqualTo(1);
        assertThat(p1.completedCount()).isEqualTo(1);
        assertThat(p1.cancelledCount()).isEqualTo(0);

        // Day 2 (Tuesday)
        var p2 = report.points().get(1);
        assertThat(p2.date()).isEqualTo(from.plusDays(1));
        assertThat(p2.confirmedCount()).isEqualTo(0);
        assertThat(p2.completedCount()).isEqualTo(0);
        assertThat(p2.cancelledCount()).isEqualTo(1);

        // Day 3 (Wednesday)
        var p3 = report.points().get(2);
        assertThat(p3.date()).isEqualTo(from.plusDays(2));
        assertThat(p3.confirmedCount()).isEqualTo(0);
        assertThat(p3.completedCount()).isEqualTo(0);
        assertThat(p3.cancelledCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("TC-SCOPE-03 / ALC-52: Manager querying other department is rejected with 403 ACCESS_DENIED")
    void testTcScope03_ManagerQueryingOtherDepartment_Returns403() {
        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate to = LocalDate.of(2026, 10, 12);
        UUID differentDeptId = UUID.randomUUID();

        assertThatThrownBy(() -> analyticsService.getUtilizationReport(
                managerPrincipal, from, to, null, differentDeptId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });

        assertThatThrownBy(() -> analyticsService.getBookingTrends(
                managerPrincipal, from, to, null, differentDeptId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });
    }

    @Test
    @DisplayName("TC-SCOPE-03 / ALC-52: Manager querying resource of other department is rejected with 403 ACCESS_DENIED")
    void testTcScope03_ManagerQueryingOtherDepartmentResource_Returns403() {
        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate to = LocalDate.of(2026, 10, 12);

        when(resourceRepository.findWithRelationsById(testRoom.getId()))
                .thenReturn(Optional.of(testRoom));

        // otherManagerPrincipal has a different department ID
        assertThatThrownBy(() -> analyticsService.getUtilizationReport(
                otherManagerPrincipal, from, to, testRoom.getId(), null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });
    }

    @Test
    @DisplayName("TC-SCOPE-03 / ALC-52: Employee attempting to access report is rejected with 403 ACCESS_DENIED")
    void testTcScope03_EmployeeAttemptingReport_Returns403() {
        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate to = LocalDate.of(2026, 10, 12);

        assertThatThrownBy(() -> analyticsService.getUtilizationReport(
                employeePrincipal, from, to, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });

        assertThatThrownBy(() -> analyticsService.getBookingTrends(
                employeePrincipal, from, to, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });
    }

    @Test
    @DisplayName("Validation: 'to' not after 'from' throws ApiException with VALIDATION_ERROR")
    void testValidation_InvalidDateRange() {
        LocalDate from = LocalDate.of(2026, 10, 10);
        LocalDate to = LocalDate.of(2026, 10, 10);

        assertThatThrownBy(() -> analyticsService.getUtilizationReport(adminPrincipal, from, to, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                });
    }
}

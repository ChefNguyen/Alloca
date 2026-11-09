package com.alloca.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alloca.analytics.domain.Alert;
import com.alloca.analytics.domain.AlertStatus;
import com.alloca.analytics.dto.AlertResponse;
import com.alloca.analytics.dto.BookingTrendReportResponse;
import com.alloca.analytics.dto.UtilizationReportResponse;
import com.alloca.analytics.repository.AlertRepository;
import com.alloca.auth.domain.Department;
import com.alloca.auth.domain.Role;
import com.alloca.auth.domain.User;
import com.alloca.auth.repository.UserRepository;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.booking.domain.Booking;
import com.alloca.booking.domain.BookingStatus;
import com.alloca.booking.repository.BookingRepository;
import com.alloca.common.dto.PageResponse;
import com.alloca.common.exception.ApiException;
import com.alloca.common.exception.ErrorCode;
import com.alloca.notification.service.NotificationService;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

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
        User user = new User(
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
                eq(List.of(testRoom.getId())), any(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(b1, b2, b3));

        UtilizationReportResponse report =
                analyticsService.getUtilizationReport(adminPrincipal, from, to, null, null);

        assertThat(report.metrics()).hasSize(1);
        var metric = report.metrics().get(0);
        assertThat(metric.operatingMinutes()).isEqualTo(540);
        assertThat(metric.scheduledMinutes()).isEqualTo(480);
        assertThat(metric.utilizationPercent()).isEqualTo(88.89);
    }

    @Test
    @DisplayName("TC-STAT-02: Report range cuts middle of booking; booking touches from/to boundaries")
    void testTcStat02_ClippedBookings() {
        LocalDate from = LocalDate.of(2026, 10, 5); // Monday 00:00:00
        LocalDate to = LocalDate.of(2026, 10, 6);   // Tuesday 00:00:00

        Instant periodStart = from.atStartOfDay(ZONE).toInstant();
        Instant periodEnd = to.atStartOfDay(ZONE).toInstant();

        Instant b1Start = periodStart.minusSeconds(3600);
        Instant b1End = periodStart.plusSeconds(3600);
        Booking b1 = createBooking(testRoom, BookingStatus.CONFIRMED, b1Start, b1End);

        Instant b2Start = periodEnd.minusSeconds(1800);
        Instant b2End = periodEnd.plusSeconds(1800);
        Booking b2 = createBooking(testRoom, BookingStatus.CONFIRMED, b2Start, b2End);

        int scheduled = analyticsService.calculateScheduledMinutes(List.of(b1, b2), periodStart, periodEnd);
        assertThat(scheduled).isEqualTo(60 + 30);
    }

    @Test
    @DisplayName("TC-STAT-03: No operating windows configured -> rate is null/NA, no division by zero")
    void testTcStat03_NoOperatingWindows_RateIsNull() {
        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate to = LocalDate.of(2026, 10, 6);

        when(resourceRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(testRoom));
        when(resourceScheduleRepository.findByResourceIdIn(List.of(testRoom.getId())))
                .thenReturn(Collections.emptyList());
        when(bookingRepository.findBookingsIntersectingPeriod(
                eq(List.of(testRoom.getId())), any(), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        UtilizationReportResponse report =
                analyticsService.getUtilizationReport(adminPrincipal, from, to, null, null);

        assertThat(report.metrics()).hasSize(1);
        var metric = report.metrics().get(0);
        assertThat(metric.operatingMinutes()).isEqualTo(0);
        assertThat(metric.scheduledMinutes()).isEqualTo(0);
        assertThat(metric.utilizationPercent()).isNull();
    }

    @Test
    @DisplayName("TC-STAT-04: Rolling 7 days evaluation: 85.0% does not alert; 85.19% strictly exceeds and alerts")
    void testTcStat04_Threshold85_Rate85NoAlert_Rate851TriggersAlert() {
        LocalDate evalDate = LocalDate.of(2026, 10, 12);
        LocalDate periodStart = evalDate.minusDays(7); // 2026-10-05
        Instant now = evalDate.atTime(0, 5).atZone(ZONE).toInstant();

        // Operating 9 hours on Monday only = 540 minutes total in the 7-day period
        ResourceSchedule schedule = new ResourceSchedule(
                UUID.randomUUID(), testRoom.getId(), DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0));

        when(resourceRepository.findAll()).thenReturn(List.of(testRoom));
        when(resourceScheduleRepository.findByResourceIdIn(List.of(testRoom.getId()))).thenReturn(List.of(schedule));
        when(alertRepository.findByResourceIdAndAlertTypeAndStatusIn(
                eq(testRoom.getId()), eq("HIGH_UTILIZATION"), any()))
                .thenReturn(Optional.empty());

        // Case A: 459 minutes booked out of 540 = 85.0% -> exactly equal to threshold 85% -> NO ALERT
        Instant mon0900 = periodStart.atTime(9, 0).atZone(ZONE).toInstant();
        Instant mon1639 = periodStart.atTime(16, 39).atZone(ZONE).toInstant(); // 459 min
        Booking bNoAlert = createBooking(testRoom, BookingStatus.CONFIRMED, mon0900, mon1639);

        when(bookingRepository.findBookingsIntersectingPeriod(
                eq(List.of(testRoom.getId())), any(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(bNoAlert));

        analyticsService.evaluateDailyUtilizationAlerts(evalDate, now);

        verify(alertRepository, never()).save(any());
        verify(notificationService, never()).dispatchUtilizationAlert(any(), any(), any(), any(), any());

        // Case B: 460 minutes booked out of 540 = 85.19% -> strictly greater than 85.0% -> TRIGGERS ALERT
        Instant mon1640 = periodStart.atTime(16, 40).atZone(ZONE).toInstant(); // 460 min
        Booking bAlert = createBooking(testRoom, BookingStatus.CONFIRMED, mon0900, mon1640);

        when(bookingRepository.findBookingsIntersectingPeriod(
                eq(List.of(testRoom.getId())), any(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(bAlert));

        User managerUser = new User(
                UUID.randomUUID(), "mgr", "mgr@alloca.test", "hash", "Manager", Role.MANAGER, testDept);
        when(userRepository.findByDepartment_IdAndRoleAndIsActiveTrue(testDept.getId(), Role.MANAGER))
                .thenReturn(List.of(managerUser));

        analyticsService.evaluateDailyUtilizationAlerts(evalDate, now);

        ArgumentCaptor<Alert> alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertRepository).save(alertCaptor.capture());
        Alert saved = alertCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(AlertStatus.OPEN);
        assertThat(saved.getActualPercent()).isEqualTo(new BigDecimal("85.19"));
        assertThat(saved.getBookedMinutes()).isEqualTo(460);
        assertThat(saved.getAvailableMinutes()).isEqualTo(540);

        verify(notificationService).dispatchUtilizationAlert(
                eq(saved.getId()), eq(managerUser.getId()), eq("Boardroom A"), eq(new BigDecimal("85.19")), eq(now));
    }

    @Test
    @DisplayName("TC-STAT-04: Recipient routing: Shared resource sends to Admin; Department resource sends to Manager")
    void testTcStat04_RecipientRouting() {
        LocalDate evalDate = LocalDate.of(2026, 10, 12);
        LocalDate periodStart = evalDate.minusDays(7);
        Instant now = evalDate.atTime(0, 5).atZone(ZONE).toInstant();

        Resource sharedRoom = new Resource(UUID.randomUUID(), ResourceStatus.AVAILABLE);
        sharedRoom.setName("Shared Hall");
        sharedRoom.setType(ResourceType.ROOM);
        sharedRoom.setDepartment(null); // shared

        ResourceSchedule schedule = new ResourceSchedule(
                UUID.randomUUID(), sharedRoom.getId(), DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0));

        when(resourceRepository.findAll()).thenReturn(List.of(sharedRoom));
        when(resourceScheduleRepository.findByResourceIdIn(List.of(sharedRoom.getId()))).thenReturn(List.of(schedule));
        when(alertRepository.findByResourceIdAndAlertTypeAndStatusIn(
                eq(sharedRoom.getId()), eq("HIGH_UTILIZATION"), any()))
                .thenReturn(Optional.empty());

        Instant mon0900 = periodStart.atTime(9, 0).atZone(ZONE).toInstant();
        Instant mon1800 = periodStart.atTime(18, 0).atZone(ZONE).toInstant();
        Booking fullBooking = createBooking(sharedRoom, BookingStatus.CONFIRMED, mon0900, mon1800);

        when(bookingRepository.findBookingsIntersectingPeriod(
                eq(List.of(sharedRoom.getId())), any(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(fullBooking));

        User adminUser = new User(
                UUID.randomUUID(), "adm", "adm@alloca.test", "hash", "Admin User", Role.ADMIN, null);
        when(userRepository.findByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(List.of(adminUser));

        analyticsService.evaluateDailyUtilizationAlerts(evalDate, now);

        verify(notificationService).dispatchUtilizationAlert(
                any(), eq(adminUser.getId()), eq("Shared Hall"), eq(new BigDecimal("100.00")), eq(now));
    }

    @Test
    @DisplayName("TC-STAT-05: Alert lifecycle: active episode deduplication, normalize resolves, unavailable resolves")
    void testTcStat05_EpisodeLifecycle() {
        LocalDate evalDate = LocalDate.of(2026, 10, 12);
        LocalDate periodStart = evalDate.minusDays(7);
        Instant now = evalDate.atTime(0, 5).atZone(ZONE).toInstant();

        ResourceSchedule schedule = new ResourceSchedule(
                UUID.randomUUID(), testRoom.getId(), DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0));

        when(resourceRepository.findAll()).thenReturn(List.of(testRoom));
        when(resourceScheduleRepository.findByResourceIdIn(List.of(testRoom.getId()))).thenReturn(List.of(schedule));

        Alert activeAlert = new Alert(
                UUID.randomUUID(), testRoom, periodStart, evalDate, "DEDUP-1",
                new BigDecimal("85.00"), new BigDecimal("90.00"), 486, 540, now);
        when(alertRepository.findByResourceIdAndAlertTypeAndStatusIn(
                eq(testRoom.getId()), eq("HIGH_UTILIZATION"), any()))
                .thenReturn(Optional.of(activeAlert));

        // Sub-test 1: Still breached -> updates metrics, does NOT send duplicate notification
        Instant mon0900 = periodStart.atTime(9, 0).atZone(ZONE).toInstant();
        Instant mon1715 = periodStart.atTime(17, 15).atZone(ZONE).toInstant(); // 495 min = 91.67%
        Booking bStillBreached = createBooking(testRoom, BookingStatus.CONFIRMED, mon0900, mon1715);
        when(bookingRepository.findBookingsIntersectingPeriod(
                eq(List.of(testRoom.getId())), any(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(bStillBreached));

        analyticsService.evaluateDailyUtilizationAlerts(evalDate, now);

        assertThat(activeAlert.getActualPercent()).isEqualTo(new BigDecimal("91.67"));
        assertThat(activeAlert.getBookedMinutes()).isEqualTo(495);
        verify(notificationService, never()).dispatchUtilizationAlert(any(), any(), any(), any(), any());

        // Sub-test 2: Utilization normalizes (rate <= threshold) -> active alert resolves with RATE_NORMALIZED
        Booking bNormalized = createBooking(testRoom, BookingStatus.CONFIRMED,
                mon0900, periodStart.atTime(12, 0).atZone(ZONE).toInstant()); // 180 min = 33.33%
        when(bookingRepository.findBookingsIntersectingPeriod(
                eq(List.of(testRoom.getId())), any(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(bNormalized));

        analyticsService.evaluateDailyUtilizationAlerts(evalDate, now);

        assertThat(activeAlert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(activeAlert.getResolutionReason()).isEqualTo("RATE_NORMALIZED");

        // Sub-test 3: Resource becomes SUSPENDED -> active alert resolves with RESOURCE_UNAVAILABLE
        testRoom.changeStatus(ResourceStatus.SUSPENDED);
        Alert anotherActiveAlert = new Alert(
                UUID.randomUUID(), testRoom, periodStart, evalDate, "DEDUP-2",
                new BigDecimal("85.00"), new BigDecimal("90.00"), 486, 540, now);
        when(alertRepository.findByResourceIdAndAlertTypeAndStatusIn(
                eq(testRoom.getId()), eq("HIGH_UTILIZATION"), any()))
                .thenReturn(Optional.of(anotherActiveAlert));

        analyticsService.evaluateDailyUtilizationAlerts(evalDate, now);

        assertThat(anotherActiveAlert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(anotherActiveAlert.getResolutionReason()).isEqualTo("RESOURCE_UNAVAILABLE");
    }

    @Test
    @DisplayName("Alert acknowledgment: OPEN alert acknowledged successfully; already acknowledged throws 409")
    void testAcknowledgeAlert_SuccessAndConflict() {
        UUID alertId = UUID.randomUUID();
        Alert alert = new Alert(
                alertId, testRoom, LocalDate.now(ZONE).minusDays(7), LocalDate.now(ZONE),
                "DEDUP-ACK", new BigDecimal("85.00"), new BigDecimal("90.00"), 486, 540, Instant.now());

        when(alertRepository.findWithRelationsById(alertId)).thenReturn(Optional.of(alert));

        User managerUser = new User(
                managerPrincipal.getId(), "mgr", "mgr@alloca.test", "hash", "Manager", Role.MANAGER, testDept);
        when(userRepository.findById(managerPrincipal.getId())).thenReturn(Optional.of(managerUser));

        AlertResponse response = analyticsService.acknowledgeAlert(managerPrincipal, alertId);
        assertThat(response.status()).isEqualTo(AlertStatus.ACKNOWLEDGED);
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.ACKNOWLEDGED);

        // Attempting to acknowledge again throws CONFLICT 409
        assertThatThrownBy(() -> analyticsService.acknowledgeAlert(managerPrincipal, alertId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.ALERT_CANNOT_BE_ACKNOWLEDGED);
                });
    }

    @Test
    @DisplayName("ALC-52: Manager cannot acknowledge alert of another department's resource (403)")
    void testAcknowledgeAlert_ManagerOtherDepartment_Forbidden() {
        UUID alertId = UUID.randomUUID();
        Alert alert = new Alert(
                alertId, testRoom, LocalDate.now(ZONE).minusDays(7), LocalDate.now(ZONE),
                "DEDUP-OTHER", new BigDecimal("85.00"), new BigDecimal("90.00"), 486, 540, Instant.now());

        when(alertRepository.findWithRelationsById(alertId)).thenReturn(Optional.of(alert));

        assertThatThrownBy(() -> analyticsService.acknowledgeAlert(otherManagerPrincipal, alertId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });
    }

    @Test
    @DisplayName("ALC-52: List alerts enforces department scope for managers")
    void testListAlerts_ManagerScopeEnforced() {
        Pageable pageable = PageRequest.of(0, 20);

        // Manager requesting other department ID -> 403
        assertThatThrownBy(() -> analyticsService.listAlerts(
                managerPrincipal, null, null, UUID.randomUUID(), pageable))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });

        // Manager requesting their own department alerts -> 200
        when(alertRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        PageResponse<AlertResponse> res = analyticsService.listAlerts(
                managerPrincipal, AlertStatus.OPEN, null, null, pageable);
        assertThat(res.items()).isEmpty();
    }

    @Test
    @DisplayName("TC-STAT-06: Trend aggregate strictly by startTime local date; excludes CANCELLED from confirmed count")
    void testTcStat06_BookingTrendsAggregation() {
        LocalDate from = LocalDate.of(2026, 10, 5); // Monday
        LocalDate to = LocalDate.of(2026, 10, 8);   // Thursday (3 days)

        Instant d0900 = from.atTime(9, 0).atZone(ZONE).toInstant();
        Instant d1000 = from.atTime(10, 0).atZone(ZONE).toInstant();
        Instant d1100 = from.atTime(11, 0).atZone(ZONE).toInstant();
        Instant d1200 = from.atTime(12, 0).atZone(ZONE).toInstant();

        Booking b1 = createBooking(testRoom, BookingStatus.CONFIRMED, d0900, d1000);
        Booking b2 = createBooking(testRoom, BookingStatus.COMPLETED, d1000, d1100);
        Booking b3 = createBooking(testRoom, BookingStatus.CANCELLED, d1100, d1200);

        when(resourceRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(testRoom));
        when(bookingRepository.findBookingsStartingInPeriod(
                eq(List.of(testRoom.getId())), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(b1, b2, b3));

        BookingTrendReportResponse response =
                analyticsService.getBookingTrends(adminPrincipal, from, to, null, null);

        assertThat(response.points()).hasSize(3);
        var mondayPoint = response.points().get(0);
        assertThat(mondayPoint.date()).isEqualTo(from);
        assertThat(mondayPoint.confirmedCount()).isEqualTo(1);
        assertThat(mondayPoint.completedCount()).isEqualTo(1);
        assertThat(mondayPoint.cancelledCount()).isEqualTo(1);

        var tuesdayPoint = response.points().get(1);
        assertThat(tuesdayPoint.date()).isEqualTo(from.plusDays(1));
        assertThat(tuesdayPoint.confirmedCount()).isZero();
        assertThat(tuesdayPoint.completedCount()).isZero();
        assertThat(tuesdayPoint.cancelledCount()).isZero();
    }

    @Test
    @DisplayName("TC-SCOPE-03 / ALC-52: Manager requesting another department's report gets 403 ACCESS_DENIED")
    void testTcScope03_ManagerRequestingOtherDepartment_Returns403() {
        LocalDate from = LocalDate.of(2026, 10, 5);
        LocalDate to = LocalDate.of(2026, 10, 12);
        UUID otherDeptId = UUID.randomUUID();

        assertThatThrownBy(() -> analyticsService.getUtilizationReport(
                managerPrincipal, from, to, null, otherDeptId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });

        assertThatThrownBy(() -> analyticsService.getBookingTrends(
                managerPrincipal, from, to, null, otherDeptId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiEx = (ApiException) ex;
                    assertThat(apiEx.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiEx.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED);
                });
    }

    @Test
    @DisplayName("TC-SCOPE-03 / ALC-52: Employee attempting to access analytics/reports gets 403 ACCESS_DENIED")
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

package com.alloca.analytics.service;

import com.alloca.analytics.domain.Alert;
import com.alloca.analytics.domain.AlertStatus;
import com.alloca.analytics.dto.AlertResponse;
import com.alloca.analytics.dto.BookingTrendPoint;
import com.alloca.analytics.dto.BookingTrendReportResponse;
import com.alloca.analytics.dto.UtilizationMetric;
import com.alloca.analytics.dto.UtilizationReportResponse;
import com.alloca.analytics.repository.AlertRepository;
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
import com.alloca.resource.domain.OperatingSchedule;
import com.alloca.resource.domain.Resource;
import com.alloca.resource.domain.ResourceSchedule;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.ScheduleWindow;
import com.alloca.resource.dto.ResourceResponse;
import com.alloca.resource.repository.ResourceRepository;
import com.alloca.resource.repository.ResourceScheduleRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for utilization metrics, booking trends, and alert episodes.
 *
 * <p>Implements ALC-48 (utilization calculation formula, scheduled-time clip,
 * operating windows denominator, rate null handling), ALC-49 (booking trends by
 * local start date), ALC-50 (alert evaluation job and lifecycle), and ALC-52 (department scope).
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<BookingStatus> UTILIZATION_BOOKING_STATUSES =
            Set.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);

    private final ResourceRepository resourceRepository;
    private final ResourceScheduleRepository resourceScheduleRepository;
    private final BookingRepository bookingRepository;
    private final AlertRepository alertRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UtilizationReportResponse getUtilizationReport(
            UserPrincipal principal, LocalDate from, LocalDate to, UUID resourceId, UUID departmentId) {
        validateDateRange(from, to);
        validateAndEnforceScope(principal, resourceId, departmentId);

        UUID effectiveDepartmentId = departmentId;
        if (principal != null && principal.isManager()) {
            effectiveDepartmentId = principal.getDepartmentId();
        }

        List<Resource> resources = resolveResources(resourceId, effectiveDepartmentId, principal);
        if (resources.isEmpty()) {
            return new UtilizationReportResponse(from, to, Collections.emptyList());
        }

        List<UUID> resourceIds = resources.stream().map(Resource::getId).toList();
        List<ResourceSchedule> allSchedules = resourceScheduleRepository.findByResourceIdIn(resourceIds);

        Map<UUID, List<ResourceSchedule>> schedulesByResource = new HashMap<>();
        for (ResourceSchedule s : allSchedules) {
            schedulesByResource.computeIfAbsent(s.getResourceId(), k -> new ArrayList<>()).add(s);
        }

        Instant periodStartUtc = from.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant periodEndUtc = to.atStartOfDay(BUSINESS_ZONE).toInstant();

        List<Booking> allBookings = bookingRepository.findBookingsIntersectingPeriod(
                resourceIds, UTILIZATION_BOOKING_STATUSES, periodStartUtc, periodEndUtc);

        Map<UUID, List<Booking>> bookingsByResource = new HashMap<>();
        for (Booking b : allBookings) {
            bookingsByResource.computeIfAbsent(b.getResource().getId(), k -> new ArrayList<>()).add(b);
        }

        List<UtilizationMetric> metrics = new ArrayList<>();
        for (Resource resource : resources) {
            List<ResourceSchedule> schedules =
                    schedulesByResource.getOrDefault(resource.getId(), Collections.emptyList());
            int operatingMinutes = calculateOperatingMinutes(schedules, from, to);

            List<Booking> bookings =
                    bookingsByResource.getOrDefault(resource.getId(), Collections.emptyList());
            int scheduledMinutes = calculateScheduledMinutes(bookings, periodStartUtc, periodEndUtc);

            Double utilizationPercent = null;
            if (operatingMinutes > 0) {
                double rawPercent = (scheduledMinutes * 100.0) / operatingMinutes;
                utilizationPercent = Math.round(rawPercent * 100.0) / 100.0;
            }

            metrics.add(new UtilizationMetric(
                    ResourceResponse.from(resource),
                    scheduledMinutes,
                    operatingMinutes,
                    utilizationPercent
            ));
        }

        return new UtilizationReportResponse(from, to, metrics);
    }

    @Transactional(readOnly = true)
    public BookingTrendReportResponse getBookingTrends(
            UserPrincipal principal, LocalDate from, LocalDate to, UUID resourceId, UUID departmentId) {
        validateDateRange(from, to);
        validateAndEnforceScope(principal, resourceId, departmentId);

        UUID effectiveDepartmentId = departmentId;
        if (principal != null && principal.isManager()) {
            effectiveDepartmentId = principal.getDepartmentId();
        }

        List<Resource> resources = resolveResources(resourceId, effectiveDepartmentId, principal);
        if (resources.isEmpty()) {
            return new BookingTrendReportResponse(from, to, buildEmptyTrendPoints(from, to));
        }

        List<UUID> resourceIds = resources.stream().map(Resource::getId).toList();
        Instant periodStartUtc = from.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant periodEndUtc = to.atStartOfDay(BUSINESS_ZONE).toInstant();

        List<Booking> bookings = bookingRepository.findBookingsStartingInPeriod(
                resourceIds, periodStartUtc, periodEndUtc);

        Map<LocalDate, int[]> countsByDate = new HashMap<>();
        for (Booking b : bookings) {
            LocalDate localDate = b.getStartTime().atZone(BUSINESS_ZONE).toLocalDate();
            int[] counts = countsByDate.computeIfAbsent(localDate, k -> new int[3]);
            if (b.getStatus() == BookingStatus.CONFIRMED) {
                counts[0]++;
            } else if (b.getStatus() == BookingStatus.COMPLETED) {
                counts[1]++;
            } else if (b.getStatus() == BookingStatus.CANCELLED) {
                counts[2]++;
            }
        }

        List<BookingTrendPoint> points = new ArrayList<>();
        LocalDate cur = from;
        while (cur.isBefore(to)) {
            int[] counts = countsByDate.getOrDefault(cur, new int[3]);
            points.add(new BookingTrendPoint(cur, counts[0], counts[1], counts[2]));
            cur = cur.plusDays(1);
        }

        return new BookingTrendReportResponse(from, to, points);
    }

    @Transactional
    public void evaluateDailyUtilizationAlerts(LocalDate evaluationDate, Instant now) {
        LocalDate periodEnd = evaluationDate;
        LocalDate periodStart = evaluationDate.minusDays(7);
        Instant periodStartUtc = periodStart.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant periodEndUtc = periodEnd.atStartOfDay(BUSINESS_ZONE).toInstant();

        List<Resource> allResources = resourceRepository.findAll();
        if (allResources.isEmpty()) {
            return;
        }

        List<UUID> resourceIds = allResources.stream().map(Resource::getId).toList();
        List<ResourceSchedule> allSchedules = resourceScheduleRepository.findByResourceIdIn(resourceIds);
        Map<UUID, List<ResourceSchedule>> schedulesByResource = new HashMap<>();
        for (ResourceSchedule s : allSchedules) {
            schedulesByResource.computeIfAbsent(s.getResourceId(), k -> new ArrayList<>()).add(s);
        }

        List<Booking> allBookings = bookingRepository.findBookingsIntersectingPeriod(
                resourceIds, UTILIZATION_BOOKING_STATUSES, periodStartUtc, periodEndUtc);
        Map<UUID, List<Booking>> bookingsByResource = new HashMap<>();
        for (Booking b : allBookings) {
            bookingsByResource.computeIfAbsent(b.getResource().getId(), k -> new ArrayList<>()).add(b);
        }

        for (Resource resource : allResources) {
            Optional<Alert> activeAlertOpt = alertRepository.findByResourceIdAndAlertTypeAndStatusIn(
                    resource.getId(), "HIGH_UTILIZATION", List.of(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED));

            if (resource.getStatus() != ResourceStatus.AVAILABLE) {
                activeAlertOpt.ifPresent(alert -> alert.resolve("RESOURCE_UNAVAILABLE", now));
                continue;
            }

            List<ResourceSchedule> schedules =
                    schedulesByResource.getOrDefault(resource.getId(), Collections.emptyList());
            int operatingMinutes = calculateOperatingMinutes(schedules, periodStart, periodEnd);

            if (operatingMinutes == 0) {
                // Denominator is 0; rate is null/NA, skip without alerting or resolving
                continue;
            }

            List<Booking> bookings =
                    bookingsByResource.getOrDefault(resource.getId(), Collections.emptyList());
            int scheduledMinutes = calculateScheduledMinutes(bookings, periodStartUtc, periodEndUtc);

            double rawPercent = (scheduledMinutes * 100.0) / operatingMinutes;
            BigDecimal actualPercent =
                    BigDecimal.valueOf(Math.round(rawPercent * 100.0) / 100.0).setScale(2, RoundingMode.HALF_UP);

            BigDecimal threshold = resource.getUtilizationThreshold() != null
                    ? resource.getUtilizationThreshold()
                    : ResourceResponse.DEFAULT_UTILIZATION_THRESHOLD;

            boolean breached = actualPercent.compareTo(threshold) > 0;

            if (breached) {
                if (activeAlertOpt.isPresent()) {
                    Alert alert = activeAlertOpt.get();
                    alert.updateMetrics(actualPercent, scheduledMinutes, operatingMinutes, periodStart, periodEnd);
                } else {
                    String dedupKey = "HIGH_UTILIZATION:" + resource.getId() + ":" + periodStart + ":" + periodEnd;
                    Alert newAlert = new Alert(
                            UuidCreator.getTimeOrderedEpoch(),
                            resource,
                            periodStart,
                            periodEnd,
                            dedupKey,
                            threshold,
                            actualPercent,
                            scheduledMinutes,
                            operatingMinutes,
                            now
                    );
                    alertRepository.save(newAlert);

                    if (resource.getDepartment() != null) {
                        List<User> managers = userRepository.findByDepartment_IdAndRoleAndIsActiveTrue(
                                resource.getDepartment().getId(), Role.MANAGER);
                        for (User m : managers) {
                            notificationService.dispatchUtilizationAlert(
                                    newAlert.getId(),
                                    m.getId(),
                                    resource.getName(),
                                    actualPercent,
                                    now
                            );
                        }
                    } else {
                        List<User> admins = userRepository.findByRoleAndIsActiveTrue(Role.ADMIN);
                        for (User a : admins) {
                            notificationService.dispatchUtilizationAlert(
                                    newAlert.getId(),
                                    a.getId(),
                                    resource.getName(),
                                    actualPercent,
                                    now
                            );
                        }
                    }
                }
            } else {
                if (activeAlertOpt.isPresent()) {
                    activeAlertOpt.get().resolve("RATE_NORMALIZED", now);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertResponse> listAlerts(
            UserPrincipal principal,
            AlertStatus status,
            UUID resourceId,
            UUID departmentId,
            Pageable pageable) {
        validateAndEnforceScope(principal, resourceId, departmentId);

        Specification<Alert> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (resourceId != null) {
                predicates.add(cb.equal(root.get("resource").get("id"), resourceId));
            }
            UUID filterDept = (principal != null && principal.isManager()) ? principal.getDepartmentId() : departmentId;
            if (filterDept != null) {
                predicates.add(cb.equal(root.get("resource").get("department").get("id"), filterDept));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Page<Alert> page = alertRepository.findAll(spec, pageable);
        return PageResponse.of(page, AlertResponse::from);
    }

    @Transactional
    public AlertResponse acknowledgeAlert(UserPrincipal principal, UUID alertId) {
        if (principal == null || principal.isEmployee()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                    "Only managers and admins can acknowledge alerts");
        }

        Alert alert = alertRepository.findWithRelationsById(alertId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.ALERT_NOT_FOUND, "Alert not found: " + alertId));

        if (principal.isManager()) {
            if (principal.getDepartmentId() == null
                    || alert.getResource().getDepartment() == null
                    || !alert.getResource().getDepartment().getId().equals(principal.getDepartmentId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                        "Managers can only acknowledge alerts for their department resources");
            }
        }

        if (alert.getStatus() != AlertStatus.OPEN) {
            throw ApiException.conflict(ErrorCode.ALERT_CANNOT_BE_ACKNOWLEDGED,
                    "Only OPEN alerts can be acknowledged; current status is " + alert.getStatus());
        }

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, "User not found"));

        alert.acknowledge(user);
        return AlertResponse.from(alert);
    }

    public int calculateOperatingMinutes(
            List<ResourceSchedule> schedules, LocalDate from, LocalDate to) {
        if (schedules == null || schedules.isEmpty() || !from.isBefore(to)) {
            return 0;
        }

        List<ScheduleWindow> windows = schedules.stream()
                .map(s -> new ScheduleWindow(DayOfWeek.of(s.getDayOfWeek()), s.getStartTime(), s.getEndTime()))
                .toList();
        OperatingSchedule operatingSchedule = new OperatingSchedule(windows, BUSINESS_ZONE);

        int totalMinutes = 0;
        LocalDate cur = from;
        while (cur.isBefore(to)) {
            DayOfWeek day = cur.getDayOfWeek();
            for (ScheduleWindow window : operatingSchedule.mergedWindows(day)) {
                long minutes = Duration.between(window.start(), window.end()).toMinutes();
                if (minutes > 0) {
                    totalMinutes += (int) minutes;
                }
            }
            cur = cur.plusDays(1);
        }

        return totalMinutes;
    }

    public int calculateScheduledMinutes(
            List<Booking> bookings, Instant periodStartUtc, Instant periodEndUtc) {
        if (bookings == null || bookings.isEmpty()) {
            return 0;
        }

        int totalMinutes = 0;
        for (Booking b : bookings) {
            if (b.getStatus() == BookingStatus.CANCELLED) {
                continue;
            }

            Instant start = b.getStartTime();
            Instant end = b.getEndTime();

            Instant clippedStart = start.isBefore(periodStartUtc) ? periodStartUtc : start;
            Instant clippedEnd = end.isAfter(periodEndUtc) ? periodEndUtc : end;

            if (clippedEnd.isAfter(clippedStart)) {
                long duration = Duration.between(clippedStart, clippedEnd).toMinutes();
                totalMinutes += (int) duration;
            }
        }

        return totalMinutes;
    }

    private List<BookingTrendPoint> buildEmptyTrendPoints(LocalDate from, LocalDate to) {
        List<BookingTrendPoint> points = new ArrayList<>();
        LocalDate cur = from;
        while (cur.isBefore(to)) {
            points.add(new BookingTrendPoint(cur, 0, 0, 0));
            cur = cur.plusDays(1);
        }
        return points;
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null) {
            throw ApiException.invalidField("from", "Parameter 'from' is required");
        }
        if (to == null) {
            throw ApiException.invalidField("to", "Parameter 'to' is required");
        }
        if (!from.isBefore(to)) {
            throw ApiException.invalidField("to", "must be after from");
        }
    }

    private void validateAndEnforceScope(UserPrincipal principal, UUID resourceId, UUID departmentId) {
        if (principal == null) {
            return;
        }

        if (principal.isEmployee()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                    "Employees cannot access analytics or alerts");
        }

        if (principal.isManager()) {
            if (principal.getDepartmentId() == null) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                        "Managers without a department cannot access analytics or alerts");
            }
            if (departmentId != null && !departmentId.equals(principal.getDepartmentId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                        "Managers cannot view analytics or alerts for other departments");
            }
            if (resourceId != null) {
                Resource resource = resourceRepository.findWithRelationsById(resourceId)
                        .filter(r -> !r.isDeleted())
                        .orElseThrow(() -> ApiException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found: " + resourceId));
                if (resource.getDepartment() == null || !resource.getDepartment().getId().equals(principal.getDepartmentId())) {
                    throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                            "Managers cannot view analytics or alerts for resources outside their department");
                }
            }
        }
    }

    private List<Resource> resolveResources(UUID resourceId, UUID departmentId, UserPrincipal principal) {
        if (resourceId != null) {
            Resource resource = resourceRepository.findWithRelationsById(resourceId)
                    .filter(found -> !found.isDeleted())
                    .orElseThrow(() -> ApiException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found: " + resourceId));

            if (principal != null && principal.isManager()) {
                if (resource.getDepartment() == null || !resource.getDepartment().getId().equals(principal.getDepartmentId())) {
                    throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                            "Managers cannot view reports for resources outside their department");
                }
            }

            return List.of(resource);
        }

        Specification<Resource> spec = (root, query, cb) -> {
            root.fetch("zone", JoinType.INNER);
            root.fetch("department", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("status"), ResourceStatus.DELETED));

            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return resourceRepository.findAll(spec);
    }
}

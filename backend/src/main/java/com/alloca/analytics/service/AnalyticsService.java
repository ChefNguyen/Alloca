package com.alloca.analytics.service;

import com.alloca.analytics.dto.UtilizationMetric;
import com.alloca.analytics.dto.UtilizationReportResponse;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.booking.domain.Booking;
import com.alloca.booking.domain.BookingStatus;
import com.alloca.booking.repository.BookingRepository;
import com.alloca.common.exception.ApiException;
import com.alloca.common.exception.ErrorCode;
import com.alloca.resource.domain.OperatingSchedule;
import com.alloca.resource.domain.Resource;
import com.alloca.resource.domain.ResourceSchedule;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.ScheduleWindow;
import com.alloca.resource.dto.ResourceResponse;
import com.alloca.resource.repository.ResourceRepository;
import com.alloca.resource.repository.ResourceScheduleRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
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
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for utilization metrics and alert episodes.
 *
 * <p>Implements ALC-48 (utilization calculation formula, scheduled-time clip,
 * operating windows denominator, rate null handling) and ALC-52 (department scope).
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
                    "Employees cannot access utilization reports");
        }

        if (principal.isManager()) {
            if (principal.getDepartmentId() == null) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                        "Managers without a department cannot access utilization reports");
            }
            if (departmentId != null && !departmentId.equals(principal.getDepartmentId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                        "Managers cannot view reports for other departments");
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

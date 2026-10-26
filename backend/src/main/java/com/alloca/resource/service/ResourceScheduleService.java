package com.alloca.resource.service;

import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.resource.domain.OperatingSchedule;
import com.alloca.resource.domain.Resource;
import com.alloca.resource.domain.ResourceSchedule;
import com.alloca.resource.domain.ScheduleWindow;
import com.alloca.resource.dto.ResourceDetailResponse;
import com.alloca.resource.dto.ResourceScheduleReplaceRequest;
import com.alloca.resource.repository.ResourceRepository;
import com.alloca.resource.repository.ResourceScheduleRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weekly operating hours of a resource (ALC-30, FR-RES-02/06).
 *
 * <p>The schedule is replaced as a whole while the resource row is locked
 * ({@code SELECT ... FOR UPDATE}, BR-RESOURCE-02). A replacement that would put any ongoing or
 * future CONFIRMED booking outside the new hours is rejected with 409 and the old schedule is kept;
 * bookings are never cancelled automatically. See docs/specs/resources-and-schedules.md.
 */
@Service
public class ResourceScheduleService {

    private final ResourceRepository resourceRepository;
    private final ResourceScheduleRepository scheduleRepository;
    private final Clock clock;
    private final ZoneId businessZone;

    public ResourceScheduleService(
            ResourceRepository resourceRepository,
            ResourceScheduleRepository scheduleRepository,
            Clock clock,
            ZoneId businessZone) {
        this.resourceRepository = resourceRepository;
        this.scheduleRepository = scheduleRepository;
        this.clock = clock;
        this.businessZone = businessZone;
    }

    @Transactional(readOnly = true)
    public List<ResourceDetailResponse.Schedule> get(UUID resourceId) {
        resourceRepository.findById(resourceId)
                .filter(resource -> !resource.isDeleted())
                .orElseThrow(() -> resourceNotFound(resourceId));
        return currentSchedules(resourceId);
    }

    @Transactional
    public List<ResourceDetailResponse.Schedule> replace(UUID resourceId, ResourceScheduleReplaceRequest request) {
        Resource resource = resourceRepository.findByIdForUpdate(resourceId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> resourceNotFound(resourceId));

        List<ScheduleWindow> windows = ScheduleInputValidator.toWindows(request);
        OperatingSchedule schedule = new OperatingSchedule(windows, businessZone);

        List<UUID> conflicting = resourceRepository.findActiveConfirmedBookings(resourceId, Instant.now(clock))
                .stream()
                .filter(booking -> !schedule.allows(booking.startTime(), booking.endTime()))
                .map(ResourceRepository.ActiveBooking::id)
                .toList();
        if (!conflicting.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.RESOURCE_SCHEDULE_CONFLICTS_WITH_BOOKINGS,
                    "The new operating hours would leave existing CONFIRMED bookings outside them", null,
                    Map.of("conflictingBookingIds", conflicting));
        }

        scheduleRepository.deleteByResourceId(resource.getId());
        scheduleRepository.saveAll(windows.stream()
                .map(window -> new ResourceSchedule(
                        UuidCreator.getTimeOrderedEpoch(), resource.getId(), window.day(), window.start(), window.end()))
                .toList());
        return currentSchedules(resourceId);
    }

    private List<ResourceDetailResponse.Schedule> currentSchedules(UUID resourceId) {
        return scheduleRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(resourceId).stream()
                .map(ResourceDetailResponse.Schedule::from)
                .toList();
    }

    private static ApiException resourceNotFound(UUID id) {
        return ApiException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found: " + id);
    }
}

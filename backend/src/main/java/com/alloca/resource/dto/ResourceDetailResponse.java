package com.alloca.resource.dto;

import com.alloca.resource.domain.Resource;
import com.alloca.resource.domain.ResourceSchedule;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.ResourceType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** OpenAPI {@code ResourceDetail}: {@code Resource} plus its weekly schedules. */
public record ResourceDetailResponse(
        UUID id,
        String name,
        ResourceType type,
        int capacity,
        ResourceStatus status,
        String description,
        ResourceResponse.ZoneSummary zone,
        ResourceResponse.DepartmentSummary department,
        BigDecimal utilizationThreshold,
        MapPosition mapPosition,
        List<Schedule> schedules) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    public record Schedule(UUID id, DayOfWeek dayOfWeek, String startTime, String endTime) {
        static Schedule from(ResourceSchedule schedule) {
            return new Schedule(
                    schedule.getId(),
                    DayOfWeek.of(schedule.getDayOfWeek()),
                    schedule.getStartTime().format(TIME),
                    schedule.getEndTime().format(TIME));
        }
    }

    public static ResourceDetailResponse from(Resource resource, List<ResourceSchedule> schedules) {
        ResourceResponse base = ResourceResponse.from(resource);
        return new ResourceDetailResponse(
                base.id(),
                base.name(),
                base.type(),
                base.capacity(),
                base.status(),
                base.description(),
                base.zone(),
                base.department(),
                base.utilizationThreshold(),
                base.mapPosition(),
                schedules.stream().map(Schedule::from).toList());
    }
}

package com.alloca.resource.dto;

import com.alloca.auth.domain.Department;
import com.alloca.resource.domain.Resource;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.ResourceType;
import com.alloca.resource.domain.Zone;
import java.math.BigDecimal;
import java.util.UUID;

/** OpenAPI {@code Resource}. */
public record ResourceResponse(
        UUID id,
        String name,
        ResourceType type,
        int capacity,
        ResourceStatus status,
        String description,
        ZoneSummary zone,
        DepartmentSummary department,
        BigDecimal utilizationThreshold,
        MapPosition mapPosition) {

    public static final BigDecimal DEFAULT_UTILIZATION_THRESHOLD = new BigDecimal("85.00");

    public record ZoneSummary(UUID id, String name, String floor, String building) {
        static ZoneSummary from(Zone zone) {
            return zone == null ? null : new ZoneSummary(zone.getId(), zone.getName(), zone.getFloor(), zone.getBuilding());
        }
    }

    public record DepartmentSummary(UUID id, String name) {
        static DepartmentSummary from(Department department) {
            return department == null ? null : new DepartmentSummary(department.getId(), department.getName());
        }
    }

    public static ResourceResponse from(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getName(),
                resource.getType(),
                resource.getCapacity(),
                resource.getStatus(),
                resource.getDescription(),
                ZoneSummary.from(resource.getZone()),
                DepartmentSummary.from(resource.getDepartment()),
                resource.getUtilizationThreshold() != null
                        ? resource.getUtilizationThreshold()
                        : DEFAULT_UTILIZATION_THRESHOLD,
                resource.hasMapPosition()
                        ? new MapPosition(resource.getMapX(), resource.getMapY(), resource.getMapWidth(), resource.getMapHeight())
                        : null);
    }
}

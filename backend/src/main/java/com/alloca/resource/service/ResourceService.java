package com.alloca.resource.service;

import com.alloca.auth.domain.Department;
import com.alloca.auth.repository.DepartmentRepository;
import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.common.api.PageResponse;
import com.alloca.resource.domain.Resource;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.ResourceType;
import com.alloca.resource.domain.Zone;
import com.alloca.resource.dto.MapPosition;
import com.alloca.resource.dto.ResourceCreateRequest;
import com.alloca.resource.dto.ResourceDetailResponse;
import com.alloca.resource.dto.ResourceResponse;
import com.alloca.resource.dto.ResourceStatusUpdateRequest;
import com.alloca.resource.dto.ResourceUpdateRequest;
import com.alloca.resource.repository.ResourceRepository;
import com.alloca.resource.repository.ResourceScheduleRepository;
import com.alloca.resource.repository.ZoneRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resource lifecycle: create, read, partial update, suspend/reactivate and soft delete.
 *
 * <p>Every mutation locks the resource row ({@code SELECT ... FOR UPDATE}, BR-RESOURCE-02) so the
 * booking check behind a delete cannot race with a concurrent booking. Operating schedule
 * replacement (ALC-33) is not handled here yet. See docs/specs/resources-and-schedules.md.
 */
@Service
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ResourceScheduleRepository scheduleRepository;
    private final ZoneRepository zoneRepository;
    private final DepartmentRepository departmentRepository;
    private final Clock clock;

    public ResourceService(
            ResourceRepository resourceRepository,
            ResourceScheduleRepository scheduleRepository,
            ZoneRepository zoneRepository,
            DepartmentRepository departmentRepository,
            Clock clock) {
        this.resourceRepository = resourceRepository;
        this.scheduleRepository = scheduleRepository;
        this.zoneRepository = zoneRepository;
        this.departmentRepository = departmentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ResourceResponse> list(
            String q, ResourceType type, UUID zoneId, UUID departmentId, ResourceStatus status,
            Integer minCapacity, Pageable pageable) {
        Specification<Resource> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("status"), ResourceStatus.DELETED));
            if (q != null && !q.isBlank()) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase()) + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern, '\\'));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (zoneId != null) {
                predicates.add(cb.equal(root.get("zone").get("id"), zoneId));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (minCapacity != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("capacity"), minCapacity));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.of(resourceRepository.findAll(spec, pageable), ResourceResponse::from);
    }

    @Transactional(readOnly = true)
    public ResourceDetailResponse get(UUID id) {
        Resource resource = resourceRepository.findWithRelationsById(id)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> resourceNotFound(id));
        return detail(resource);
    }

    @Transactional
    public ResourceResponse create(ResourceCreateRequest request) {
        ResourceStatus status = request.status() == null ? ResourceStatus.AVAILABLE : request.status();
        if (status == ResourceStatus.DELETED) {
            throw ApiException.invalidField("status", "A resource cannot be created as DELETED");
        }
        Zone zone = findZone(request.zoneId());
        Department department = request.departmentId() == null ? null : findDepartment(request.departmentId());

        Resource resource = new Resource(UuidCreator.getTimeOrderedEpoch(), status);
        resource.setName(request.name().trim());
        resource.setType(request.type());
        resource.setCapacity(request.capacity());
        resource.setDescription(request.description());
        resource.setZone(zone);
        resource.setDepartment(department);
        resource.setUtilizationThreshold(request.utilizationThreshold());
        return ResourceResponse.from(resourceRepository.save(resource));
    }

    @Transactional
    public ResourceDetailResponse update(UUID id, ResourceUpdateRequest request) {
        if (request.isEmpty()) {
            throw ApiException.invalidField("body", "At least one field must be provided");
        }
        Resource resource = lockActive(id);

        if (request.isPresent("name")) {
            String name = request.getName() == null ? "" : request.getName().trim();
            if (name.isEmpty()) {
                throw ApiException.invalidField("name", "must not be blank");
            }
            resource.setName(name);
        }
        if (request.isPresent("type")) {
            resource.setType(requireNonNull("type", request.getType()));
        }
        if (request.isPresent("capacity")) {
            resource.setCapacity(requireNonNull("capacity", request.getCapacity()));
        }
        if (request.isPresent("description")) {
            resource.setDescription(request.getDescription());
        }
        if (request.isPresent("zoneId")) {
            resource.setZone(findZone(requireNonNull("zoneId", request.getZoneId())));
        }
        if (request.isPresent("departmentId")) {
            resource.setDepartment(
                    request.getDepartmentId() == null ? null : findDepartment(request.getDepartmentId()));
        }
        if (request.isPresent("utilizationThreshold")) {
            resource.setUtilizationThreshold(request.getUtilizationThreshold());
        }
        if (request.isPresent("mapPosition")) {
            MapPosition position = request.getMapPosition();
            if (position == null) {
                resource.clearMapPosition();
            } else {
                resource.placeOnMap(position.x(), position.y(), position.width(), position.height());
            }
        }
        // Also re-checks an existing position when only the zone changed.
        validateMapPosition(resource);
        return detail(resourceRepository.save(resource));
    }

    @Transactional
    public ResourceResponse updateStatus(UUID id, ResourceStatusUpdateRequest request) {
        Resource resource = lockActive(id);
        if (request.status() == ResourceStatus.DELETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_RESOURCE_STATUS_TRANSITION,
                    "Use DELETE to remove a resource");
        }
        resource.changeStatus(request.status());
        return ResourceResponse.from(resourceRepository.save(resource));
    }

    @Transactional
    public void delete(UUID id) {
        Resource resource = lockActive(id);
        if (resource.getStatus() != ResourceStatus.SUSPENDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_RESOURCE_STATUS_TRANSITION,
                    "Must be SUSPENDED before deletion");
        }
        Instant now = Instant.now(clock);
        List<UUID> blocking = resourceRepository.findActiveConfirmedBookingIds(id, now);
        if (!blocking.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.RESOURCE_HAS_ONGOING_OR_FUTURE_BOOKINGS,
                    "Resource still has ongoing or future CONFIRMED bookings", null,
                    Map.of("conflictingBookingIds", blocking));
        }
        resource.markDeleted(now);
        resourceRepository.save(resource);
    }

    private Resource lockActive(UUID id) {
        return resourceRepository.findByIdForUpdate(id)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> resourceNotFound(id));
    }

    private ResourceDetailResponse detail(Resource resource) {
        return ResourceDetailResponse.from(
                resource, scheduleRepository.findByResourceIdOrderByDayOfWeekAscStartTimeAsc(resource.getId()));
    }

    private Zone findZone(UUID zoneId) {
        return zoneRepository.findById(zoneId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.ZONE_NOT_FOUND, "Zone not found: " + zoneId));
    }

    private Department findDepartment(UUID departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> ApiException.notFound(
                        ErrorCode.DEPARTMENT_NOT_FOUND, "Department not found: " + departmentId));
    }

    private static void validateMapPosition(Resource resource) {
        if (!resource.hasMapPosition()) {
            return;
        }
        Zone zone = resource.getZone();
        if (!zone.hasFloorMap()) {
            throw ApiException.invalidField("mapPosition", "The zone has no floor map");
        }
        boolean inside = resource.getMapX() + resource.getMapWidth() <= zone.getFloorMapWidth()
                && resource.getMapY() + resource.getMapHeight() <= zone.getFloorMapHeight();
        if (!inside) {
            throw ApiException.invalidField("mapPosition", "Must be inside the zone floor map");
        }
    }

    private static <T> T requireNonNull(String field, T value) {
        if (value == null) {
            throw ApiException.invalidField(field, "must not be null");
        }
        return value;
    }

    private static ApiException resourceNotFound(UUID id) {
        return ApiException.notFound(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found: " + id);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

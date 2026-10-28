package com.alloca.resource.service;

import com.alloca.common.api.ApiException;
import com.alloca.common.api.ErrorCode;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.Zone;
import com.alloca.resource.dto.ZoneResponse;
import com.alloca.resource.dto.ZoneWriteRequest;
import com.alloca.resource.repository.ResourceRepository;
import com.alloca.resource.repository.ZoneRepository;
import com.github.f4b6a3.uuid.UuidCreator;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Zones (building/floor areas) that resources are assigned to.
 *
 * <p>Deleting a zone is a soft delete: soft-deleted resources keep pointing at it for booking
 * history. It is blocked while a non-deleted resource is still in the zone; the zone row is locked
 * so a resource created concurrently cannot slip in. A deleted zone behaves as not found.
 * Floor map upload lives in the floormap module.
 */
@Service
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final ResourceRepository resourceRepository;
    private final Clock clock;

    public ZoneService(ZoneRepository zoneRepository, ResourceRepository resourceRepository, Clock clock) {
        this.zoneRepository = zoneRepository;
        this.resourceRepository = resourceRepository;
        this.clock = clock;
    }

    /** Zones are few, so the optional building/floor filters are applied in memory. */
    @Transactional(readOnly = true)
    public List<ZoneResponse> list(String building, String floor) {
        return zoneRepository.findByDeletedAtIsNull(Sort.by("building", "floor", "name")).stream()
                .filter(zone -> building == null || building.equals(zone.getBuilding()))
                .filter(zone -> floor == null || floor.equals(zone.getFloor()))
                .map(ZoneResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ZoneResponse get(UUID id) {
        return ZoneResponse.from(zoneRepository.findById(id)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> zoneNotFound(id)));
    }

    @Transactional
    public ZoneResponse create(ZoneWriteRequest request) {
        Zone zone = new Zone(UuidCreator.getTimeOrderedEpoch(),
                request.name().trim(), request.floor().trim(), request.building().trim());
        return ZoneResponse.from(zoneRepository.save(zone));
    }

    @Transactional
    public ZoneResponse update(UUID id, ZoneWriteRequest request) {
        Zone zone = lockActive(id);
        zone.rename(request.name().trim(), request.floor().trim(), request.building().trim());
        return ZoneResponse.from(zoneRepository.save(zone));
    }

    @Transactional
    public void delete(UUID id) {
        Zone zone = lockActive(id);
        long resourceCount = resourceRepository.countByZone_IdAndStatusNot(id, ResourceStatus.DELETED);
        if (resourceCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ZONE_IN_USE,
                    "Zone still has " + resourceCount + " resource(s); move or delete them first",
                    null, Map.of("resourceCount", resourceCount));
        }
        zone.markDeleted(Instant.now(clock));
        zoneRepository.save(zone);
    }

    private Zone lockActive(UUID id) {
        return zoneRepository.findByIdForUpdate(id)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> zoneNotFound(id));
    }

    private static ApiException zoneNotFound(UUID id) {
        return ApiException.notFound(ErrorCode.ZONE_NOT_FOUND, "Zone not found: " + id);
    }
}

package com.alloca.resource.service;

import com.alloca.resource.domain.Zone;
import com.alloca.resource.dto.ZoneResponse;
import com.alloca.resource.repository.ZoneRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only zone lookup used by resource forms and filters. */
@Service
public class ZoneService {

    private final ZoneRepository zoneRepository;

    public ZoneService(ZoneRepository zoneRepository) {
        this.zoneRepository = zoneRepository;
    }

    /** Zones are few, so the optional building/floor filters are applied in memory. */
    @Transactional(readOnly = true)
    public List<ZoneResponse> list(String building, String floor) {
        return zoneRepository.findAll(Sort.by("building", "floor", "name")).stream()
                .filter(zone -> building == null || building.equals(zone.getBuilding()))
                .filter(zone -> floor == null || floor.equals(zone.getFloor()))
                .map(ZoneResponse::from)
                .toList();
    }
}

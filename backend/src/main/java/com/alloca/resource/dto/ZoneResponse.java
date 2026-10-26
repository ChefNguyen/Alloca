package com.alloca.resource.dto;

import com.alloca.resource.domain.Zone;
import java.util.UUID;

/** OpenAPI {@code Zone}; the floor map size is included so the UI can scale resource markers. */
public record ZoneResponse(
        UUID id,
        String name,
        String floor,
        String building,
        String floorMapUrl,
        Integer floorMapWidth,
        Integer floorMapHeight) {

    public static ZoneResponse from(Zone zone) {
        return new ZoneResponse(
                zone.getId(),
                zone.getName(),
                zone.getFloor(),
                zone.getBuilding(),
                zone.getFloorMapUrl(),
                zone.getFloorMapWidth(),
                zone.getFloorMapHeight());
    }
}

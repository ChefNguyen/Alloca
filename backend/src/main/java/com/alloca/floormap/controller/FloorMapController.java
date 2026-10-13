package com.alloca.floormap.controller;

import com.alloca.floormap.service.FloorMapService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for floor-map upload, marker positioning and map availability.
 *
 * <p>TODO (ALC-54 to ALC-57): implement this module in Sprint 4 after resource
 * and booking flows are stable.
 */
@RestController
@RequestMapping("/api/v1/zones")
public class FloorMapController {

    private final FloorMapService floorMapService;

    public FloorMapController(FloorMapService floorMapService) {
        this.floorMapService = floorMapService;
    }
}

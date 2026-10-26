package com.alloca.resource.controller;

import com.alloca.resource.dto.ZoneResponse;
import com.alloca.resource.service.ZoneService;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Zone read endpoint. TODO (ALC-31): create/update/delete zones and floor map upload. */
@RestController
@RequestMapping("/api/v1/zones")
public class ZoneController {

    private final ZoneService zoneService;

    public ZoneController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    @GetMapping
    public List<ZoneResponse> list(
            @RequestParam(required = false) @Size(max = 100) String building,
            @RequestParam(required = false) @Size(max = 50) String floor) {
        return zoneService.list(building, floor);
    }
}

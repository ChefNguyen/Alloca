package com.alloca.resource.controller;

import com.alloca.resource.dto.ZoneResponse;
import com.alloca.resource.dto.ZoneWriteRequest;
import com.alloca.resource.service.ZoneService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Zone endpoints; reads are open to any authenticated user, writes are ADMIN-only. Floor maps: FloorMapController. */
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

    @GetMapping("/{id}")
    public ZoneResponse get(@PathVariable UUID id) {
        return zoneService.get(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ZoneResponse> create(@Valid @RequestBody ZoneWriteRequest request) {
        ZoneResponse created = zoneService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/zones/" + created.id())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ZoneResponse update(@PathVariable UUID id, @Valid @RequestBody ZoneWriteRequest request) {
        return zoneService.update(id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        zoneService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

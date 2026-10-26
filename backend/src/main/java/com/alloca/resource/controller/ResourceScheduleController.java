package com.alloca.resource.controller;

import com.alloca.resource.dto.ResourceDetailResponse;
import com.alloca.resource.dto.ResourceScheduleReplaceRequest;
import com.alloca.resource.service.ResourceScheduleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operating hours of a resource: readable by any authenticated user, replaceable by ADMIN only. */
@RestController
@RequestMapping("/api/v1/resources/{id}/schedules")
public class ResourceScheduleController {

    private final ResourceScheduleService scheduleService;

    public ResourceScheduleController(ResourceScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public List<ResourceDetailResponse.Schedule> get(@PathVariable UUID id) {
        return scheduleService.get(id);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ResourceDetailResponse.Schedule> replace(
            @PathVariable UUID id, @Valid @RequestBody ResourceScheduleReplaceRequest request) {
        return scheduleService.replace(id, request);
    }
}

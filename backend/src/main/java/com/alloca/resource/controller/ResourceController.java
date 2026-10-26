package com.alloca.resource.controller;

import com.alloca.common.api.ApiException;
import com.alloca.common.api.PageResponse;
import com.alloca.resource.domain.ResourceStatus;
import com.alloca.resource.domain.ResourceType;
import com.alloca.resource.dto.ResourceCreateRequest;
import com.alloca.resource.dto.ResourceDetailResponse;
import com.alloca.resource.dto.ResourceResponse;
import com.alloca.resource.dto.ResourceStatusUpdateRequest;
import com.alloca.resource.dto.ResourceUpdateRequest;
import com.alloca.resource.service.ResourceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

/**
 * HTTP boundary for resources.
 *
 * <p>Reads are open to any authenticated user; every mutation is ADMIN-only (OpenAPI {@code x-roles}).
 * TODO (ALC-33): {@code PUT /{id}/schedules}.
 */
@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    /** Whitelist so clients cannot sort by arbitrary or sensitive entity properties. */
    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "type", "capacity", "status");
    private static final Sort DEFAULT_SORT = Sort.by("name").ascending();

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    public PageResponse<ResourceResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) ResourceType type,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) ResourceStatus status,
            @RequestParam(required = false) @Min(1) Integer minCapacity) {
        return resourceService.list(q, type, zoneId, departmentId, status, minCapacity,
                PageRequest.of(page, size, parseSort(sort)));
    }

    @GetMapping("/{id}")
    public ResourceDetailResponse get(@PathVariable UUID id) {
        return resourceService.get(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ResourceResponse> create(@Valid @RequestBody ResourceCreateRequest request) {
        ResourceResponse created = resourceService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/resources/" + created.id())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResourceDetailResponse update(@PathVariable UUID id, @Valid @RequestBody ResourceUpdateRequest request) {
        return resourceService.update(id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/status")
    public ResourceResponse updateStatus(
            @PathVariable UUID id, @Valid @RequestBody ResourceStatusUpdateRequest request) {
        return resourceService.updateStatus(id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        resourceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_SORT;
        }
        String[] parts = sort.split(",", -1);
        String field = parts[0].trim();
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase() : "asc";
        boolean validDirection = direction.equals("asc") || direction.equals("desc");
        if (parts.length > 2 || !SORTABLE_FIELDS.contains(field) || !validDirection) {
            throw ApiException.invalidField("sort", "Use <field>,asc|desc with field in " + SORTABLE_FIELDS);
        }
        return Sort.by(direction.equals("desc") ? Sort.Order.desc(field) : Sort.Order.asc(field));
    }
}

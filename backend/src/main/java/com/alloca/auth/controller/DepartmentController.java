package com.alloca.auth.controller;

import com.alloca.auth.api.dto.DepartmentResponse;
import com.alloca.auth.api.dto.DepartmentWriteRequest;
import com.alloca.auth.service.DepartmentService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

/** Department endpoints; reads are open to any authenticated user, writes are ADMIN-only. */
@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    public List<DepartmentResponse> list() {
        return departmentService.list();
    }

    @GetMapping("/{id}")
    public DepartmentResponse get(@PathVariable UUID id) {
        return departmentService.get(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<DepartmentResponse> create(@Valid @RequestBody DepartmentWriteRequest request) {
        DepartmentResponse created = departmentService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/departments/" + created.id())).body(created);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public DepartmentResponse update(@PathVariable UUID id, @Valid @RequestBody DepartmentWriteRequest request) {
        return departmentService.update(id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        departmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

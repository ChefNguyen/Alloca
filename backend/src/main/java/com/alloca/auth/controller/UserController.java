package com.alloca.auth.controller;

import com.alloca.auth.api.dto.UserAccessUpdateRequest;
import com.alloca.auth.api.dto.UserCreateRequest;
import com.alloca.auth.api.dto.UserResponse;
import com.alloca.auth.api.dto.UserUpdateRequest;
import com.alloca.auth.domain.Role;
import com.alloca.auth.security.UserPrincipal;
import com.alloca.auth.service.UserService;
import com.alloca.common.api.ApiException;
import com.alloca.common.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

/**
 * HTTP boundary for account administration and access assignment (ALC-26, ALC-27).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("username", "fullname", "email", "role", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by("createdAt").descending();

    private final UserService userService;

    /**
     * ALC-26: Tạo tài khoản mới (Admin only).
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserResponse response = userService.createUser(request);
        URI location = URI.create("/api/v1/users/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * ALC-26: Liệt kê người dùng có phân trang và tìm kiếm (Admin & Manager).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public PageResponse<UserResponse> listUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) Boolean isActive
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, parseSort(sort));
        return userService.listUsers(principal, departmentId, role, isActive, q, pageRequest);
    }

    /**
     * ALC-26: Xem chi tiết tài khoản người dùng (Admin hoặc Manager cùng phòng ban).
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public UserResponse getUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId
    ) {
        return userService.getUser(principal, userId);
    }

    /**
     * ALC-26: Cập nhật tài khoản người dùng (Admin only).
     */
    @PatchMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return userService.updateUser(userId, request);
    }

    /**
     * ALC-26: Vô hiệu hóa tài khoản (Admin only).
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivateUser(@PathVariable UUID userId) {
        userService.deactivateUser(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * ALC-27: Gán role và phòng ban kèm ghi audit log (Admin only).
     */
    @PatchMapping("/{userId}/access")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateUserAccess(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID userId,
            @Valid @RequestBody UserAccessUpdateRequest request
    ) {
        return userService.updateUserAccess(principal.getId(), userId, request);
    }

    /**
     * Helper parse tham số sắp xếp an toàn, chống SQL Injection.
     */
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

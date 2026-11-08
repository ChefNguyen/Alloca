package com.alloca.booking.controller;

import com.alloca.auth.security.UserPrincipal;
import com.alloca.booking.domain.BookingStatus;
import com.alloca.booking.dto.*;
import com.alloca.booking.service.BookingQueryService;
import com.alloca.common.dto.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingQueryController {
    private final BookingQueryService service;

    public BookingQueryController(BookingQueryService service) { this.service = service; }

    @GetMapping
    public PageResponse<BookingResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startTime,desc") String sort,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) UUID resourceId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) BookingStatus status) {
        return service.list(principal, new BookingSearchFilter(from, to, resourceId, userId, departmentId, status), page, size, sort);
    }

    @GetMapping("/{bookingId}")
    public BookingDetailResponse get(@PathVariable UUID bookingId, @AuthenticationPrincipal UserPrincipal principal) {
        return service.get(bookingId, principal);
    }

    @GetMapping("/{bookingId}/history")
    public List<BookingStatusHistoryResponse> history(@PathVariable UUID bookingId, @AuthenticationPrincipal UserPrincipal principal) {
        return service.history(bookingId, principal);
    }
}

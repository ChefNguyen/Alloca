package com.alloca.booking.controller;

import com.alloca.auth.security.UserPrincipal;
import com.alloca.booking.dto.BookingCreateRequest;
import com.alloca.booking.dto.BookingCancelRequest;
import com.alloca.booking.dto.BookingResponse;
import com.alloca.booking.dto.ResourceAvailabilityResponse;
import com.alloca.booking.service.BookingService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for availability, booking creation, cancellation and history.
 */
@RestController
@RequestMapping("/api/v1")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * ALC-34: Lấy slot trống của tài nguyên theo ngày.
     * Path: GET /api/v1/resources/{resourceId}/availability?date=YYYY-MM-DD
     */
    @GetMapping("/resources/{resourceId}/availability")
    public ResourceAvailabilityResponse getResourceAvailability(
            @PathVariable UUID resourceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "30") Integer slotMinutes
    ) {
        return bookingService.getResourceAvailability(resourceId, date, slotMinutes);
    }

    /**
     * ALC-35: Tạo booking với kiểm tra xung đột thời gian.
     * Path: POST /api/v1/bookings
     */
    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingCreateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        BookingResponse response = bookingService.createBooking(request, principal);
        URI location = URI.create("/api/v1/bookings/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    /**
     * ALC-38, ALC-39, ALC-40: Hủy booking trước giờ bắt đầu.
     * Áp dụng phân quyền: Owner (ALC-38), Manager cùng phòng ban (ALC-39), Admin (ALC-40).
     * Path: POST /api/v1/bookings/{bookingId}/cancel
     */
    @PostMapping("/bookings/{bookingId}/cancel")
    public ResponseEntity<Void> cancelBooking(
            @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) BookingCancelRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        bookingService.cancelBooking(bookingId, request, principal);
        return ResponseEntity.noContent().build();
    }
}

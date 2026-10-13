package com.alloca.booking.controller;

import com.alloca.booking.service.BookingService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP boundary for availability, booking creation, cancellation and history.
 *
 * <p>TODO (ALC-34 to ALC-41): expose endpoints incrementally, matching the
 * OpenAPI contract and avoiding placeholder success responses.
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }
}

package com.alloca.booking.service;

import org.springframework.stereotype.Service;

/**
 * Application service for booking workflows.
 *
 * <p>TODO (ALC-34 to ALC-41): validate Asia/Ho_Chi_Minh booking boundaries,
 * acquire a PostgreSQL lock on the resource, check overlap inside one transaction,
 * write status history and add concurrent integration tests.
 * See docs/specs/booking-and-time.md.
 */
@Service
public class BookingService {
}

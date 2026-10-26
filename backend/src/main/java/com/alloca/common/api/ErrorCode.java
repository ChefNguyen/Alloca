package com.alloca.common.api;

/** Machine-readable error codes from the OpenAPI {@code ErrorResponse.code} enum. */
public enum ErrorCode {
    VALIDATION_ERROR,
    ACCESS_DENIED,
    DEPARTMENT_NOT_FOUND,
    ZONE_NOT_FOUND,
    RESOURCE_NOT_FOUND,
    RESOURCE_HAS_ONGOING_OR_FUTURE_BOOKINGS,
    RESOURCE_SCHEDULE_CONFLICTS_WITH_BOOKINGS,
    INVALID_RESOURCE_STATUS_TRANSITION,
    INTERNAL_SERVER_ERROR
}

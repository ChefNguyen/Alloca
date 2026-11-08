package com.alloca.booking.dto;

import java.time.Instant;

public record TimeResponse( 
    Instant startTime, 
    Instant endTime) {
    
}

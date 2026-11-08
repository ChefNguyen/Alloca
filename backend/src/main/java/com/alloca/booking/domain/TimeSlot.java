package com.alloca.booking.domain;

import java.time.Instant;

// Value object 
public record TimeSlot(Instant startTime, Instant endTime) {
    public TimeSlot{
        if (startTime == null || endTime == null){
            throw new IllegalArgumentException("Start and end are required");
        }

        if (!startTime.isBefore(endTime)){
            throw new IllegalArgumentException("End must be after start");
        }
    }

    public boolean overlaps(TimeSlot other){
        return startTime.isBefore(other.endTime()) && endTime.isAfter(other.startTime());
    }
}

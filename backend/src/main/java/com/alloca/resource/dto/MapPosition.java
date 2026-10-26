package com.alloca.resource.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Pixel rectangle on the zone's original floor map image. */
public record MapPosition(
        @NotNull @Min(0) Integer x,
        @NotNull @Min(0) Integer y,
        @NotNull @Min(1) Integer width,
        @NotNull @Min(1) Integer height) {
}

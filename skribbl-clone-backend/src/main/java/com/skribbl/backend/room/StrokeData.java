package com.skribbl.backend.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StrokeData(
    @NotNull Double x1,
    @NotNull Double y1,
    @NotNull Double x2,
    @NotNull Double y2,
    @NotBlank String color,
    @NotNull Integer size
) {
}

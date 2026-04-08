package com.skribbl.backend.room;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DrawActionRequest(
    @NotBlank String roomId,
    @NotBlank String playerId,
    @Valid @NotNull StrokeData stroke
) {
}

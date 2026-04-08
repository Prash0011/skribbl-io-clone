package com.skribbl.backend.room;

import jakarta.validation.constraints.NotBlank;

public record PlayerActionRequest(
    @NotBlank String roomId,
    @NotBlank String playerId
) {
}

package com.skribbl.backend.room;

import jakarta.validation.constraints.NotBlank;

public record ConnectionRequest(
    @NotBlank String roomId,
    @NotBlank String playerId
) {
}

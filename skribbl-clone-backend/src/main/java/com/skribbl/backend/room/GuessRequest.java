package com.skribbl.backend.room;

import jakarta.validation.constraints.NotBlank;

public record GuessRequest(
    @NotBlank String roomId,
    @NotBlank String playerId,
    @NotBlank String text
) {
}

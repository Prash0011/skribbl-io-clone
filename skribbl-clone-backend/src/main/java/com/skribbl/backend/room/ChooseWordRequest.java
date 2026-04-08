package com.skribbl.backend.room;

import jakarta.validation.constraints.NotBlank;

public record ChooseWordRequest(
    @NotBlank String roomId,
    @NotBlank String playerId,
    @NotBlank String word
) {
}

package com.skribbl.backend.room;

import jakarta.validation.constraints.NotBlank;

public record JoinRoomRequest(
    @NotBlank String roomId,
    @NotBlank String playerName
) {
}

package com.skribbl.backend.room;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRoomRequest(
    @NotBlank String playerName,
    @Valid @NotNull RoomSettingsRequest settings
) {
}

package com.skribbl.backend.room;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RoomSettingsRequest(
    @Min(2) @Max(12) int maxPlayers,
    @Min(1) @Max(5) int rounds,
    @Min(30) @Max(240) int drawTimeSeconds,
    @Min(2) @Max(5) int wordChoiceCount,
    @Min(0) @Max(5) int hintCount,
    boolean privateRoom
) {
}

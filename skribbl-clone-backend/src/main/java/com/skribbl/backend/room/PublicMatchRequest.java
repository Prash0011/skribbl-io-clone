package com.skribbl.backend.room;

import jakarta.validation.constraints.NotBlank;

public record PublicMatchRequest(@NotBlank String playerName) {
}

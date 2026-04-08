package com.skribbl.backend.room;

import java.util.List;

public record RoomView(
    String roomId,
    String phase,
    String roomType,
    RoomSettingsRequest settings,
    List<PlayerView> players,
    String hostPlayerId,
    String drawerPlayerId,
    String drawerName,
    int currentRound,
    int totalRounds,
    int turnNumber,
    String maskedWord,
    long roundEndsAt,
    boolean canStart,
    String statusMessage,
    String winnerName
) {
}

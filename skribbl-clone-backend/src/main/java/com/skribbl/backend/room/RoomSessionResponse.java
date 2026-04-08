package com.skribbl.backend.room;

public record RoomSessionResponse(
    String roomId,
    String playerId,
    String playerName,
    RoomView room
) {
}

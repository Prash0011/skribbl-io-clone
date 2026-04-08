package com.skribbl.backend.room;

public record PlayerView(
    String id,
    String name,
    int score,
    boolean ready,
    boolean host,
    boolean drawer,
    boolean guessedCorrectly
) {
}

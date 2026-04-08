package com.skribbl.backend.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import com.skribbl.backend.room.ChooseWordRequest;
import com.skribbl.backend.room.ConnectionRequest;
import com.skribbl.backend.room.DrawActionRequest;
import com.skribbl.backend.room.GuessRequest;
import com.skribbl.backend.room.PlayerActionRequest;
import com.skribbl.backend.room.RoomService;

@Controller
public class GameMessageController {

    private final RoomService roomService;

    public GameMessageController(RoomService roomService) {
        this.roomService = roomService;
    }

    @MessageMapping("/room.connect")
    public void connect(ConnectionRequest request, SimpMessageHeaderAccessor headers) {
        roomService.connect(request.roomId(), request.playerId(), headers.getSessionId());
    }

    @MessageMapping("/room.ready")
    public void toggleReady(PlayerActionRequest request) {
        roomService.toggleReady(request.roomId(), request.playerId());
    }

    @MessageMapping("/room.start")
    public void startGame(PlayerActionRequest request) {
        roomService.startGame(request.roomId(), request.playerId());
    }

    @MessageMapping("/game.choose-word")
    public void chooseWord(ChooseWordRequest request) {
        roomService.chooseWord(request.roomId(), request.playerId(), request.word());
    }

    @MessageMapping("/game.draw")
    public void draw(DrawActionRequest request) {
        roomService.addStroke(request.roomId(), request.playerId(), request.stroke());
    }

    @MessageMapping("/game.clear")
    public void clearCanvas(PlayerActionRequest request) {
        roomService.clearCanvas(request.roomId(), request.playerId());
    }

    @MessageMapping("/game.undo")
    public void undoCanvas(PlayerActionRequest request) {
        roomService.undoLastStroke(request.roomId(), request.playerId());
    }

    @MessageMapping("/game.guess")
    public void submitGuess(GuessRequest request) {
        roomService.submitGuess(request.roomId(), request.playerId(), request.text());
    }
}

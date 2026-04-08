package com.skribbl.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.skribbl.backend.room.CreateRoomRequest;
import com.skribbl.backend.room.JoinRoomRequest;
import com.skribbl.backend.room.PublicMatchRequest;
import com.skribbl.backend.room.RoomService;
import com.skribbl.backend.room.RoomSessionResponse;
import com.skribbl.backend.room.RoomView;

import jakarta.validation.Valid;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomSessionResponse createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return roomService.createRoom(request);
    }

    @PostMapping("/join")
    public RoomSessionResponse joinRoom(@Valid @RequestBody JoinRoomRequest request) {
        return roomService.joinRoom(request);
    }

    @PostMapping("/public")
    public RoomSessionResponse joinPublicMatch(@Valid @RequestBody PublicMatchRequest request) {
        return roomService.joinPublicMatch(request.playerName());
    }

    @GetMapping("/{roomId}")
    public RoomView getRoom(@PathVariable String roomId, @RequestParam String playerId) {
        return roomService.getRoomView(roomId, playerId);
    }
}

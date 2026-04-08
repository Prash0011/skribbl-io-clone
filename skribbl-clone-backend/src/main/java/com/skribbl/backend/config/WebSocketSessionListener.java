package com.skribbl.backend.config;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.skribbl.backend.room.RoomService;

@Component
public class WebSocketSessionListener {

    private final RoomService roomService;

    public WebSocketSessionListener(RoomService roomService) {
        this.roomService = roomService;
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        roomService.disconnect(accessor.getSessionId());
    }
}

package com.chatapp.message;

import java.security.Principal;
import java.util.List;

import com.chatapp.room.Room;
import com.chatapp.room.RoomRepository;
import com.chatapp.user.User;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class MessageController {
    private final MessageRepository messageRepository;
    private final RoomRepository roomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public MessageController(
            MessageRepository messageRepository,
            RoomRepository roomRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.messageRepository = messageRepository;
        this.roomRepository = roomRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/sendMessage/{roomId}")
    public void sendMessage(
            @DestinationVariable String roomId,
            @Valid @Payload SendMessageRequest request,
            Principal principal
    ) {
        if (!(principal instanceof StompUser stompUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized websocket session");
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
        if (!room.getMemberIds().contains(stompUser.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this room");
        }

        ChatMessage message = new ChatMessage();
        message.setRoomId(roomId);
        message.setSenderId(stompUser.id());
        message.setSenderUsername(stompUser.name());
        message.setContent(request.content().trim());
        message.setType(MessageType.CHAT);

        messagingTemplate.convertAndSend("/topic/" + roomId, messageRepository.save(message));
    }

    public record SendMessageRequest(@NotBlank @Size(max = 2000) String content) {
    }

    public record StompUser(String id, String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }

    @RestController
    @RequestMapping("/api/messages")
    public static class HistoryController {
        private final MessageRepository messageRepository;
        private final RoomRepository roomRepository;

        public HistoryController(MessageRepository messageRepository, RoomRepository roomRepository) {
            this.messageRepository = messageRepository;
            this.roomRepository = roomRepository;
        }

        @GetMapping("/{roomId}")
        public List<ChatMessage> messages(@PathVariable String roomId, @AuthenticationPrincipal User user) {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));
            if (!room.getMemberIds().contains(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this room");
            }
            return messageRepository.findTop100ByRoomIdOrderByCreatedAtAsc(roomId);
        }
    }
}

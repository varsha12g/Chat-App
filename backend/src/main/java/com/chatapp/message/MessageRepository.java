package com.chatapp.message;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findTop100ByRoomIdOrderByCreatedAtAsc(String roomId);
}

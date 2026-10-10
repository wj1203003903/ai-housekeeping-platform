package com.anxin.home.genai.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ResilientRedisChatMemoryRepository implements ChatMemoryRepository {

    private static final Logger LOG = LoggerFactory.getLogger(ResilientRedisChatMemoryRepository.class);
    private static final TypeReference<List<StoredMessage>> MESSAGE_LIST = new TypeReference<>() { };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MemoryProperties properties;
    private final InMemoryChatMemoryRepository fallback = new InMemoryChatMemoryRepository();

    public ResilientRedisChatMemoryRepository(StringRedisTemplate redisTemplate,
                                              ObjectMapper objectMapper,
                                              MemoryProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<String> findConversationIds() {
        try {
            Set<String> members = redisTemplate.opsForSet().members(indexKey());
            return members == null ? List.of() : List.copyOf(members);
        } catch (RuntimeException exception) {
            LOG.warn("Redis chat-memory index is unavailable; using process-local fallback", exception);
            return fallback.findConversationIds();
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        try {
            String json = redisTemplate.opsForValue().get(messageKey(conversationId));
            if (json == null) {
                return List.of();
            }
            List<StoredMessage> stored = objectMapper.readValue(json, MESSAGE_LIST);
            return stored.stream().map(this::restore).toList();
        } catch (RuntimeException | JsonProcessingException exception) {
            LOG.warn("Redis chat memory is unavailable for {}; using process-local fallback",
                conversationId, exception);
            return fallback.findByConversationId(conversationId);
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        fallback.saveAll(conversationId, messages);
        try {
            List<StoredMessage> stored = messages.stream()
                .filter(message -> message.getMessageType() != MessageType.TOOL)
                .map(message -> new StoredMessage(message.getMessageType().getValue(), message.getText()))
                .toList();
            redisTemplate.opsForValue().set(messageKey(conversationId),
                objectMapper.writeValueAsString(stored), properties.getRedisTtl());
            redisTemplate.opsForSet().add(indexKey(), conversationId);
            redisTemplate.expire(indexKey(), properties.getRedisTtl().multipliedBy(2));
        } catch (RuntimeException | JsonProcessingException exception) {
            LOG.warn("Could not persist chat memory for {}; fallback remains available",
                conversationId, exception);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        fallback.deleteByConversationId(conversationId);
        try {
            redisTemplate.delete(messageKey(conversationId));
            redisTemplate.opsForSet().remove(indexKey(), conversationId);
        } catch (RuntimeException exception) {
            LOG.warn("Could not delete Redis chat memory for {}", conversationId, exception);
        }
    }

    private Message restore(StoredMessage message) {
        MessageType type = MessageType.fromValue(message.type());
        if (type == MessageType.ASSISTANT) {
            return new AssistantMessage(message.text());
        }
        if (type == MessageType.SYSTEM) {
            return new SystemMessage(message.text());
        }
        return new UserMessage(message.text());
    }

    private String messageKey(String conversationId) {
        return properties.getRedisKeyPrefix() + conversationId;
    }

    private String indexKey() {
        return properties.getRedisKeyPrefix() + "index";
    }

    private record StoredMessage(String type, String text) {
    }
}

package com.anxin.home.genai.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfiguration {

    @Bean
    @Primary
    ChatMemoryRepository persistentChatMemoryRepository(StringRedisTemplate redisTemplate,
                                                        ObjectMapper objectMapper,
                                                        MemoryProperties properties) {
        return new ResilientRedisChatMemoryRepository(redisTemplate, objectMapper, properties);
    }

    @Bean
    @Primary
    ChatMemory chatMemory(ChatMemoryRepository repository, MemoryProperties properties) {
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(repository)
            .maxMessages(properties.getMaxMessages())
            .build();
    }
}

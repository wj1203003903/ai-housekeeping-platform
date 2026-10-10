package com.anxin.home.genai.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

@Service
public class LongTermMemoryService {
    private static final Logger LOG = LoggerFactory.getLogger(LongTermMemoryService.class);
    private final UserMemoryRepository repository;
    private final JdbcTemplate jdbc;
    private final EmbeddingModel embeddingModel;
    private final MemoryProperties properties;
    private final boolean postgres;

    public LongTermMemoryService(UserMemoryRepository repository, JdbcTemplate jdbc,
                                 EmbeddingModel embeddingModel, MemoryProperties properties,
                                 DataSource dataSource) throws SQLException {
        this.repository = repository;
        this.jdbc = jdbc;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        try (var connection = dataSource.getConnection()) {
            this.postgres = connection.getMetaData().getDatabaseProductName().contains("PostgreSQL");
        }
    }

    @Transactional
    public UserMemory remember(String userId, String kind, String content) {
        UserMemory memory = repository.save(new UserMemory(userId, kind, content));
        if (properties.isSemanticEnabled() && postgres) {
            try {
                jdbc.update("update user_memories set embedding=cast(? as vector) where id=?",
                    vector(embeddingModel.embed(content)), memory.getId());
            } catch (RuntimeException exception) {
                LOG.warn("Semantic embedding failed; text memory was still persisted", exception);
            }
        }
        return memory;
    }

    public List<UserMemory> list(String userId) {
        return repository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<String> recall(String userId, String query) {
        if (properties.isSemanticEnabled() && postgres) {
            try {
                return jdbc.query("select content from user_memories where user_id=? and embedding is not null "
                        + "order by embedding <=> cast(? as vector) limit ?",
                    (rs, row) -> rs.getString(1), userId, vector(embeddingModel.embed(query)), properties.getSemanticTopK());
            } catch (RuntimeException exception) {
                LOG.warn("Semantic recall failed; falling back to recent memories", exception);
            }
        }
        return list(userId).stream().limit(properties.getSemanticTopK()).map(UserMemory::getContent).toList();
    }

    @Transactional
    public void forget(String userId, Long id) { repository.deleteByIdAndUserId(id, userId); }

    private String vector(float[] values) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append(',');
            result.append(values[i]);
        }
        return result.append(']').toString();
    }
}

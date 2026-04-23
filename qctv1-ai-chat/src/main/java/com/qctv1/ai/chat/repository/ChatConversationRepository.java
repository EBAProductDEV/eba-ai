package com.qctv1.ai.chat.repository;

import com.qctv1.ai.chat.domain.ChatConversationRecord;
import com.qctv1.ai.chat.support.BusinessException;
import com.qctv1.ai.chat.vo.ChatConversationSummaryVo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ChatConversationRepository {

    private static final RowMapper<ChatConversationRecord> CONVERSATION_ROW_MAPPER = (rs, rowNum) ->
            new ChatConversationRecord(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getString("title"),
                    rs.getString("summary"),
                    rs.getString("provider"),
                    rs.getString("model"),
                    rs.getInt("message_count"),
                    rs.getTimestamp("last_message_at") == null ? null : rs.getTimestamp("last_message_at").toLocalDateTime(),
                    rs.getTimestamp("created_at").toLocalDateTime(),
                    rs.getTimestamp("updated_at").toLocalDateTime(),
                    rs.getBoolean("deleted")
            );

    private final JdbcTemplate jdbcTemplate;

    public ChatConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChatConversationRecord create(Long userId, String title, String provider, String model) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO ai_chat_conversation
                    (user_id, title, summary, provider, model, message_count, last_message_at, created_at, updated_at, deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, title);
            ps.setString(3, null);
            ps.setString(4, provider);
            ps.setString(5, model);
            ps.setInt(6, 0);
            ps.setTimestamp(7, null);
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            ps.setBoolean(10, false);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(500, "Failed to create conversation");
        }
        return findByIdAndUserId(key.longValue(), userId)
                .orElseThrow(() -> new BusinessException(500, "Conversation was not found after create"));
    }

    public Optional<ChatConversationRecord> findByIdAndUserId(Long id, Long userId) {
        List<ChatConversationRecord> conversations = jdbcTemplate.query("""
                SELECT id, user_id, title, summary, provider, model, message_count, last_message_at, created_at, updated_at, deleted
                FROM ai_chat_conversation
                WHERE id = ? AND user_id = ? AND deleted = 0
                """, CONVERSATION_ROW_MAPPER, id, userId);
        return conversations.stream().findFirst();
    }

    public List<ChatConversationSummaryVo> listByUserId(Long userId, int limit) {
        return jdbcTemplate.query("""
                SELECT c.id,
                       c.title,
                       COALESCE((
                           SELECT m.content
                           FROM ai_chat_message m
                           WHERE m.conversation_id = c.id
                           ORDER BY m.seq_no DESC
                           LIMIT 1
                       ), '') AS preview,
                       c.provider,
                       c.model,
                       c.last_message_at
                FROM ai_chat_conversation c
                WHERE c.user_id = ? AND c.deleted = 0
                ORDER BY c.last_message_at DESC, c.updated_at DESC
                LIMIT ?
                """, (rs, rowNum) -> new ChatConversationSummaryVo(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("preview"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getTimestamp("last_message_at") == null ? null : rs.getTimestamp("last_message_at").toLocalDateTime()
        ), userId, limit);
    }

    public void updateConversationState(
            Long conversationId,
            Long userId,
            String title,
            String summary,
            String provider,
            String model,
            int messageCount,
            LocalDateTime lastMessageAt
    ) {
        jdbcTemplate.update("""
                UPDATE ai_chat_conversation
                SET title = ?,
                    summary = ?,
                    provider = ?,
                    model = ?,
                    message_count = ?,
                    last_message_at = ?,
                    updated_at = ?
                WHERE id = ? AND user_id = ? AND deleted = 0
                """,
                title,
                summary,
                provider,
                model,
                messageCount,
                Timestamp.valueOf(lastMessageAt),
                Timestamp.valueOf(LocalDateTime.now()),
                conversationId,
                userId
        );
    }
}

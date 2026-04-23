package com.qctv1.ai.chat.repository;

import com.qctv1.ai.chat.domain.ChatMessageRecord;
import com.qctv1.ai.chat.support.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Repository
public class ChatMessageRepository {

    private static final RowMapper<ChatMessageRecord> MESSAGE_ROW_MAPPER = (rs, rowNum) -> new ChatMessageRecord(
            rs.getLong("id"),
            rs.getLong("conversation_id"),
            rs.getLong("user_id"),
            rs.getInt("seq_no"),
            rs.getString("role"),
            rs.getString("content"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toLocalDateTime()
    );

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChatMessageRecord save(Long conversationId, Long userId, int seqNo, String role, String content, String status) {
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO ai_chat_message
                    (conversation_id, user_id, seq_no, role, content, status, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, conversationId);
            ps.setLong(2, userId);
            ps.setInt(3, seqNo);
            ps.setString(4, role);
            ps.setString(5, content);
            ps.setString(6, status);
            ps.setTimestamp(7, Timestamp.valueOf(now));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException(500, "Failed to save chat message");
        }
        return new ChatMessageRecord(key.longValue(), conversationId, userId, seqNo, role, content, status, now);
    }

    public List<ChatMessageRecord> listByConversationId(Long conversationId, Long userId) {
        return jdbcTemplate.query("""
                SELECT id, conversation_id, user_id, seq_no, role, content, status, created_at
                FROM ai_chat_message
                WHERE conversation_id = ? AND user_id = ?
                ORDER BY seq_no ASC
                """, MESSAGE_ROW_MAPPER, conversationId, userId);
    }

    public List<ChatMessageRecord> listRecentByConversationId(Long conversationId, Long userId, int limit) {
        List<ChatMessageRecord> messages = jdbcTemplate.query("""
                SELECT id, conversation_id, user_id, seq_no, role, content, status, created_at
                FROM ai_chat_message
                WHERE conversation_id = ? AND user_id = ?
                ORDER BY seq_no DESC
                LIMIT ?
                """, MESSAGE_ROW_MAPPER, conversationId, userId, limit);
        Collections.reverse(messages);
        return messages;
    }

    public List<ChatMessageRecord> listRecentBeforeSeq(Long conversationId, Long userId, int beforeSeqNo, int limit) {
        List<ChatMessageRecord> messages = jdbcTemplate.query("""
                SELECT id, conversation_id, user_id, seq_no, role, content, status, created_at
                FROM ai_chat_message
                WHERE conversation_id = ? AND user_id = ? AND seq_no < ?
                ORDER BY seq_no DESC
                LIMIT ?
                """, MESSAGE_ROW_MAPPER, conversationId, userId, beforeSeqNo, limit);
        messages.sort(Comparator.comparing(ChatMessageRecord::seqNo));
        return messages;
    }
}

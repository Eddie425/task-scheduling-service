package com.example.demo.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class JdbcOutboxRepository implements OutboxRepository {
    private final JdbcTemplate jdbc;

    public JdbcOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(String taskId, String payload) {
        jdbc.update(
                "INSERT INTO task_outbox (task_id, payload, status, next_attempt_at) " +
                        "VALUES (?, ?, 'PENDING', CURRENT_TIMESTAMP(6))",
                taskId,
                payload);
    }

    @Override
    public List<OutboxMessage> findDispatchable(Instant now, int limit) {
        return jdbc.query(
                "SELECT id, task_id, payload, attempt_count FROM task_outbox " +
                        "WHERE status = 'PENDING' AND next_attempt_at <= ? ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED",
                (rs, rowNum) -> new OutboxMessage(
                        rs.getLong("id"),
                        rs.getString("task_id"),
                        rs.getString("payload"),
                        rs.getInt("attempt_count")),
                Timestamp.from(now),
                limit);
    }

    @Override
    public void markSent(long id) {
        jdbc.update("UPDATE task_outbox SET status = 'SENT', last_error = NULL WHERE id = ?", id);
    }

    @Override
    public void markFailed(long id, int attemptCount, Instant nextAttemptAt, String error) {
        jdbc.update(
                "UPDATE task_outbox SET status = 'PENDING', attempt_count = ?, next_attempt_at = ?, last_error = ? " +
                        "WHERE id = ?",
                attemptCount,
                Timestamp.from(nextAttemptAt),
                error,
                id);
    }
}

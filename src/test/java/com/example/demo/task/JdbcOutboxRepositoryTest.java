package com.example.demo.task;

import com.example.demo.task.OutboxRepository.OutboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcOutboxRepositoryTest {
    private JdbcTemplate jdbc;
    private JdbcOutboxRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repository = new JdbcOutboxRepository(jdbc);
    }

    @Test
    void insertsOutboxEventAsPending() {
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);

        repository.insert("task-1", "{\"taskId\":\"task-1\"}");

        verify(jdbc).update(
                eq("INSERT INTO task_outbox (task_id, payload, status, next_attempt_at) " +
                        "VALUES (?, ?, 'PENDING', CURRENT_TIMESTAMP(6))"),
                eq("task-1"),
                eq("{\"taskId\":\"task-1\"}"));
    }

    @Test
    void mapsOnlyDuePendingMessagesForDispatch() throws Exception {
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<OutboxMessage> mapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getLong("id")).thenReturn(4L);
            when(resultSet.getString("task_id")).thenReturn("task-1");
            when(resultSet.getString("payload")).thenReturn("{\"taskId\":\"task-1\"}");
            when(resultSet.getInt("attempt_count")).thenReturn(2);
            return List.of(mapper.mapRow(resultSet, 0));
        });

        assertEquals(List.of(new OutboxMessage(4, "task-1", "{\"taskId\":\"task-1\"}", 2)),
                repository.findDispatchable(Instant.parse("2030-01-01T00:00:00Z"), 10));

        verify(jdbc).query(
                eq("SELECT id, task_id, payload, attempt_count FROM task_outbox " +
                        "WHERE status = 'PENDING' AND next_attempt_at <= ? ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED"),
                any(RowMapper.class),
                eq(Timestamp.from(Instant.parse("2030-01-01T00:00:00Z"))),
                eq(10));
    }

    @Test
    void marksOutboxSentAndRecordsRetryDetailsOnFailure() {
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);
        Instant retryAt = Instant.parse("2030-01-01T00:00:05Z");

        repository.markSent(4);
        repository.markFailed(4, 3, retryAt, "broker unavailable");

        verify(jdbc).update("UPDATE task_outbox SET status = 'SENT', last_error = NULL WHERE id = ?", 4L);
        verify(jdbc).update(
                eq("UPDATE task_outbox SET status = 'PENDING', attempt_count = ?, next_attempt_at = ?, last_error = ? " +
                        "WHERE id = ?"),
                eq(3),
                eq(Timestamp.from(retryAt)),
                eq("broker unavailable"),
                eq(4L));
    }
}

package com.example.demo.task;

import com.example.demo.task.TaskModels.CreateTaskRequest;
import com.example.demo.task.TaskModels.TaskResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcTaskRepositoryTest {
    private JdbcTemplate jdbc;
    private JdbcTaskRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        repository = new JdbcTaskRepository(jdbc, new ObjectMapper());
    }

    @Test
    void createsPendingTaskWithSerializedPayloadAndUtcInstant() {
        Instant executeAt = Instant.parse("2030-01-01T00:00:00Z");
        var payload = JsonNodeFactory.instance.objectNode().put("type", "email");
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);

        repository.create(new CreateTaskRequest("task-1", executeAt, payload));

        verify(jdbc).update(
                eq("INSERT INTO scheduled_task (task_id, execute_at, payload, status) VALUES (?, ?, ?, 'PENDING')"),
                eq("task-1"),
                eq(Timestamp.from(executeAt)),
                eq("{\"type\":\"email\"}"));
    }

    @Test
    void mapsTaskRowsBackToApiModels() throws Exception {
        Instant executeAt = Instant.parse("2030-01-01T00:00:00Z");
        TaskResponse expected = new TaskResponse(
                "task-1", executeAt, JsonNodeFactory.instance.objectNode().put("type", "email"), TaskStatus.PENDING);
        when(jdbc.query(eq("SELECT task_id, execute_at, payload, status FROM scheduled_task WHERE task_id = ?"),
                any(RowMapper.class), eq("task-1"))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<TaskResponse> mapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getString("task_id")).thenReturn(expected.taskId());
            when(resultSet.getTimestamp("execute_at")).thenReturn(Timestamp.from(executeAt));
            when(resultSet.getString("payload")).thenReturn("{\"type\":\"email\"}");
            when(resultSet.getString("status")).thenReturn("PENDING");
            return List.of(mapper.mapRow(resultSet, 0));
        });

        assertEquals(java.util.Optional.of(expected), repository.findById("task-1"));
    }

    @Test
    void surfacesCorruptStoredPayloadInsteadOfReturningInvalidTask() throws Exception {
        when(jdbc.query(eq("SELECT task_id, execute_at, payload, status FROM scheduled_task WHERE task_id = ?"),
                any(RowMapper.class), eq("task-1"))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<TaskResponse> mapper = invocation.getArgument(1);
            ResultSet resultSet = mock(ResultSet.class);
            when(resultSet.getString("task_id")).thenReturn("task-1");
            when(resultSet.getTimestamp("execute_at")).thenReturn(Timestamp.from(Instant.now()));
            when(resultSet.getString("payload")).thenReturn("{broken");
            when(resultSet.getString("status")).thenReturn("PENDING");
            return List.of(mapper.mapRow(resultSet, 0));
        });

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> repository.findById("task-1"));

        assertTrue(error.getMessage().contains("not valid JSON"));
    }

    @Test
    void computesOffsetWithoutIntegerOverflow() {
        when(jdbc.query(any(String.class), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        repository.findPage(TaskStatus.PENDING, 100_000, 100);

        verify(jdbc).query(
                eq("SELECT task_id, execute_at, payload, status FROM scheduled_task WHERE status = ? " +
                        "ORDER BY created_at DESC, task_id LIMIT ? OFFSET ?"),
                any(RowMapper.class),
                eq("PENDING"),
                eq(100),
                eq(10_000_000L));
    }

    @Test
    void updatesTaskOnlyFromExpectedStateForCancellationAndTransitions() {
        when(jdbc.update(any(String.class), any(Object[].class))).thenReturn(1);

        repository.cancelPending("task-1");
        repository.markDispatching("task-1");
        repository.markTriggered("task-1");

        verify(jdbc).update(
                eq("UPDATE scheduled_task SET status = 'CANCELLED' WHERE task_id = ? AND status = 'PENDING'"),
                eq("task-1"));
        verify(jdbc).update(
                eq("UPDATE scheduled_task SET status = 'DISPATCHING' WHERE task_id = ? AND status = 'PENDING'"),
                eq("task-1"));
        verify(jdbc).update(
                eq("UPDATE scheduled_task SET status = 'TRIGGERED' WHERE task_id = ? AND status = 'DISPATCHING'"),
                eq("task-1"));
    }

    @Test
    void countsTasksForRequestedStatus() {
        when(jdbc.queryForObject("SELECT COUNT(*) FROM scheduled_task WHERE status = ?",
                Long.class, "PENDING")).thenReturn(3L);

        assertEquals(3L, repository.count(TaskStatus.PENDING));
    }
}

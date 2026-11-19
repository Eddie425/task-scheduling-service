package com.example.demo.task;

import com.example.demo.task.TaskModels.CreateTaskRequest;
import com.example.demo.task.TaskModels.TaskResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcTaskRepository implements TaskRepository {
    private static final String TASK_COLUMNS = "task_id, execute_at, payload, status";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcTaskRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void create(CreateTaskRequest request) {
        jdbc.update(
                "INSERT INTO scheduled_task (task_id, execute_at, payload, status) VALUES (?, ?, ?, 'PENDING')",
                request.taskId(),
                Timestamp.from(request.executeAt()),
                writeJson(request.payload()));
    }

    @Override
    public Optional<TaskResponse> findById(String taskId) {
        List<TaskResponse> rows = jdbc.query(
                "SELECT " + TASK_COLUMNS + " FROM scheduled_task WHERE task_id = ?",
                this::mapTask,
                taskId);
        return rows.stream().findFirst();
    }

    @Override
    public List<TaskResponse> findPage(TaskStatus status, int page, int size) {
        long offset = (long) page * size;
        return jdbc.query(
                "SELECT " + TASK_COLUMNS + " FROM scheduled_task WHERE status = ? " +
                        "ORDER BY created_at DESC, task_id LIMIT ? OFFSET ?",
                this::mapTask,
                status.name(),
                size,
                offset);
    }

    @Override
    public long count(TaskStatus status) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM scheduled_task WHERE status = ?", Long.class, status.name());
        return count == null ? 0 : count;
    }

    @Override
    public int cancelPending(String taskId) {
        return jdbc.update(
                "UPDATE scheduled_task SET status = 'CANCELLED' WHERE task_id = ? AND status = 'PENDING'",
                taskId);
    }

    @Override
    public List<String> findDueTaskIds(Instant now, int limit) {
        return jdbc.query(
                "SELECT task_id FROM scheduled_task WHERE status = 'PENDING' AND execute_at <= ? " +
                        "ORDER BY execute_at, task_id LIMIT ? FOR UPDATE SKIP LOCKED",
                (rs, rowNum) -> rs.getString(1),
                Timestamp.from(now),
                limit);
    }

    @Override
    public int markDispatching(String taskId) {
        return jdbc.update(
                "UPDATE scheduled_task SET status = 'DISPATCHING' WHERE task_id = ? AND status = 'PENDING'",
                taskId);
    }

    @Override
    public int markTriggered(String taskId) {
        return jdbc.update(
                "UPDATE scheduled_task SET status = 'TRIGGERED' WHERE task_id = ? AND status = 'DISPATCHING'",
                taskId);
    }

    private TaskResponse mapTask(ResultSet rs, int rowNum) throws SQLException {
        try {
            JsonNode payload = objectMapper.readTree(rs.getString("payload"));
            return new TaskResponse(
                    rs.getString("task_id"),
                    rs.getTimestamp("execute_at").toInstant(),
                    payload,
                    TaskStatus.valueOf(rs.getString("status")));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored task payload is not valid JSON", e);
        }
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Task payload cannot be serialized as JSON", e);
        }
    }
}

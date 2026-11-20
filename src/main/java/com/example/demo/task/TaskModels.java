package com.example.demo.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TaskModels {
    private TaskModels() {}

    public record CreateTaskRequest(
            @NotBlank @Size(max = 128) String taskId,
            @NotNull @Future Instant executeAt,
            @NotNull ObjectNode payload) {}

    public record TaskResponse(String taskId, Instant executeAt, JsonNode payload, TaskStatus status) {}

    public record TaskMessage(String taskId, Instant executeAt, JsonNode payload) {}

    public record TaskPageResponse(
            List<TaskResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {}
}

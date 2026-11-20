package com.example.demo.task;

import com.example.demo.task.TaskModels.TaskPageResponse;
import com.example.demo.task.TaskModels.TaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(TaskExceptionHandler.class)
class TaskControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService tasks;

    @Test
    void createsValidTaskAndReturnsCreatedResponse() throws Exception {
        when(tasks.create(any())).thenReturn(task("task-1", TaskStatus.PENDING));

        mockMvc.perform(post("/tasks")
                        .contentType("application/json")
                        .content("""
                                {"taskId":"task-1","executeAt":"2030-01-01T00:00:00Z","payload":{"type":"email"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId").value("task-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(tasks).create(any());
    }

    @Test
    void rejectsInvalidTaskBodyWithFieldDetails() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType("application/json")
                        .content("""
                                {"taskId":" ","executeAt":"2020-01-01T00:00:00Z","payload":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value for: executeAt, payload, taskId"));

        verifyNoInteractions(tasks);
    }

    @Test
    void rejectsNonObjectPayload() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType("application/json")
                        .content("""
                                {"taskId":"task-1","executeAt":"2030-01-01T00:00:00Z","payload":"not-an-object"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tasks);
    }

    @Test
    void rejectsInvalidPaginationAndUnknownStatus() throws Exception {
        mockMvc.perform(get("/tasks").param("page", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/tasks").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(tasks);
    }

    @Test
    void listsTasksWithPaginationMetadata() throws Exception {
        when(tasks.list(TaskStatus.PENDING, 0, 20))
                .thenReturn(new TaskPageResponse(List.of(task("task-1", TaskStatus.PENDING)), 0, 20, 1, 1));

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].taskId").value("task-1"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(tasks).list(TaskStatus.PENDING, 0, 20);
    }

    @Test
    void returnsNotFoundForUnknownTask() throws Exception {
        when(tasks.get("missing")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        mockMvc.perform(get("/tasks/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConflictWhenTaskCannotBeCancelled() throws Exception {
        when(tasks.cancel("running")).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "Task can only be cancelled while pending"));

        mockMvc.perform(delete("/tasks/running"))
                .andExpect(status().isConflict());
    }

    @Test
    void returnsConflictForDuplicateTaskId() throws Exception {
        when(tasks.create(any())).thenThrow(new DuplicateKeyException("duplicate key"));

        mockMvc.perform(post("/tasks")
                        .contentType("application/json")
                        .content("""
                                {"taskId":"task-1","executeAt":"2030-01-01T00:00:00Z","payload":{"type":"email"}}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A task with this taskId already exists"));
    }

    private TaskResponse task(String taskId, TaskStatus status) {
        return new TaskResponse(
                taskId,
                Instant.parse("2030-01-01T00:00:00Z"),
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("type", "email"),
                status);
    }
}

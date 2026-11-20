package com.example.demo.task;

import com.example.demo.task.TaskModels.CreateTaskRequest;
import com.example.demo.task.TaskModels.TaskPageResponse;
import com.example.demo.task.TaskModels.TaskResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {
    private TaskRepository repository;
    private TaskReadCache cache;
    private TaskService service;

    @BeforeEach
    void setUp() {
        repository = mock(TaskRepository.class);
        cache = mock(TaskReadCache.class);
        service = new TaskService(repository, cache);
    }

    @Test
    void readsMysqlWhenCacheMissesAndCachesTerminalTask() {
        TaskResponse task = new TaskResponse(
                "done-1", Instant.parse("2030-01-01T00:00:00Z"),
                new ObjectMapper().createObjectNode(), TaskStatus.TRIGGERED);
        when(cache.get("done-1")).thenReturn(Optional.empty());
        when(repository.findById("done-1")).thenReturn(Optional.of(task));

        assertEquals(task, service.get("done-1"));

        verify(cache).put(task);
    }

    @Test
    void servesTerminalTaskFromCacheWithoutReadingMysql() {
        TaskResponse task = new TaskResponse(
                "cached-1", Instant.parse("2030-01-01T00:00:00Z"),
                new ObjectMapper().createObjectNode(), TaskStatus.CANCELLED);
        when(cache.get("cached-1")).thenReturn(Optional.of(task));

        assertEquals(task, service.get("cached-1"));

        verify(repository, never()).findById("cached-1");
    }

    @Test
    void reportsMissingTaskAsNotFound() {
        when(cache.get("missing")).thenReturn(Optional.empty());
        when(repository.findById("missing")).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.get("missing"));

        assertEquals(404, error.getStatusCode().value());
        verify(cache, never()).put(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsCancellationAfterTaskHasStartedDispatching() {
        TaskResponse task = new TaskResponse(
                "running-1", Instant.parse("2030-01-01T00:00:00Z"),
                new ObjectMapper().createObjectNode(), TaskStatus.DISPATCHING);
        when(repository.cancelPending("running-1")).thenReturn(0);
        when(repository.findById("running-1")).thenReturn(Optional.of(task));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.cancel("running-1"));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void acceptsRepeatedCancellation() {
        TaskResponse task = new TaskResponse(
                "cancelled-1", Instant.parse("2030-01-01T00:00:00Z"),
                new ObjectMapper().createObjectNode(), TaskStatus.CANCELLED);
        when(repository.cancelPending("cancelled-1")).thenReturn(0);
        when(repository.findById("cancelled-1")).thenReturn(Optional.of(task));

        assertEquals(task, service.cancel("cancelled-1"));
    }

    @Test
    void createsTaskAndReturnsSavedRecord() {
        var objectMapper = new ObjectMapper();
        var payload = objectMapper.createObjectNode().put("type", "email");
        var request = new CreateTaskRequest("new-1", Instant.parse("2030-01-01T00:00:00Z"), payload);
        TaskResponse saved = new TaskResponse("new-1", request.executeAt(), payload, TaskStatus.PENDING);
        when(repository.findById("new-1")).thenReturn(Optional.of(saved));

        assertEquals(saved, service.create(request));

        verify(repository).create(request);
    }

    @Test
    void calculatesPaginationMetadataForTaskList() {
        TaskResponse task = new TaskResponse(
                "pending-1", Instant.parse("2030-01-01T00:00:00Z"),
                new ObjectMapper().createObjectNode(), TaskStatus.PENDING);
        when(repository.count(TaskStatus.PENDING)).thenReturn(21L);
        when(repository.findPage(TaskStatus.PENDING, 1, 20)).thenReturn(List.of(task));

        TaskPageResponse result = service.list(TaskStatus.PENDING, 1, 20);

        assertEquals(List.of(task), result.content());
        assertEquals(21, result.totalElements());
        assertEquals(2, result.totalPages());
        assertEquals(1, result.page());
    }

    @Test
    void returnsEmptyPageMetadataWhenNoTasksExist() {
        when(repository.count(TaskStatus.CANCELLED)).thenReturn(0L);
        when(repository.findPage(TaskStatus.CANCELLED, 0, 10)).thenReturn(List.of());

        TaskPageResponse result = service.list(TaskStatus.CANCELLED, 0, 10);

        assertEquals(0, result.totalPages());
        assertEquals(0, result.totalElements());
        assertEquals(List.of(), result.content());
    }

    @Test
    void returnsCancelledTaskAfterSuccessfulCancellation() {
        TaskResponse task = new TaskResponse(
                "cancel-me", Instant.parse("2030-01-01T00:00:00Z"),
                new ObjectMapper().createObjectNode(), TaskStatus.CANCELLED);
        when(repository.cancelPending("cancel-me")).thenReturn(1);
        when(repository.findById("cancel-me")).thenReturn(Optional.of(task));

        assertEquals(task, service.cancel("cancel-me"));
    }
}

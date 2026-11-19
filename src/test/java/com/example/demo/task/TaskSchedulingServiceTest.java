package com.example.demo.task;

import com.example.demo.task.TaskModels.TaskResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskSchedulingServiceTest {
    private TaskRepository tasks;
    private OutboxRepository outbox;
    private TaskSchedulingService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        tasks = mock(TaskRepository.class);
        outbox = mock(OutboxRepository.class);
        objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new TaskSchedulingService(tasks, outbox, objectMapper, 25);
    }

    @Test
    void atomicallyTransitionsDuePendingTaskAndCreatesOutboxEntry() {
        when(tasks.findDueTaskIds(any(Instant.class), eq(25))).thenReturn(List.of("due-1"));
        when(tasks.markDispatching("due-1")).thenReturn(1);
        when(tasks.findById("due-1")).thenReturn(java.util.Optional.of(new TaskResponse(
                "due-1",
                Instant.parse("2030-01-01T00:00:00Z"),
                new ObjectMapper().createObjectNode().put("type", "email"),
                TaskStatus.DISPATCHING)));

        service.enqueueDueTasks();

        verify(tasks).markDispatching("due-1");
        org.mockito.ArgumentCaptor<String> message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(outbox).insert(eq("due-1"), message.capture());
        try {
            var json = objectMapper.readTree(message.getValue());
            assertEquals("due-1", json.get("taskId").asText());
            assertEquals("2030-01-01T00:00:00Z", json.get("executeAt").asText());
            assertEquals("email", json.get("payload").get("type").asText());
            assertFalse(json.has("status"));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AssertionError("Outbox event should be valid JSON", e);
        }
    }

    @Test
    void doesNotCreateOutboxEntryWhenTaskWasNoLongerPending() {
        when(tasks.findDueTaskIds(any(Instant.class), eq(25))).thenReturn(List.of("cancelled-1"));
        when(tasks.markDispatching("cancelled-1")).thenReturn(0);

        service.enqueueDueTasks();

        org.mockito.Mockito.verifyNoInteractions(outbox);
        org.mockito.Mockito.verify(tasks, org.mockito.Mockito.never()).findById("cancelled-1");
    }
}

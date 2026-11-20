package com.example.demo.task;

import com.example.demo.task.OutboxRepository.OutboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxDispatchServiceTest {
    private OutboxRepository outbox;
    private TaskRepository tasks;
    private TaskMessagePublisher publisher;
    private OutboxDispatchService service;

    @BeforeEach
    void setUp() {
        outbox = mock(OutboxRepository.class);
        tasks = mock(TaskRepository.class);
        publisher = mock(TaskMessagePublisher.class);
        service = new OutboxDispatchService(outbox, tasks, publisher, 10, 300);
    }

    @Test
    void marksTaskTriggeredAfterSuccessfulPublish() throws Exception {
        when(outbox.findDispatchable(org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.eq(10)))
                .thenReturn(List.of(new OutboxMessage(1, "task-1", "{\"taskId\":\"task-1\"}", 0)));

        service.dispatchDueMessages();

        verify(outbox).markSent(1);
        verify(tasks).markTriggered("task-1");
    }

    @Test
    void schedulesRetryAndLeavesTaskDispatchingWhenPublishFails() throws Exception {
        when(outbox.findDispatchable(org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.eq(10)))
                .thenReturn(List.of(new OutboxMessage(7, "task-7", "{}", 2)));
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(publisher).publish("task-7", "{}");

        service.dispatchDueMessages();

        org.mockito.ArgumentCaptor<Instant> nextAttempt = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(outbox).markFailed(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq(3),
                nextAttempt.capture(), org.mockito.ArgumentMatchers.eq("broker unavailable"));
        org.junit.jupiter.api.Assertions.assertTrue(
                nextAttempt.getValue().isAfter(Instant.now().plusSeconds(3)));
        org.mockito.Mockito.verify(tasks, org.mockito.Mockito.never()).markTriggered("task-7");
    }

    @Test
    void capsRetryDelayAndHandlesExceptionsWithoutMessages() throws Exception {
        service = new OutboxDispatchService(outbox, tasks, publisher, 10, 5);
        when(outbox.findDispatchable(org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.eq(10)))
                .thenReturn(List.of(new OutboxMessage(8, "task-8", "{}", 10)));
        org.mockito.Mockito.doThrow(new IllegalStateException())
                .when(publisher).publish("task-8", "{}");

        service.dispatchDueMessages();

        org.mockito.ArgumentCaptor<Instant> nextAttempt = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(outbox).markFailed(org.mockito.ArgumentMatchers.eq(8L), org.mockito.ArgumentMatchers.eq(11),
                nextAttempt.capture(), org.mockito.ArgumentMatchers.eq("IllegalStateException"));
        org.junit.jupiter.api.Assertions.assertTrue(
                nextAttempt.getValue().isAfter(Instant.now().plusSeconds(4)));
        org.junit.jupiter.api.Assertions.assertTrue(
                nextAttempt.getValue().isBefore(Instant.now().plusSeconds(6)));
    }

    @Test
    void restoresInterruptFlagAndStopsProcessingRemainingMessages() throws Exception {
        when(outbox.findDispatchable(org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.eq(10)))
                .thenReturn(List.of(
                        new OutboxMessage(9, "task-9", "{}", 0),
                        new OutboxMessage(10, "task-10", "{}", 0)));
        org.mockito.Mockito.doThrow(new InterruptedException("interrupted"))
                .when(publisher).publish("task-9", "{}");

        try {
            service.dispatchDueMessages();

            org.junit.jupiter.api.Assertions.assertTrue(Thread.currentThread().isInterrupted());
            verify(outbox).markFailed(org.mockito.ArgumentMatchers.eq(9L),
                    org.mockito.ArgumentMatchers.eq(1),
                    org.mockito.ArgumentMatchers.any(Instant.class),
                    org.mockito.ArgumentMatchers.eq("interrupted"));
            org.mockito.Mockito.verify(publisher, org.mockito.Mockito.never()).publish("task-10", "{}");
        } finally {
            Thread.interrupted();
        }
    }
}

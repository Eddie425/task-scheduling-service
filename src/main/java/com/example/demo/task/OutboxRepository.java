package com.example.demo.task;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository {
    void insert(String taskId, String payload);

    List<OutboxMessage> findDispatchable(Instant now, int limit);

    void markSent(long id);

    void markFailed(long id, int attemptCount, Instant nextAttemptAt, String error);

    record OutboxMessage(long id, String taskId, String payload, int attemptCount) {}
}

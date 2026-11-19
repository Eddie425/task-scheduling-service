package com.example.demo.task;

import com.example.demo.task.OutboxRepository.OutboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OutboxDispatchService {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchService.class);

    private final OutboxRepository outbox;
    private final TaskRepository tasks;
    private final TaskMessagePublisher publisher;
    private final int batchSize;
    private final long maxBackoffSeconds;

    public OutboxDispatchService(
            OutboxRepository outbox,
            TaskRepository tasks,
            TaskMessagePublisher publisher,
            @Value("${app.outbox.batch-size:100}") int batchSize,
            @Value("${app.outbox.max-backoff-seconds:300}") long maxBackoffSeconds) {
        this.outbox = outbox;
        this.tasks = tasks;
        this.publisher = publisher;
        this.batchSize = batchSize;
        this.maxBackoffSeconds = maxBackoffSeconds;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    @Transactional
    public void dispatchDueMessages() {
        List<OutboxMessage> messages = outbox.findDispatchable(Instant.now(), batchSize);
        for (OutboxMessage message : messages) {
            try {
                publisher.publish(message.taskId(), message.payload());
                outbox.markSent(message.id());
                tasks.markTriggered(message.taskId());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                recordFailure(message, exception);
                break;
            } catch (Exception exception) {
                recordFailure(message, exception);
            }
        }
    }

    private void recordFailure(OutboxMessage message, Exception exception) {
        int attempt = message.attemptCount() + 1;
        long delay = Math.min(maxBackoffSeconds, 1L << Math.min(attempt - 1, 20));
        String error = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        outbox.markFailed(
                message.id(),
                attempt,
                Instant.now().plusSeconds(delay),
                error.substring(0, Math.min(error.length(), 1000)));
        log.warn("Failed to publish scheduled task {} (attempt {})", message.taskId(), attempt, exception);
    }
}

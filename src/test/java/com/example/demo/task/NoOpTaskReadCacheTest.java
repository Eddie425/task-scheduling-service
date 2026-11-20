package com.example.demo.task;

import com.example.demo.task.TaskModels.TaskResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NoOpTaskReadCacheTest {
    @Test
    void alwaysMissesAndIgnoresWritesWhenCachingIsDisabled() {
        NoOpTaskReadCache cache = new NoOpTaskReadCache();
        TaskResponse task = new TaskResponse(
                "task-1",
                Instant.parse("2030-01-01T00:00:00Z"),
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(),
                TaskStatus.TRIGGERED);

        cache.put(task);

        assertTrue(cache.get("task-1").isEmpty());
    }
}

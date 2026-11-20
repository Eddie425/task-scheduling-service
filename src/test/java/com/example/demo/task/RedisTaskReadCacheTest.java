package com.example.demo.task;

import com.example.demo.task.TaskModels.TaskResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisTaskReadCacheTest {
    private static final String KEY = "scheduled-task:terminal:task-1";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ObjectMapper objectMapper;
    private RedisTaskReadCache cache;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        when(redis.opsForValue()).thenReturn(values);
        cache = new RedisTaskReadCache(redis, objectMapper, 60);
    }

    @Test
    void readsAndDeserializesCachedTerminalTask() throws Exception {
        TaskResponse task = terminalTask(TaskStatus.TRIGGERED);
        when(values.get(KEY)).thenReturn(objectMapper.writeValueAsString(task));

        assertEquals(Optional.of(task), cache.get("task-1"));
    }

    @Test
    void returnsCacheMissWhenKeyIsAbsent() {
        when(values.get(KEY)).thenReturn(null);

        assertEquals(Optional.empty(), cache.get("task-1"));
    }

    @Test
    void fallsBackToMysqlWhenRedisReadFails() {
        when(values.get(KEY)).thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertEquals(Optional.empty(), cache.get("task-1"));
    }

    @Test
    void deletesMalformedCachedEntryAndReturnsMiss() {
        when(values.get(KEY)).thenReturn("{invalid");

        assertEquals(Optional.empty(), cache.get("task-1"));

        verify(redis).delete(KEY);
    }

    @Test
    void cachesOnlyTerminalTasksWithConfiguredTtl() throws Exception {
        cache.put(terminalTask(TaskStatus.CANCELLED));

        verify(values).set(eq(KEY), any(String.class), eq(Duration.ofSeconds(60)));
    }

    @Test
    void doesNotCacheMutableTaskStates() {
        cache.put(new TaskResponse(
                "task-1", Instant.parse("2030-01-01T00:00:00Z"),
                objectMapper.createObjectNode(), TaskStatus.PENDING));

        verify(redis, never()).opsForValue();
    }

    @Test
    void ignoresCacheWriteFailuresBecauseMysqlIsAuthoritative() {
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(values).set(eq(KEY), any(String.class), any(Duration.class));

        cache.put(terminalTask(TaskStatus.TRIGGERED));

        verify(values).set(eq(KEY), any(String.class), eq(Duration.ofSeconds(60)));
    }

    @Test
    void invalidatesUnreadableSerializedTaskWithoutFailingRequest() {
        when(values.get(KEY)).thenReturn("""
                {"taskId":"task-1","executeAt":"2030-01-01T00:00:00Z","payload":{},"status":"UNKNOWN"}
                """);

        assertTrue(cache.get("task-1").isEmpty());

        verify(redis).delete(KEY);
    }

    private TaskResponse terminalTask(TaskStatus status) {
        return new TaskResponse(
                "task-1",
                Instant.parse("2030-01-01T00:00:00Z"),
                objectMapper.createObjectNode().put("type", "email"),
                status);
    }
}

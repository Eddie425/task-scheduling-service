package com.example.demo.task;

import com.example.demo.task.TaskModels.TaskResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.redis-cache.enabled", havingValue = "true")
public class RedisTaskReadCache implements TaskReadCache {
    private static final Logger log = LoggerFactory.getLogger(RedisTaskReadCache.class);
    private static final String KEY_PREFIX = "scheduled-task:terminal:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisTaskReadCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${app.redis-cache.ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Optional<TaskResponse> get(String taskId) {
        try {
            String cached = redis.opsForValue().get(KEY_PREFIX + taskId);
            if (cached == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(cached, TaskResponse.class));
        } catch (DataAccessException e) {
            log.warn("Redis cache read failed; using MySQL for task {}", taskId, e);
            return Optional.empty();
        } catch (JsonProcessingException e) {
            log.warn("Discarding invalid cached task {}", taskId, e);
            try {
                redis.delete(KEY_PREFIX + taskId);
            } catch (DataAccessException deleteError) {
                log.warn("Could not remove invalid cached task {}", taskId, deleteError);
            }
            return Optional.empty();
        }
    }

    @Override
    public void put(TaskResponse task) {
        if (task.status() != TaskStatus.TRIGGERED && task.status() != TaskStatus.CANCELLED) {
            return;
        }
        try {
            redis.opsForValue().set(
                    KEY_PREFIX + task.taskId(),
                    objectMapper.writeValueAsString(task),
                    ttl);
        } catch (DataAccessException e) {
            log.warn("Redis cache write failed for task {}; MySQL remains authoritative", task.taskId(), e);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize task {} into Redis cache", task.taskId(), e);
        }
    }
}

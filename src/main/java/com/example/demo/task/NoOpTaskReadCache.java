package com.example.demo.task;

import com.example.demo.task.TaskModels.TaskResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.redis-cache.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpTaskReadCache implements TaskReadCache {
    @Override
    public Optional<TaskResponse> get(String taskId) {
        return Optional.empty();
    }

    @Override
    public void put(TaskResponse task) {}
}

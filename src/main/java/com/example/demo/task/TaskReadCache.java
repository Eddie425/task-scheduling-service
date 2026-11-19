package com.example.demo.task;

import com.example.demo.task.TaskModels.TaskResponse;

import java.util.Optional;

public interface TaskReadCache {
    Optional<TaskResponse> get(String taskId);

    void put(TaskResponse task);
}

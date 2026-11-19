package com.example.demo.task;

import com.example.demo.task.TaskModels.CreateTaskRequest;
import com.example.demo.task.TaskModels.TaskResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    void create(CreateTaskRequest request);

    Optional<TaskResponse> findById(String taskId);

    List<TaskResponse> findPage(TaskStatus status, int page, int size);

    long count(TaskStatus status);

    int cancelPending(String taskId);

    List<String> findDueTaskIds(Instant now, int limit);

    int markDispatching(String taskId);

    int markTriggered(String taskId);
}

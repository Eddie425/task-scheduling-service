package com.example.demo.task;

import com.example.demo.task.TaskModels.CreateTaskRequest;
import com.example.demo.task.TaskModels.TaskPageResponse;
import com.example.demo.task.TaskModels.TaskResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
public class TaskService {
    private final TaskRepository tasks;
    private final TaskReadCache cache;

    public TaskService(TaskRepository tasks, TaskReadCache cache) {
        this.tasks = tasks;
        this.cache = cache;
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        tasks.create(request);
        return tasks.findById(request.taskId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(String taskId) {
        Optional<TaskResponse> cached = cache.get(taskId);
        if (cached.isPresent()) {
            return cached.get();
        }
        TaskResponse task = tasks.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        cache.put(task);
        return task;
    }

    @Transactional(readOnly = true)
    public TaskPageResponse list(TaskStatus status, int page, int size) {
        long total = tasks.count(status);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new TaskPageResponse(tasks.findPage(status, page, size), page, size, total, totalPages);
    }

    @Transactional
    public TaskResponse cancel(String taskId) {
        if (tasks.cancelPending(taskId) == 0) {
            TaskResponse current = tasks.findById(taskId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
            if (current.status() != TaskStatus.CANCELLED) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Task can only be cancelled while pending");
            }
            return current;
        }
        return tasks.findById(taskId).orElseThrow();
    }
}

package com.example.demo.task;

import com.example.demo.task.TaskModels.CreateTaskRequest;
import com.example.demo.task.TaskModels.TaskPageResponse;
import com.example.demo.task.TaskModels.TaskResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tasks")
@Validated
public class TaskController {
    private final TaskService tasks;

    public TaskController(TaskService tasks) {
        this.tasks = tasks;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        return tasks.create(request);
    }

    @GetMapping("/{taskId}")
    public TaskResponse get(@PathVariable String taskId) {
        return tasks.get(taskId);
    }

    @DeleteMapping("/{taskId}")
    public TaskResponse cancel(@PathVariable String taskId) {
        return tasks.cancel(taskId);
    }

    @GetMapping
    public TaskPageResponse list(
            @RequestParam(defaultValue = "PENDING") TaskStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return tasks.list(status, page, size);
    }
}

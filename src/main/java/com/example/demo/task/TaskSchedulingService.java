package com.example.demo.task;

import com.example.demo.task.TaskModels.TaskMessage;
import com.example.demo.task.TaskModels.TaskResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TaskSchedulingService {
    private final TaskRepository tasks;
    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public TaskSchedulingService(
            TaskRepository tasks,
            OutboxRepository outbox,
            ObjectMapper objectMapper,
            @Value("${app.scheduler.batch-size:100}") int batchSize) {
        this.tasks = tasks;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.scheduler.poll-interval-ms:1000}")
    @Transactional
    public void enqueueDueTasks() {
        List<String> dueTaskIds = tasks.findDueTaskIds(Instant.now(), batchSize);
        for (String taskId : dueTaskIds) {
            if (tasks.markDispatching(taskId) == 1) {
                TaskResponse task = tasks.findById(taskId).orElseThrow();
                outbox.insert(taskId, writeJson(task));
            }
        }
    }

    private String writeJson(TaskResponse task) {
        try {
            return objectMapper.writeValueAsString(
                    new TaskMessage(task.taskId(), task.executeAt(), task.payload()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize task for the outbox", e);
        }
    }
}

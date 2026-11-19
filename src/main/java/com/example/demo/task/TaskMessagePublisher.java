package com.example.demo.task;

public interface TaskMessagePublisher {
    void publish(String taskId, String payload) throws Exception;
}

package com.example.demo.task;

import jakarta.annotation.PreDestroy;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class RocketMqTaskMessagePublisher implements TaskMessagePublisher {
    private final String nameServer;
    private final String producerGroup;
    private final String topic;
    private DefaultMQProducer producer;

    public RocketMqTaskMessagePublisher(
            @Value("${rocketmq.name-server}") String nameServer,
            @Value("${rocketmq.producer.group}") String producerGroup,
            @Value("${rocketmq.topic}") String topic) {
        this.nameServer = nameServer;
        this.producerGroup = producerGroup;
        this.topic = topic;
    }

    @Override
    public synchronized void publish(String taskId, String payload) throws Exception {
        DefaultMQProducer activeProducer = getProducer();
        Message message = new Message(topic, "task-trigger", payload.getBytes(StandardCharsets.UTF_8));
        message.setKeys(taskId);
        message.putUserProperty("idempotencyKey", taskId);
        activeProducer.send(message);
    }

    private DefaultMQProducer getProducer() throws MQClientException {
        if (producer == null) {
            DefaultMQProducer newProducer = new DefaultMQProducer(producerGroup);
            newProducer.setNamesrvAddr(nameServer);
            newProducer.start();
            producer = newProducer;
        }
        return producer;
    }

    @PreDestroy
    public synchronized void close() {
        if (producer != null) {
            producer.shutdown();
        }
    }
}

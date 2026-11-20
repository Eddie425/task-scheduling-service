package com.example.demo.task;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMqTaskMessagePublisherTest {
    private DefaultMQProducer producer;
    private RocketMqTaskMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        producer = mock(DefaultMQProducer.class);
        publisher = new RocketMqTaskMessagePublisher("localhost:9876", "test-producer", "task-topic");
        ReflectionTestUtils.setField(publisher, "producer", producer);
    }

    @Test
    void publishesJsonWithTaskIdAsKeyAndIdempotencyProperty() throws Exception {
        SendResult result = new SendResult();
        result.setSendStatus(SendStatus.SEND_OK);
        when(producer.send(any(Message.class))).thenReturn(result);

        publisher.publish("task-1", "{\"taskId\":\"task-1\"}");

        var message = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(producer).send(message.capture());
        org.junit.jupiter.api.Assertions.assertEquals("task-topic", message.getValue().getTopic());
        org.junit.jupiter.api.Assertions.assertEquals("task-trigger", message.getValue().getTags());
        org.junit.jupiter.api.Assertions.assertEquals("task-1", message.getValue().getKeys());
        org.junit.jupiter.api.Assertions.assertEquals(
                "task-1", message.getValue().getUserProperty("idempotencyKey"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "{\"taskId\":\"task-1\"}", new String(message.getValue().getBody(), StandardCharsets.UTF_8));

        publisher.close();
        verify(producer).shutdown();
    }

    @Test
    void treatsUnconfirmedBrokerSendAsFailureForOutboxRetry() throws Exception {
        SendResult result = new SendResult();
        result.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
        when(producer.send(any(Message.class))).thenReturn(result);

        assertThrows(IllegalStateException.class, () -> publisher.publish("task-1", "{}"));
    }

    @Test
    void startsProducerLazilyWithConfiguredNameServerAndGroup() throws Exception {
        SendResult result = new SendResult();
        result.setSendStatus(SendStatus.SEND_OK);
        try (MockedConstruction<DefaultMQProducer> constructed = mockConstruction(
                DefaultMQProducer.class,
                (mock, context) -> when(mock.send(any(Message.class))).thenReturn(result))) {
            RocketMqTaskMessagePublisher lazyPublisher =
                    new RocketMqTaskMessagePublisher("namesrv:9876", "group-a", "topic-a");

            lazyPublisher.publish("task-2", "{}");

            DefaultMQProducer created = constructed.constructed().getFirst();
            verify(created).setNamesrvAddr("namesrv:9876");
            verify(created).start();
            verify(created).send(any(Message.class));
            lazyPublisher.close();
            verify(created).shutdown();
        }
    }

    @Test
    void doesNotRetainProducerWhenStartupFails() throws Exception {
        try (MockedConstruction<DefaultMQProducer> constructed = mockConstruction(
                DefaultMQProducer.class,
                (mock, context) -> org.mockito.Mockito.doThrow(
                        new org.apache.rocketmq.client.exception.MQClientException("startup failed", null))
                        .when(mock).start())) {
            RocketMqTaskMessagePublisher lazyPublisher =
                    new RocketMqTaskMessagePublisher("namesrv:9876", "group-a", "topic-a");

            assertThrows(Exception.class, () -> lazyPublisher.publish("task-3", "{}"));
            lazyPublisher.close();

            verify(constructed.constructed().getFirst()).start();
            verify(constructed.constructed().getFirst(), org.mockito.Mockito.never()).shutdown();
        }
    }
}

package com.faust.easychat.mq.publisher;

import org.springframework.kafka.core.KafkaTemplate;

import com.faust.easychat.dto.ws.WsPushEvent;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class KafkaWsPushPublisher {

    private final KafkaTemplate<String, WsPushEvent> kafkaTemplate;
    private final String topic;

    public void publish(WsPushEvent event) {
        kafkaTemplate.send(topic, event.getToUserId(), event);
    }
}

package com.faust.easychat.ws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faust.easychat.dto.ws.WsPushEvent;
import com.faust.easychat.exception.AppException;
import com.faust.easychat.mq.publisher.KafkaWsPushPublisher;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "easychat.ws.delivery", havingValue = "kafka")
@RequiredArgsConstructor
public class KafkaWsPushRouter implements WsPushRouter {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final KafkaWsPushPublisher publisher;

    @Override
    public void route(String toUserId, String type, Object payload) {
        try {
            String payloadJson = payload == null ? null : MAPPER.writeValueAsString(payload);
            publisher.publish(new WsPushEvent(toUserId, type, payloadJson));
        } catch (JsonProcessingException e) {
            throw AppException.internalError("WebSocket 推送序列化失败");
        }
    }
}

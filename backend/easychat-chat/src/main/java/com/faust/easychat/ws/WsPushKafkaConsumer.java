package com.faust.easychat.ws;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.faust.easychat.dto.ws.WsPushEvent;
import com.faust.easychat.netty.WsSessionManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "easychat.ws.delivery", havingValue = "kafka")
@RequiredArgsConstructor
public class WsPushKafkaConsumer {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private final WsSessionManager sessionManager;
    private final WsChannelWriter channelWriter;

    @KafkaListener(
            topics = "${easychat.kafka.ws-push-topic:easychat-ws-push}",
            groupId = "#{@wsKafkaConsumerGroup}")
    public void onWsPush(WsPushEvent event) {
        if (event == null || event.getToUserId() == null || event.getType() == null) {
            return;
        }
        if (sessionManager.getChannel(event.getToUserId()) == null) {
            return;
        }
        Object payload = deserializePayload(event.getPayloadJson());
        channelWriter.sendToUser(event.getToUserId(), event.getType(), payload);
    }

    private Object deserializePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(payloadJson);
        } catch (Exception e) {
            log.warn("WS 推送 payload 反序列化失败，按原始字符串下发: {}", e.getMessage());
            return payloadJson;
        }
    }
}

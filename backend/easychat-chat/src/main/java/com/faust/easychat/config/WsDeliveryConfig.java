package com.faust.easychat.config;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
public class WsDeliveryConfig {

    @Bean
    public String wsInstanceId(@Value("${easychat.ws.instance-id:}") String configured) {
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return UUID.randomUUID().toString();
    }

    @Configuration
    @EnableKafka
    @ConditionalOnProperty(name = "easychat.ws.delivery", havingValue = "kafka")
    static class KafkaListenerConfig {

        /**
         * 每个 chat 实例独立 consumer group，才能收到全量 WS 推送后在本机筛选连接。
         * 可通过 easychat.ws.kafka-consumer-group 显式覆盖。
         */
        @Bean("wsKafkaConsumerGroup")
        String wsKafkaConsumerGroup(
                @Value("${easychat.ws.kafka-consumer-group:}") String configured,
                @Value("${spring.application.name}") String appName,
                @Qualifier("wsInstanceId") String instanceId) {
            if (configured != null && !configured.isBlank()) {
                return configured;
            }
            return appName + "-ws-push-" + instanceId;
        }
    }
}

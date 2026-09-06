package com.faust.easychat.mq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "easychat.kafka")
public class EasychatKafkaProperties {

    private String wsPushTopic = "easychat-ws-push";
}

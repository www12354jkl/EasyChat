package com.faust.easychat.mq.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.faust.easychat.dto.ws.WsPushEvent;
import com.faust.easychat.mq.publisher.KafkaWsPushPublisher;

@AutoConfiguration
@ConditionalOnProperty(name = "easychat.ws.delivery", havingValue = "kafka")
@EnableConfigurationProperties(EasychatKafkaProperties.class)
public class KafkaWsPushAutoConfiguration {

    @Bean
    public ProducerFactory<String, WsPushEvent> wsPushProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, WsPushEvent> wsPushKafkaTemplate(
            ProducerFactory<String, WsPushEvent> wsPushProducerFactory) {
        return new KafkaTemplate<>(wsPushProducerFactory);
    }

    @Bean
    public KafkaWsPushPublisher kafkaWsPushPublisher(
            KafkaTemplate<String, WsPushEvent> wsPushKafkaTemplate,
            EasychatKafkaProperties properties) {
        return new KafkaWsPushPublisher(wsPushKafkaTemplate, properties.getWsPushTopic());
    }
}

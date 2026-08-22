package com.faust.easychat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "tencent.cos")
@Data
public class CosConfig {
    private String secretId;
    private String secretKey;
    private String region;
    private String bucket;
    private String urlPrefix;
}

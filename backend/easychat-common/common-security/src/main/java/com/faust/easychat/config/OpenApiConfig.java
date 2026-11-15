package com.faust.easychat.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "Bearer";

    @Bean
    public OpenAPI openAPI(@Value("${spring.application.name}") String appName) {
        String title = appName.contains("user") ? "EasyChat 用户服务"
                : appName.contains("chat") ? "EasyChat 聊天服务"
                : appName;
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .description("本地开发文档。点击右上角 Authorize，填入登录返回的 accessToken（不用加 Bearer 前缀）。生产环境默认关闭。")
                        .version("1.0"))
                .servers(List.of(new Server().url("/").description("当前访问地址")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 accessToken")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}

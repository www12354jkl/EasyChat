package com.faust.easychat.config;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.WebCallbackManager")
public class SentinelWebMvcConfig {

    private static final String BLOCK_BODY = "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}";

    @PostConstruct
    public void initBlockHandler() throws Exception {
        Class<?> managerClass = Class.forName("com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.WebCallbackManager");
        Class<?> handlerClass = Class.forName("com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.UrlBlockHandler");

        Object handler = Proxy.newProxyInstance(
                handlerClass.getClassLoader(),
                new Class<?>[] { handlerClass },
                (proxy, method, args) -> {
                    HttpServletResponse response = (HttpServletResponse) args[1];
                    response.setContentType("application/json;charset=UTF-8");
                    response.setStatus(200);
                    response.getWriter().print(BLOCK_BODY);
                    return null;
                });

        Method setHandler = managerClass.getMethod("setUrlBlockHandler", handlerClass);
        setHandler.invoke(null, handler);
    }
}

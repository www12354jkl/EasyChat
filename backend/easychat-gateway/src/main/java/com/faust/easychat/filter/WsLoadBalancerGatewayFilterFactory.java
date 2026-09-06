package com.faust.easychat.filter;

import java.net.URI;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultRequest;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestData;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class WsLoadBalancerGatewayFilterFactory
        extends AbstractGatewayFilterFactory<WsLoadBalancerGatewayFilterFactory.Config> {

    public static final String WS_PORT_METADATA = "ws-port";

    private static final String DEFAULT_WS_PORT = "8083";

    private final LoadBalancerClientFactory clientFactory;

    public WsLoadBalancerGatewayFilterFactory(LoadBalancerClientFactory clientFactory) {
        super(Config.class);
        this.clientFactory = clientFactory;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String serviceId = config.getServiceId();
            if (serviceId == null || serviceId.isBlank()) {
                return Mono.error(new IllegalArgumentException("WsLoadBalancer serviceId must not be empty"));
            }
            URI incomingUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            if (incomingUrl == null) {
                incomingUrl = exchange.getRequest().getURI();
            }
            URI requestUri = incomingUrl;
            return choose(serviceId, exchange).flatMap(response -> {
                if (response == null || !response.hasServer()) {
                    log.warn("No WS backend available for service {}", serviceId);
                    throw NotFoundException.create(true, "Unable to find instance for " + serviceId);
                }
                ServiceInstance instance = response.getServer();
                String wsPort = instance.getMetadata().getOrDefault(WS_PORT_METADATA, DEFAULT_WS_PORT);
                URI targetUrl = UriComponentsBuilder.newInstance()
                        .scheme("http")
                        .host(instance.getHost())
                        .port(Integer.parseInt(wsPort))
                        .path(requestUri.getRawPath())
                        .query(requestUri.getRawQuery())
                        .fragment(requestUri.getRawFragment())
                        .build(true)
                        .toUri();
                log.debug("WS route {} -> {} (ws-port {})", serviceId, targetUrl, wsPort);
                exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, targetUrl);
                return chain.filter(exchange);
            });
        };
    }

    private Mono<Response<ServiceInstance>> choose(String serviceId, ServerWebExchange exchange) {
        ReactiveLoadBalancer<ServiceInstance> loadBalancer = clientFactory.getInstance(serviceId);
        if (loadBalancer == null) {
            return Mono.error(new IllegalStateException("No load balancer configured for " + serviceId));
        }
        Request<RequestDataContext> request = new DefaultRequest<>(new RequestDataContext(buildRequestData(exchange)));
        return Mono.from(loadBalancer.choose(request));
    }

    private static RequestData buildRequestData(ServerWebExchange exchange) {
        return new RequestData(exchange.getRequest());
    }

    public static class Config {

        private String serviceId;

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }
    }
}

package com.faust.easychat.netty;


import org.springframework.stereotype.Component;

import com.faust.easychat.service.TokenBlacklistService;
import com.faust.easychat.util.JwtUtil;
import com.faust.easychat.ws.WsSessionRegistry;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.RequiredArgsConstructor;


@Component
@RequiredArgsConstructor
public class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final JwtUtil jwtUtil;

    private final TokenBlacklistService tokenBlacklistService;

    private final WsSessionManager sessionManager;

    private final WsMessageSender wsMessageSender;
    private final WsSessionRegistry sessionRegistry;

    @Override
    protected void initChannel(SocketChannel ch) {
        ch.pipeline()
            .addLast(new HttpServerCodec())
            .addLast(new ChunkedWriteHandler())
            .addLast(new HttpObjectAggregator(65536))
            .addLast(new AuthWsHandler(jwtUtil, tokenBlacklistService, sessionManager))
            .addLast(new WebSocketServerProtocolHandler("/ws", true))
            .addLast(new ChatWsHandler(wsMessageSender, sessionRegistry));
    }

}

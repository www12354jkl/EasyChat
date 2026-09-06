package com.faust.easychat.ws;

public interface WsPushRouter {

    void route(String toUserId, String type, Object payload);
}

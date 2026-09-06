package com.faust.easychat.api.dubbo;

/**
 * Dubbo 消费端 mock 类名（{@code mock = "fail:..."}）。
 */
public final class DubboFallbacks {

    private DubboFallbacks() {
    }

    public static final String USER_QUERY =
            "fail:com.faust.easychat.api.fallback.UserQueryServiceFallback";

    public static final String FRIEND_REQUEST =
            "fail:com.faust.easychat.api.fallback.FriendRequestServiceFallback";

    public static final String CONVERSATION_DELETE =
            "fail:com.faust.easychat.api.fallback.ConversationDeleteServiceFallback";
}

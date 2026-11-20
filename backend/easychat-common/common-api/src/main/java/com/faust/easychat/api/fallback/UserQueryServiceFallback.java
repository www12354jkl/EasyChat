package com.faust.easychat.api.fallback;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faust.easychat.api.user.UserQueryService;
import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.exception.AppException;

/**
 * user-service 不可用时的查询降级：统一抛出 503 {@link AppException#serviceUnavailable(String)}，
 * 避免关键校验（如发消息、建群、加好友会话校验）误将 RPC 不可用当成 404/400 业务错误返回。
 * 列表/展示/非关键通知补全调用方如需优雅降级，应显式捕获 503 异常后回退空集合或 null。
 */
public class UserQueryServiceFallback implements UserQueryService {

    private static final Logger log = LoggerFactory.getLogger(UserQueryServiceFallback.class);
    private static final String SERVICE_UNAVAILABLE_MESSAGE = "用户服务暂不可用";

    @Override
    public UserBriefDTO getUserById(String id) {
        log.warn("UserQueryService 降级: getUserById id={}", id);
        throw AppException.serviceUnavailable(SERVICE_UNAVAILABLE_MESSAGE);
    }

    @Override
    public List<UserBriefDTO> batchGetUsers(List<String> ids) {
        int count = ids == null ? 0 : ids.size();
        log.warn("UserQueryService 降级: batchGetUsers count={}", count);
        throw AppException.serviceUnavailable(SERVICE_UNAVAILABLE_MESSAGE);
    }

    @Override
    public boolean isFriend(String userId, String friendId) {
        log.warn("UserQueryService 降级: isFriend userId={}, friendId={}", userId, friendId);
        throw AppException.serviceUnavailable(SERVICE_UNAVAILABLE_MESSAGE);
    }
}

package com.faust.easychat.api.fallback;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.faust.easychat.api.user.UserQueryService;
import com.faust.easychat.dto.user.UserBriefDTO;

/**
 * user-service 不可用时的查询降级：返回空值，由调用方按 null/空列表处理。
 */
public class UserQueryServiceFallback implements UserQueryService {

    private static final Logger log = LoggerFactory.getLogger(UserQueryServiceFallback.class);

    @Override
    public UserBriefDTO getUserById(String id) {
        log.warn("UserQueryService 降级: getUserById id={}", id);
        return null;
    }

    @Override
    public List<UserBriefDTO> batchGetUsers(List<String> ids) {
        int count = ids == null ? 0 : ids.size();
        log.warn("UserQueryService 降级: batchGetUsers count={}", count);
        return List.of();
    }

    @Override
    public boolean isFriend(String userId, String friendId) {
        log.warn("UserQueryService 降级: isFriend userId={}, friendId={}", userId, friendId);
        return false;
    }
}

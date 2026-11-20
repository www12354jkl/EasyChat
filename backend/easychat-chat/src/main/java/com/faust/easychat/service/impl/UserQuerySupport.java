package com.faust.easychat.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.faust.easychat.api.user.UserQueryService;
import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.enums.ReturnCodeEnum;
import com.faust.easychat.exception.AppException;

import lombok.extern.slf4j.Slf4j;

/**
 * 仅用于列表展示与推送文案补全的非关键用户查询：当 user-service 触发 503 降级时回退为空值，
 * 避免阻塞会话列表、历史消息、入群申请展示或消息撤回/邀请响应主流程。
 * 关键准入校验（如发消息、建群、加群成员、创建好友会话）须直接调用 {@link UserQueryService}，让 503 透传。
 */
@Slf4j
final class UserQuerySupport {

    private UserQuerySupport() {
    }

    static Map<String, UserBriefDTO> fetchUserMapSafely(UserQueryService userQueryService, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserBriefDTO> users;
        try {
            users = userQueryService.batchGetUsers(userIds);
        } catch (AppException e) {
            if (e.getReturnCode() == ReturnCodeEnum.SERVICE_UNAVAILABLE) {
                log.warn("批量查询用户信息降级: count={}, reason={}", userIds.size(), e.getMessage());
                return Map.of();
            }
            throw e;
        }
        if (users == null) {
            return Map.of();
        }
        return users.stream()
                .collect(Collectors.toMap(UserBriefDTO::getUserId, u -> u, (x, y) -> x));
    }

    static UserBriefDTO findUserOptional(UserQueryService userQueryService, String userId) {
        if (userId == null) {
            return null;
        }
        try {
            return userQueryService.getUserById(userId);
        } catch (AppException e) {
            if (e.getReturnCode() == ReturnCodeEnum.SERVICE_UNAVAILABLE) {
                log.warn("查询用户信息降级: userId={}, reason={}", userId, e.getMessage());
                return null;
            }
            throw e;
        }
    }
}

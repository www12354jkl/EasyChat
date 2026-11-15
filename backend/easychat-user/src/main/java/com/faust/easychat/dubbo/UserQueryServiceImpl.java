package com.faust.easychat.dubbo;

import java.util.List;

import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.util.StringUtils;

import com.faust.easychat.api.user.UserQueryService;
import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.mapper.FriendMapper;
import com.faust.easychat.service.UserService;

import lombok.RequiredArgsConstructor;

@DubboService
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {

    private final UserService userService;
    private final FriendMapper friendMapper;

    @Override
    public UserBriefDTO getUserById(String id) {
        return userService.getUserBrief(id);
    }

    @Override
    public List<UserBriefDTO> batchGetUsers(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String joined = ids.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        if (!StringUtils.hasText(joined)) {
            return List.of();
        }
        return userService.batchGetUsers(joined);
    }

    @Override
    public boolean isFriend(String userId, String friendId) {
        return friendMapper.selectByUserIdAndFriendUserId(userId, friendId) != null;
    }
}

package com.faust.easychat.api.user;

import java.util.List;

import com.faust.easychat.dto.user.UserBriefDTO;

/**
 * 用户查询 RPC（chat 等服务消费）。
 */
public interface UserQueryService {

    UserBriefDTO getUserById(String id);

    List<UserBriefDTO> batchGetUsers(List<String> ids);

    boolean isFriend(String userId, String friendId);
}

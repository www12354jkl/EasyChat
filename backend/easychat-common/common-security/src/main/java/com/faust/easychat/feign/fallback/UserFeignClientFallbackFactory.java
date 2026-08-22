package com.faust.easychat.feign.fallback;

import java.util.List;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.feign.UserFeignClient;
import com.faust.easychat.result.Result;

@Component
public class UserFeignClientFallbackFactory implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        return new UserFeignClient() {

            @Override
            public Result<UserBriefDTO> getUserById(String id) {
                return Result.error(500, "服务器内部错误");
            }

            @Override
            public Result<List<UserBriefDTO>> batchGetUsers(String ids) {
                return Result.error(500, "服务器内部错误");
            }

            @Override
            public Result<Boolean> isFriend(String userId, String friendId) {
                return Result.error(500, "服务器内部错误");
            }
            
        };
    }

}

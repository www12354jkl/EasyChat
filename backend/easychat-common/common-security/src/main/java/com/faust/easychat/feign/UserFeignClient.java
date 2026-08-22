package com.faust.easychat.feign;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.feign.fallback.UserFeignClientFallbackFactory;
import com.faust.easychat.result.Result;

@FeignClient(value = "easychat-user-service", fallbackFactory = UserFeignClientFallbackFactory.class)
public interface UserFeignClient {
    
    @GetMapping("/api/internal/user/{id}")
    public Result<UserBriefDTO> getUserById(@PathVariable String id);

    @GetMapping("/api/internal/users/batch")
    public Result<List<UserBriefDTO>> batchGetUsers(@RequestParam("ids") String ids);

    @GetMapping("/api/internal/friend/friend-confirm")
    public Result<Boolean> isFriend(@RequestParam("userId") String userId,@RequestParam("friendId") String friendId);

}

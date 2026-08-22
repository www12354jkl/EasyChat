package com.faust.easychat.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.faust.easychat.context.UserContext;
import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.dto.user.friend.AddFriendRequest;
import com.faust.easychat.dto.user.friend.FriendDTO;
import com.faust.easychat.dto.user.friend.FriendRequestDTO;
import com.faust.easychat.dto.user.friend.FriendRequestHandledDTO;
import com.faust.easychat.dto.user.friend.HandleRequestReq;
import com.faust.easychat.entity.Friend;
import com.faust.easychat.entity.FriendRequest;
import com.faust.easychat.entity.User;
import com.faust.easychat.entity.UserSettings;
import com.faust.easychat.exception.AppException;
import com.faust.easychat.feign.ChatFeignClient;
import com.faust.easychat.mapper.FriendMapper;
import com.faust.easychat.mapper.FriendRequestMapper;
import com.faust.easychat.mapper.UserMapper;
import com.faust.easychat.mapper.UserSettingsMapper;
import com.faust.easychat.service.FriendService;

import com.faust.easychat.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    private final UserMapper userMapper;

    private final FriendMapper friendMapper;

    private final UserSettingsMapper userSettingsMapper;

    private final FriendRequestMapper friendRequestMapper;

    private final ChatFeignClient chatFeignClient;

    @Override
    public List<UserBriefDTO> searchUser(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String currentUserId = UserContext.get();
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.like(User::getNickname, keyword).or().like(User::getEmail, keyword));
        wrapper.ne(User::getId, currentUserId);
        return userMapper.selectList(wrapper).stream().map(user -> {
            UserBriefDTO dto = new UserBriefDTO();
            dto.setUserId(user.getId());
            dto.setNickname(user.getNickname());
            dto.setAvatar(user.getAvatar());
            dto.setSignature(user.getSignature());
            return dto;
        }).toList();
    }

    @Override
    @Transactional
    public String addFriend(AddFriendRequest request) {
        String currentUserId = UserContext.get();
        String toUserId = request.getToUserId();
        if (toUserId == null || userMapper.selectById(toUserId) == null) {
            throw AppException.userNotFound();
        }
        if (toUserId.equals(currentUserId)) {
            throw AppException.badRequest("不能添加自己为好友");
        }
        if (isFriend(currentUserId, toUserId)) {
            throw AppException.badRequest("你们已经是好友了");
        }
        // 已有待处理申请（防止重复申请撞 UNIQUE）
        Long pending = friendRequestMapper.selectCount(new LambdaQueryWrapper<FriendRequest>()
                .eq(FriendRequest::getFromUserId, currentUserId)
                .eq(FriendRequest::getToUserId, toUserId)
                .eq(FriendRequest::getStatus, 0));
        if (pending != null && pending > 0) {
            throw AppException.badRequest("已发送过申请，请等待对方处理");
        }
        // 对方设置：0 无需验证直接加好友；无设置记录按需要验证处理
        UserSettings settings = userSettingsMapper.selectOne(new LambdaQueryWrapper<UserSettings>()
                .eq(UserSettings::getUserId, toUserId));
        int friendVerify = (settings != null && settings.getFriendVerify() != null)
                ? settings.getFriendVerify() : 1;
        if (friendVerify == 0) {
            insertFriendPair(currentUserId, toUserId);
            return "added";
        }
        FriendRequest friendRequest = new FriendRequest();
        friendRequest.setFromUserId(currentUserId);
        friendRequest.setToUserId(toUserId);
        friendRequest.setMessage(request.getMessage());
        friendRequest.setStatus(0);
        friendRequest.setCreatedAt(LocalDateTime.now());
        friendRequest.setUpdatedAt(LocalDateTime.now());
        friendRequestMapper.insert(friendRequest);
        pushFriendRequestNotify(friendRequest);
        return "pending";
    }

    private void pushFriendRequestNotify(FriendRequest friendRequest) {
        User fromUser = userMapper.selectById(friendRequest.getFromUserId());
        if (fromUser == null) {
            return;
        }
        FriendRequestDTO dto = new FriendRequestDTO();
        dto.setId(friendRequest.getId());
        dto.setFromUserId(fromUser.getId());
        dto.setFromName(fromUser.getNickname());
        dto.setFromAvatar(fromUser.getAvatar());
        dto.setMessage(friendRequest.getMessage());
        dto.setStatus(friendRequest.getStatus());
        dto.setCreatedAt(friendRequest.getCreatedAt());
        Result<Void> result = chatFeignClient.pushFriendRequest(friendRequest.getToUserId(), dto);
        if (result == null || result.getCode() != 200) {
            log.warn("好友申请 WS 推送失败: toUserId={}, requestId={}", friendRequest.getToUserId(),
                    friendRequest.getId());
        }
    }

    @Override
    public List<FriendRequestDTO> myRequests() {
        String currentUserId = UserContext.get();
        return friendRequestMapper.selectPendingByToUserId(currentUserId).stream().map(friendRequest -> {
            User user = userMapper.selectById(friendRequest.getFromUserId());
            if (user == null) {
                throw AppException.userNotFound();
            }
            FriendRequestDTO dto = new FriendRequestDTO();
            dto.setId(friendRequest.getId());
            dto.setFromUserId(user.getId());
            dto.setFromName(user.getNickname());
            dto.setFromAvatar(user.getAvatar());
            dto.setMessage(friendRequest.getMessage());
            dto.setStatus(friendRequest.getStatus());
            dto.setCreatedAt(friendRequest.getCreatedAt());
            return dto;
        }).toList();
    }

    @Override
    @Transactional
    public void handleRequest(String id, HandleRequestReq req) {
        if (req.getStatus() == null || (req.getStatus() != 1 && req.getStatus() != 2)) {
            throw AppException.badRequest("status 必须为 1（同意）或 2（拒绝）");
        }
        FriendRequest friendRequest = friendRequestMapper.selectById(id);
        String currentUserId = UserContext.get();
        if (friendRequest == null) {
            throw AppException.badRequest("申请不存在");
        }
        if (!friendRequest.getToUserId().equals(currentUserId)) {
            throw AppException.badRequest("只有当前用户可以操作");
        }
        if (friendRequest.getStatus() != null && friendRequest.getStatus() != 0) {
            throw AppException.badRequest("该申请已处理");
        }
        if (req.getStatus() == 1 && !isFriend(currentUserId, friendRequest.getFromUserId())) {
            insertFriendPair(currentUserId, friendRequest.getFromUserId());
        }
        friendRequest.setStatus(req.getStatus());
        friendRequestMapper.updateById(friendRequest);
        pushFriendRequestHandledNotify(friendRequest, currentUserId);
    }

    private void pushFriendRequestHandledNotify(FriendRequest friendRequest, String handlerUserId) {
        User handler = userMapper.selectById(handlerUserId);
        if (handler == null) {
            return;
        }
        FriendRequestHandledDTO dto = new FriendRequestHandledDTO();
        dto.setId(friendRequest.getId());
        dto.setStatus(friendRequest.getStatus());
        dto.setUserId(handler.getId());
        dto.setNickname(handler.getNickname());
        dto.setAvatar(handler.getAvatar());
        Result<Void> result = chatFeignClient.pushFriendRequestHandled(friendRequest.getFromUserId(), dto);
        if (result == null || result.getCode() != 200) {
            log.warn("好友申请处理 WS 推送失败: fromUserId={}, requestId={}", friendRequest.getFromUserId(),
                    friendRequest.getId());
        }
    }

    @Override
    public List<FriendDTO> getFriendList() {
        String currentUserId = UserContext.get();
        return friendMapper.listFriends(currentUserId).stream().map(friend -> {
            FriendDTO dto = new FriendDTO();
            dto.setUserId(friend.getId());
            dto.setNickname(friend.getNickname());
            dto.setSignature(friend.getSignature());
            dto.setAvatar(friend.getAvatar());
            return dto;
        }).toList();
    }

    @Override
    @Transactional
    public void deleteFriend(String id) {
        String currentUserId = UserContext.get();
        if (userMapper.selectById(id) == null) {
            throw AppException.userNotFound();
        }
        friendMapper.deleteByUserIdAndFriendUserId(currentUserId, id);
        friendMapper.deleteByUserIdAndFriendUserId(id, currentUserId);

        Result<Void> chatResult = chatFeignClient.removeDirectConversation(currentUserId, id);
        if (chatResult == null || chatResult.getCode() != 200) {
            log.warn("删好友后会话清理失败: userId={}, friendId={}", currentUserId, id);
        }
    }

    /** 双向各插一行，调用方负责事务 */
    private void insertFriendPair(String a, String b) {
        Friend f1 = new Friend();
        f1.setUserId(a);
        f1.setFriendId(b);
        f1.setCreatedAt(LocalDateTime.now());
        friendMapper.insert(f1);
        Friend f2 = new Friend();
        f2.setUserId(b);
        f2.setFriendId(a);
        f2.setCreatedAt(LocalDateTime.now());
        friendMapper.insert(f2);
    }

    /** 双向任一方向存在即算好友 */
    private boolean isFriend(String a, String b) {
        Long count = friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                .and(w -> w.eq(Friend::getUserId, a).eq(Friend::getFriendId, b)
                        .or()
                        .eq(Friend::getUserId, b).eq(Friend::getFriendId, a)));
        return count != null && count > 0;
    }
}

package com.faust.easychat.service.impl;

import com.faust.easychat.netty.WsMessageSender;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.faust.easychat.context.UserContext;
import com.faust.easychat.dto.chat.ConversationResponse;
import com.faust.easychat.dto.chat.MessageDTO;
import com.faust.easychat.dto.chat.SendMessageRequest;
import com.faust.easychat.dto.chat.SingleConversationRequest;
import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.entity.Conversation;
import com.faust.easychat.entity.ConversationMember;
import com.faust.easychat.entity.Message;
import com.faust.easychat.exception.AppException;
import com.faust.easychat.feign.UserFeignClient;
import com.faust.easychat.mapper.ConversationMapper;
import com.faust.easychat.mapper.ConversationMemberMapper;
import com.faust.easychat.mapper.MessageMapper;
import com.faust.easychat.result.PageResult;
import com.faust.easychat.service.MessageService;
import com.faust.easychat.util.CosUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final WsMessageSender wsMessageSender;

    private final ConversationMapper conversationMapper;

    private final ConversationMemberMapper conversationMemberMapper;

    private final MessageMapper messageMapper;

    private final UserFeignClient userFeignClient;

    private final CosUtil cosUtil;

    @Override
    @Transactional
    public MessageDTO sendMessage(SendMessageRequest request) {
        Conversation conversation = conversationMapper.selectById(request.getConversationId());
        if (conversation == null) {
            throw AppException.conversationNotFound();
        }
        if (conversation.getType() != null && conversation.getType() == 3) {
            throw AppException.badRequest("该会话不支持发送消息");
        }
        UserBriefDTO sender = userFeignClient.getUserById(UserContext.get()).getData();
        if (sender == null) {
            throw AppException.userNotFound();
        }
        ConversationMember me = conversationMemberMapper.selectByConversationAndUser(request.getConversationId(),
                sender.getUserId());
        if (me == null) {
            throw AppException.forbidden("你不是该会话成员");
        }

        Integer contentType = request.getContentType() == null ? 1 : request.getContentType();
        if (contentType < 1 || contentType > 3) {
            throw AppException.badRequest("不支持的消息类型");
        }

        String content = request.getContent();
        if (content == null || content.isBlank()) {
            throw AppException.badRequest("消息内容不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        Message message = new Message();
        message.setConversationId(request.getConversationId());
        message.setContent(request.getContent());
        message.setContentType(contentType);
        message.setSenderId(sender.getUserId());
        message.setCreatedAt(now);
        messageMapper.insert(message);

        String lastMessage = switch (contentType) {
            case 2 -> "[图片]";
            case 3 -> "[文件]";
            default -> content;
        };
        conversation.setLastMessage(lastMessage);
        conversation.setLastMessageAt(now);
        conversationMapper.updateById(conversation);

        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setContent(message.getContent());
        dto.setContentType(message.getContentType());
        dto.setConversationId(message.getConversationId());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setSenderId(sender.getUserId());
        dto.setSenderName(sender.getNickname());
        dto.setSenderAvatar(sender.getAvatar());

        List<ConversationMember> members = conversationMemberMapper
                .selectConversationAllMembers(request.getConversationId());

        for (ConversationMember member : members) {
            if (!member.getUserId().equals(sender.getUserId())) {
                wsMessageSender.sendToUser(member.getUserId(), "NEW_MESSAGE", dto);
            }
        }

        return dto;
    }

    @Override
    public PageResult<MessageDTO> getChatHistory(String conversationId, String title, int page, int size) {
        ConversationMember me = conversationMemberMapper.selectByConversationAndUser(conversationId, UserContext.get());
        if (me == null) {
            throw AppException.forbidden("你不是该会话成员");
        }
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .like(StringUtils.hasText(title), Message::getContent, title)
                .orderByDesc(Message::getCreatedAt);
        Page<Message> mpPage = new Page<>(page, size);
        Page<Message> result = messageMapper.selectPage(mpPage, wrapper);

        // 批量查发送者昵称/头像，避免 N+1
        List<Message> records = result.getRecords();
        List<String> senderIds = records.stream().map(Message::getSenderId).distinct().toList();
        Map<String, UserBriefDTO> userMap = fetchUserMap(senderIds);

        List<MessageDTO> dtos = records.stream().map(m -> {
            MessageDTO dto = new MessageDTO();
            dto.setId(m.getId());
            dto.setConversationId(m.getConversationId());
            dto.setSenderId(m.getSenderId());
            UserBriefDTO sender = userMap.get(m.getSenderId());
            dto.setSenderName(sender != null ? sender.getNickname() : null);
            dto.setSenderAvatar(sender != null ? sender.getAvatar() : null);
            dto.setContentType(m.getContentType());
            dto.setContent(m.getContent());
            dto.setCreatedAt(m.getCreatedAt());
            return dto;
        }).toList();
        return PageResult.of(dtos, result.getTotal(), page, size);
    }

    @Override
    public List<ConversationResponse> getChatList() {
        String userId = UserContext.get();
        List<Conversation> conversations = conversationMapper.selectMyConversations(userId);
        if (conversations.isEmpty()) {
            return List.of();
        }

        List<String> ids = conversations.stream().map(Conversation::getId).toList();
        Map<String, Integer> totalMap = conversationMemberMapper.countByConversationIds(ids).stream()
                .collect(Collectors.toMap(m -> (String) m.get("conversation_id"),
                        m -> ((Number) m.get("total")).intValue(), (x, y) -> x));

        List<ConversationMember> members = conversationMemberMapper.selectMemberByConversationIds(ids);
        Map<String, String> friendMap = members.stream()
                .filter(m -> !m.getUserId().equals(userId)) // 排除自己 = 对方
                .collect(Collectors.toMap(ConversationMember::getConversationId,
                        ConversationMember::getUserId, (x, y) -> x));

        Map<String, Integer> unreadMap = conversationMemberMapper.countUnreadByConversationIds(userId, ids).stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("conversation_id"),
                        m -> ((Number) m.get("unread")).intValue(),
                        (x, y) -> x));

        // 单聊对方实时昵称/头像
        List<String> friendIds = friendMap.values().stream().distinct().toList();
        Map<String, UserBriefDTO> userMap = fetchUserMap(friendIds);

        return conversations.stream()
                .map(c -> buildConversationResponse(c, userId, friendMap, userMap, totalMap, unreadMap))
                .filter(Objects::nonNull)
                .toList();
    }

    private ConversationResponse buildConversationResponse(Conversation c, String userId,
            Map<String, String> friendMap, Map<String, UserBriefDTO> userMap,
            Map<String, Integer> totalMap, Map<String, Integer> unreadMap) {
        if (c.getType() != null && c.getType() == 3) {
            return null;
        }

        ConversationResponse r = new ConversationResponse();
        r.setId(c.getId());
        r.setType(c.getType());
        r.setIsGroup(c.getType() == 2);
        r.setIsGroupInvite(false);
        if (c.getType() == 1) {
            String friendId = friendMap.get(c.getId());
            UserBriefDTO friend = userMap.get(friendId);
            r.setFriendId(friendId);
            r.setName(friend != null ? friend.getNickname() : c.getName());
            r.setAvatar(friend != null ? friend.getAvatar() : c.getAvatar());
        } else {
            r.setName(c.getName());
            r.setAvatar(c.getAvatar());
        }
        r.setLastMessage(c.getLastMessage());
        r.setLastMessageAt(c.getLastMessageAt());
        r.setUnread(unreadMap.getOrDefault(c.getId(), 0));
        r.setTotal(totalMap.getOrDefault(c.getId(), 0));
        return r;
    }

    /** 批量查用户信息（判空防 Feign fallback），key=userId */
    private Map<String, UserBriefDTO> fetchUserMap(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserBriefDTO> users = userFeignClient.batchGetUsers(String.join(",", userIds)).getData();
        if (users == null) {
            return Map.of();
        }
        return users.stream()
                .collect(Collectors.toMap(UserBriefDTO::getUserId, u -> u, (x, y) -> x));
    }

    @Override
    @Transactional
    public ConversationResponse createFriendConversation(SingleConversationRequest request) {
        String currentUserId = UserContext.get();
        String friendId = request.getToUserId();
        if (friendId == null || friendId.isBlank() || friendId.equals(currentUserId)) {
            throw AppException.badRequest("对象不能为空或是自己");
        }
        Boolean isFriend = userFeignClient.isFriend(currentUserId, friendId).getData();
        if (isFriend == null || !isFriend) {
            throw AppException.badRequest("只能和好友创建会话");
        }
        String conversationId = currentUserId.compareTo(friendId) < 0
                ? currentUserId + "_" + friendId
                : friendId + "_" + currentUserId;
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            LocalDateTime now = LocalDateTime.now();
            conversation = new Conversation();
            conversation.setId(conversationId);
            conversation.setType(1);
            conversation.setStatus(1);
            conversation.setCreatedAt(now);
            conversation.setUpdatedAt(now);
            conversationMapper.insert(conversation);
            // 双向插成员：发消息/查历史都依赖 member 校验，缺了会报"你不是该会话成员"
            conversationMemberMapper.batchInsert(conversationId, List.of(currentUserId, friendId), 1, now);
        }
        UserBriefDTO friendUser = userFeignClient.getUserById(friendId).getData();
        return buildSingleConversationResponse(conversation, friendUser);
    }

    @Override
    public String upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw AppException.badRequest("请选择文件");
        }
        if (file.getSize() > 20 * 1024 * 1024) {
            throw AppException.badRequest("文件大小不能超过20MB");
        }
        String original = file.getOriginalFilename();
        String suffix = "";
        if (original != null && original.contains(".")) {
            suffix = original.substring(original.lastIndexOf("."));
        }
        String path = "easychat/chat-files/" + UUID.randomUUID() + suffix;
        String url = cosUtil.upload(file, path);
        return url;
    }

    private ConversationResponse buildSingleConversationResponse(Conversation conversation, UserBriefDTO friend) {
        ConversationResponse response = new ConversationResponse();
        response.setId(conversation.getId());
        response.setName(friend != null ? friend.getNickname() : conversation.getName());
        response.setAvatar(friend != null ? friend.getAvatar() : conversation.getAvatar());
        response.setType(1);
        response.setIsGroup(false);
        response.setFriendId(friend != null ? friend.getUserId() : null);
        response.setLastMessage(conversation.getLastMessage());
        response.setLastMessageAt(conversation.getLastMessageAt());
        response.setUnread(0);
        response.setTotal(2);
        return response;
    }

    @Override
    @Transactional
    public Integer deleteChatHistory(String conversationId) {
        String currentUserId = UserContext.get();
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw AppException.conversationNotFound();
        }
        if (conversationMemberMapper.selectByConversationAndUser(conversationId, currentUserId) == null) {
            throw AppException.forbidden("你不是该会话成员");
        }
        int deleted = messageMapper.deleteByConversationId(conversationId);

        conversation.setLastMessage(null);
        conversation.setLastMessageAt(null);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);

        return Integer.valueOf(deleted);
    }

    @Override
    public void markRead(String conversationId) {
        String currentUserId = UserContext.get();
        if (conversationMapper.selectById(conversationId) == null) {
            throw AppException.conversationNotFound();
        }
        if (conversationMemberMapper.selectByConversationAndUser(conversationId, currentUserId) == null) {
            throw AppException.forbidden("你不是该会话成员");
        }
        conversationMemberMapper.markRead(conversationId, currentUserId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void removeDirectConversation(String userId, String friendId) {
        if (userId == null || friendId == null || userId.equals(friendId)) {
            return;
        }
        String conversationId = userId.compareTo(friendId) < 0
                ? userId + "_" + friendId
                : friendId + "_" + userId;
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || conversation.getType() == null || conversation.getType() != 1) {
            return;
        }
        messageMapper.deleteByConversationId(conversationId);
        conversationMemberMapper.deleteByConversationAndUser(conversationId, userId);
        conversationMemberMapper.deleteByConversationAndUser(conversationId, friendId);
        conversationMapper.deleteById(conversationId);

        Map<String, String> payload = Map.of("id", conversationId);
        wsMessageSender.sendToUser(userId, "CONVERSATION_REMOVED", payload);
        wsMessageSender.sendToUser(friendId, "CONVERSATION_REMOVED", payload);
    }
}

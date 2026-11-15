package com.faust.easychat.service.impl;

import com.faust.easychat.netty.WsMessageSender;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.dao.DuplicateKeyException;
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
import com.faust.easychat.api.dubbo.DubboFallbacks;
import com.faust.easychat.api.user.UserQueryService;
import com.faust.easychat.exception.AppException;
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

    private static final int STATUS_NORMAL = 1;
    private static final int STATUS_RECALLED = 2;
    private static final Duration RECALL_WINDOW = Duration.ofMinutes(2);
    private static final String RECALLED_SUMMARY = "[已撤回]";
    private static final int LAST_MESSAGE_MAX = 500;
    private static final Set<String> CHAT_FILE_SUFFIXES = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".pdf", ".txt", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".zip");

    private final WsMessageSender wsMessageSender;

    private final ConversationMapper conversationMapper;

    private final ConversationMemberMapper conversationMemberMapper;

    private final MessageMapper messageMapper;

    @DubboReference(mock = DubboFallbacks.USER_QUERY, cluster = "failfast", retries = 0)
    private UserQueryService userQueryService;

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
        UserBriefDTO sender = userQueryService.getUserById(UserContext.get());
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
        String clientMsgId = normalizeClientMsgId(request.getClientMsgId());
        if (clientMsgId != null) {
            Message existing = findByClientMsgId(sender.getUserId(), clientMsgId);
            if (existing != null) {
                return toMessageDto(existing, sender);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Message message = new Message();
        message.setConversationId(request.getConversationId());
        message.setContent(request.getContent());
        message.setContentType(contentType);
        message.setSenderId(sender.getUserId());
        message.setClientMsgId(clientMsgId);
        message.setCreatedAt(now);
        message.setStatus(STATUS_NORMAL);
        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException e) {
            Message existing = clientMsgId == null ? null : findByClientMsgId(sender.getUserId(), clientMsgId);
            if (existing == null) {
                throw e;
            }
            return toMessageDto(existing, sender);
        }

        String lastMessage = summarizeLastMessage(contentType, content);
        conversationMapper.updateLastMessageIfNotNewer(conversation.getId(), lastMessage, now, now);

        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setContent(message.getContent());
        dto.setContentType(message.getContentType());
        dto.setConversationId(message.getConversationId());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setSenderId(sender.getUserId());
        dto.setSenderName(sender.getNickname());
        dto.setSenderAvatar(sender.getAvatar());
        dto.setStatus(STATUS_NORMAL);

        List<ConversationMember> members = conversationMemberMapper
                .selectConversationAllMembers(request.getConversationId());

        for (ConversationMember member : members) {
            if (!member.getUserId().equals(sender.getUserId())) {
                pushSafely(member.getUserId(), "NEW_MESSAGE", dto, message.getId());
            }
        }

        return dto;
    }

    @Override
    @Transactional
    public MessageDTO recallMessage(String messageId) {
        String userId = UserContext.get();
        Message message = messageMapper.selectById(messageId);
        if (message == null) {
            throw AppException.notFound("消息不存在");
        }
        if (!userId.equals(message.getSenderId())) {
            throw AppException.forbidden("只能撤回自己发送的消息");
        }
        if (Integer.valueOf(STATUS_RECALLED).equals(message.getStatus())) {
            throw AppException.badRequest("消息已撤回");
        }
        Integer contentType = message.getContentType();
        if (contentType == null || contentType < 1 || contentType > 3) {
            throw AppException.badRequest("该类型消息不支持撤回");
        }
        if (message.getCreatedAt().plus(RECALL_WINDOW).isBefore(LocalDateTime.now())) {
            throw AppException.badRequest("超过2分钟，无法撤回");
        }
        if (conversationMemberMapper.selectByConversationAndUser(message.getConversationId(), userId) == null) {
            throw AppException.forbidden("你不是该会话成员");
        }

        message.setStatus(STATUS_RECALLED);
        messageMapper.updateById(message);
        updateConversationLastMessageIfNeeded(message);

        UserBriefDTO sender = userQueryService.getUserById(message.getSenderId());
        MessageDTO dto = toMessageDto(message, sender);

        List<ConversationMember> members = conversationMemberMapper
                .selectConversationAllMembers(message.getConversationId());
        for (ConversationMember member : members) {
            pushSafely(member.getUserId(), "MESSAGE_UPDATE", dto, message.getId());
        }
        return dto;
    }

    @Override
    public PageResult<MessageDTO> getChatHistory(String conversationId, String title, int page, int size) {
        ConversationMember me = conversationMemberMapper.selectByConversationAndUser(conversationId, UserContext.get());
        if (me == null) {
            throw AppException.forbidden("你不是该会话成员");
        }
        page = Math.max(page, 1);
        size = clampPageSize(size);
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, conversationId)
                .gt(me.getClearedAt() != null, Message::getCreatedAt, me.getClearedAt())
                .like(StringUtils.hasText(title), Message::getContent, title)
                .orderByDesc(Message::getCreatedAt);
        Page<Message> mpPage = new Page<>(page, size);
        Page<Message> result = messageMapper.selectPage(mpPage, wrapper);

        // 批量查发送者昵称/头像，避免 N+1
        List<Message> records = result.getRecords();
        List<String> senderIds = records.stream().map(Message::getSenderId).distinct().toList();
        Map<String, UserBriefDTO> userMap = fetchUserMap(senderIds);

        List<MessageDTO> dtos = records.stream()
                .map(m -> toMessageDto(m, userMap.get(m.getSenderId())))
                .toList();
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

        Map<String, LocalDateTime> clearedMap = members.stream()
                .filter(m -> userId.equals(m.getUserId()) && m.getClearedAt() != null)
                .collect(Collectors.toMap(ConversationMember::getConversationId, ConversationMember::getClearedAt,
                        (x, y) -> x));

        // 单聊对方实时昵称/头像
        List<String> friendIds = friendMap.values().stream().distinct().toList();
        Map<String, UserBriefDTO> userMap = fetchUserMap(friendIds);

        return conversations.stream()
                .map(c -> buildConversationResponse(c, userId, friendMap, userMap, totalMap, unreadMap, clearedMap))
                .filter(Objects::nonNull)
                .toList();
    }

    private ConversationResponse buildConversationResponse(Conversation c, String userId,
            Map<String, String> friendMap, Map<String, UserBriefDTO> userMap,
            Map<String, Integer> totalMap, Map<String, Integer> unreadMap,
            Map<String, LocalDateTime> clearedMap) {
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
        LocalDateTime clearedAt = clearedMap.get(c.getId());
        if (clearedAt != null && (c.getLastMessageAt() == null || !c.getLastMessageAt().isAfter(clearedAt))) {
            r.setLastMessage(null);
            r.setLastMessageAt(null);
        } else {
            r.setLastMessage(c.getLastMessage());
            r.setLastMessageAt(c.getLastMessageAt());
        }
        r.setUnread(unreadMap.getOrDefault(c.getId(), 0));
        r.setTotal(totalMap.getOrDefault(c.getId(), 0));
        return r;
    }

    /** 批量查用户信息，key=userId */
    private Map<String, UserBriefDTO> fetchUserMap(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserBriefDTO> users = userQueryService.batchGetUsers(userIds);
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
        if (!userQueryService.isFriend(currentUserId, friendId)) {
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
        UserBriefDTO friendUser = userQueryService.getUserById(friendId);
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
        String suffix = chatFileSuffix(original);
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
        // 只改当前用户的可见窗口，不删 message 表，对方记录不受影响
        LocalDateTime now = LocalDateTime.now();
        conversationMemberMapper.markCleared(conversationId, currentUserId, now);
        conversationMemberMapper.markRead(conversationId, currentUserId, now);
        return 0;
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

    private void updateConversationLastMessageIfNeeded(Message message) {
        conversationMapper.updateLastMessageIfNotNewer(
                message.getConversationId(),
                RECALLED_SUMMARY,
                message.getCreatedAt(),
                LocalDateTime.now());
    }

    static int clampPageSize(int size) {
        if (size < 1) {
            return 30;
        }
        return Math.min(size, 100);
    }

    static String chatFileSuffix(String original) {
        if (original == null || !original.contains(".")) {
            throw AppException.badRequest("不支持的文件类型");
        }
        String suffix = original.substring(original.lastIndexOf('.')).toLowerCase();
        if (!CHAT_FILE_SUFFIXES.contains(suffix)) {
            throw AppException.badRequest("不支持的文件类型");
        }
        return suffix;
    }

    static String summarizeLastMessage(Integer contentType, String content) {
        int type = contentType == null ? 1 : contentType;
        return switch (type) {
            case 2 -> "[图片]";
            case 3 -> "[文件]";
            default -> abbreviate(content, LAST_MESSAGE_MAX);
        };
    }

    private static String abbreviate(String content, int max) {
        if (content == null || content.length() <= max) {
            return content == null ? "" : content;
        }
        return content.substring(0, max);
    }

    private void pushSafely(String userId, String type, Object payload, String messageId) {
        try {
            wsMessageSender.sendToUser(userId, type, payload);
        } catch (RuntimeException e) {
            log.warn("消息已入库但推送失败 messageId={} toUserId={} type={}", messageId, userId, type, e);
        }
    }

    private Message findByClientMsgId(String senderId, String clientMsgId) {
        return messageMapper.selectOne(new LambdaQueryWrapper<Message>()
                .eq(Message::getSenderId, senderId)
                .eq(Message::getClientMsgId, clientMsgId)
                .last("LIMIT 1"));
    }

    private static String normalizeClientMsgId(String clientMsgId) {
        if (!StringUtils.hasText(clientMsgId)) {
            return null;
        }
        String trimmed = clientMsgId.trim();
        if (trimmed.length() > 64) {
            throw AppException.badRequest("客户端消息号过长");
        }
        return trimmed;
    }

    private static MessageDTO toMessageDto(Message message, UserBriefDTO sender) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setConversationId(message.getConversationId());
        dto.setSenderId(message.getSenderId());
        if (sender != null) {
            dto.setSenderName(sender.getNickname());
            dto.setSenderAvatar(sender.getAvatar());
        }
        dto.setContentType(message.getContentType());
        dto.setContent(message.getContent());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setStatus(message.getStatus() == null ? STATUS_NORMAL : message.getStatus());
        return dto;
    }
}

package com.faust.easychat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.faust.easychat.api.fallback.UserQueryServiceFallback;
import com.faust.easychat.api.user.UserQueryService;
import com.faust.easychat.context.UserContext;
import com.faust.easychat.dto.chat.ConversationResponse;
import com.faust.easychat.dto.chat.MessageDTO;
import com.faust.easychat.dto.chat.SendMessageRequest;
import com.faust.easychat.dto.chat.SingleConversationRequest;
import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.entity.Conversation;
import com.faust.easychat.entity.ConversationMember;
import com.faust.easychat.entity.Message;
import com.faust.easychat.enums.ReturnCodeEnum;
import com.faust.easychat.exception.AppException;
import com.faust.easychat.mapper.ConversationMapper;
import com.faust.easychat.mapper.ConversationMemberMapper;
import com.faust.easychat.mapper.MessageMapper;
import com.faust.easychat.netty.WsMessageSender;
import com.faust.easychat.result.PageResult;
import com.faust.easychat.util.CosUtil;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplRecallTest {

    private static final String USER_ID = "user-1";
    private static final String OTHER_ID = "user-2";
    private static final String CONV_ID = "conv-1";
    private static final String MSG_ID = "msg-1";

    @Mock
    private WsMessageSender wsMessageSender;
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private ConversationMemberMapper conversationMemberMapper;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private CosUtil cosUtil;

    @InjectMocks
    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        UserContext.set(USER_ID);
        ReflectionTestUtils.setField(messageService, "userQueryService", userQueryService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void recallMessage_notFound_throws() {
        when(messageMapper.selectById(MSG_ID)).thenReturn(null);

        assertThrows(AppException.class, () -> messageService.recallMessage(MSG_ID));
    }

    @Test
    void recallMessage_notSender_throws() {
        Message message = message(MSG_ID, OTHER_ID, 1, 1, LocalDateTime.now());
        when(messageMapper.selectById(MSG_ID)).thenReturn(message);

        assertThrows(AppException.class, () -> messageService.recallMessage(MSG_ID));
    }

    @Test
    void recallMessage_alreadyRecalled_throws() {
        Message message = message(MSG_ID, USER_ID, 2, 1, LocalDateTime.now());
        when(messageMapper.selectById(MSG_ID)).thenReturn(message);

        assertThrows(AppException.class, () -> messageService.recallMessage(MSG_ID));
    }

    @Test
    void recallMessage_inviteCard_throws() {
        Message message = message(MSG_ID, USER_ID, 1, 4, LocalDateTime.now());
        when(messageMapper.selectById(MSG_ID)).thenReturn(message);

        assertThrows(AppException.class, () -> messageService.recallMessage(MSG_ID));
    }

    @Test
    void recallMessage_expired_throws() {
        Message message = message(MSG_ID, USER_ID, 1, 1, LocalDateTime.now().minusMinutes(3));
        when(messageMapper.selectById(MSG_ID)).thenReturn(message);

        assertThrows(AppException.class, () -> messageService.recallMessage(MSG_ID));
    }

    @Test
    void recallMessage_notMember_throws() {
        Message message = message(MSG_ID, USER_ID, 1, 1, LocalDateTime.now());
        when(messageMapper.selectById(MSG_ID)).thenReturn(message);
        when(conversationMemberMapper.selectByConversationAndUser(CONV_ID, USER_ID)).thenReturn(null);

        assertThrows(AppException.class, () -> messageService.recallMessage(MSG_ID));
    }

    @Test
    void recallMessage_success_updatesStatusAndPushesWs() {
        Message message = message(MSG_ID, USER_ID, 1, 1, LocalDateTime.now());
        when(messageMapper.selectById(MSG_ID)).thenReturn(message);
        when(conversationMemberMapper.selectByConversationAndUser(CONV_ID, USER_ID)).thenReturn(member(USER_ID));

        UserBriefDTO sender = new UserBriefDTO();
        sender.setUserId(USER_ID);
        sender.setNickname("Alice");
        sender.setAvatar("a.png");
        when(userQueryService.getUserById(USER_ID)).thenReturn(sender);
        when(conversationMemberMapper.selectConversationAllMembers(CONV_ID))
                .thenReturn(List.of(member(USER_ID), member(OTHER_ID)));

        MessageDTO dto = messageService.recallMessage(MSG_ID);

        assertEquals(2, dto.getStatus());
        verify(messageMapper).updateById(message);
        assertEquals(2, message.getStatus());
        verify(conversationMapper).updateLastMessageIfNotNewer(
                eq(CONV_ID), eq("[已撤回]"), eq(message.getCreatedAt()), any(LocalDateTime.class));
        verify(wsMessageSender).sendToUser(eq(USER_ID), eq("MESSAGE_UPDATE"), any(MessageDTO.class));
        verify(wsMessageSender).sendToUser(eq(OTHER_ID), eq("MESSAGE_UPDATE"), any(MessageDTO.class));
    }

    @Test
    void recallMessage_passesRecalledTimeSoNewerSummaryWins() {
        LocalDateTime createdAt = LocalDateTime.now().minusSeconds(5);
        Message message = message(MSG_ID, USER_ID, 1, 1, createdAt);
        when(messageMapper.selectById(MSG_ID)).thenReturn(message);
        when(conversationMemberMapper.selectByConversationAndUser(CONV_ID, USER_ID)).thenReturn(member(USER_ID));

        UserBriefDTO sender = new UserBriefDTO();
        sender.setUserId(USER_ID);
        when(userQueryService.getUserById(USER_ID)).thenReturn(sender);
        when(conversationMemberMapper.selectConversationAllMembers(CONV_ID))
                .thenReturn(List.of(member(USER_ID)));

        messageService.recallMessage(MSG_ID);

        verify(conversationMapper).updateLastMessageIfNotNewer(
                eq(CONV_ID), eq("[已撤回]"), eq(createdAt), any(LocalDateTime.class));
    }

    @Test
    void summarizeLastMessage_longText_isTruncated() {
        String content = "a".repeat(600);
        String summary = MessageServiceImpl.summarizeLastMessage(1, content);
        assertEquals(500, summary.length());
    }

    @Test
    void summarizeLastMessage_image_usesLabel() {
        assertEquals("[图片]", MessageServiceImpl.summarizeLastMessage(2, "https://example.com/a.png"));
    }

    @Test
    void clampPageSize_capsLargeAndFixesNonPositive() {
        assertEquals(100, MessageServiceImpl.clampPageSize(10_000));
        assertEquals(30, MessageServiceImpl.clampPageSize(0));
        assertEquals(30, MessageServiceImpl.clampPageSize(-1));
    }

    @Test
    void sendMessage_sameClientMsgId_returnsExistingWithoutInsert() {
        Conversation conversation = new Conversation();
        conversation.setId(CONV_ID);
        conversation.setType(1);
        when(conversationMapper.selectById(CONV_ID)).thenReturn(conversation);
        UserBriefDTO user = new UserBriefDTO();
        user.setUserId(USER_ID);
        user.setNickname("n");
        when(userQueryService.getUserById(USER_ID)).thenReturn(user);
        when(conversationMemberMapper.selectByConversationAndUser(CONV_ID, USER_ID)).thenReturn(member(USER_ID));
        Message existing = message(MSG_ID, USER_ID, 1, 1, LocalDateTime.now());
        existing.setClientMsgId("c1");
        existing.setContent("hello");
        when(messageMapper.selectOne(any())).thenReturn(existing);

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(CONV_ID);
        request.setContent("hello");
        request.setContentType(1);
        request.setClientMsgId("c1");

        MessageDTO dto = messageService.sendMessage(request);

        assertEquals(MSG_ID, dto.getId());
        verify(messageMapper, never()).insert(any(Message.class));
    }

    @Test
    void sendMessage_pushFailure_keepsStoredMessage() {
        Conversation conversation = new Conversation();
        conversation.setId(CONV_ID);
        conversation.setType(1);
        when(conversationMapper.selectById(CONV_ID)).thenReturn(conversation);
        UserBriefDTO user = new UserBriefDTO();
        user.setUserId(USER_ID);
        user.setNickname("n");
        when(userQueryService.getUserById(USER_ID)).thenReturn(user);
        when(conversationMemberMapper.selectByConversationAndUser(CONV_ID, USER_ID)).thenReturn(member(USER_ID));
        when(conversationMemberMapper.selectConversationAllMembers(CONV_ID)).thenReturn(List.of(member(OTHER_ID)));
        doThrow(new RuntimeException("push down")).when(wsMessageSender)
                .sendToUser(eq(OTHER_ID), eq("NEW_MESSAGE"), any());

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(CONV_ID);
        request.setContent("hello");
        request.setContentType(1);

        MessageDTO dto = messageService.sendMessage(request);

        assertEquals("hello", dto.getContent());
        verify(messageMapper).insert(any(Message.class));
    }

    @Test
    void chatFileSuffix_rejectsHtmlAndExtensionless() {
        assertThrows(AppException.class, () -> MessageServiceImpl.chatFileSuffix("page.html"));
        assertThrows(AppException.class, () -> MessageServiceImpl.chatFileSuffix("noextension"));
        assertEquals(".png", MessageServiceImpl.chatFileSuffix("a.PNG"));
    }

    @Test
    void sendMessage_whenUserServiceFallbackTriggers_throwsServiceUnavailable() {
        Conversation conversation = new Conversation();
        conversation.setId(CONV_ID);
        conversation.setType(1);
        when(conversationMapper.selectById(CONV_ID)).thenReturn(conversation);
        ReflectionTestUtils.setField(messageService, "userQueryService", new UserQueryServiceFallback());

        SendMessageRequest request = new SendMessageRequest();
        request.setConversationId(CONV_ID);
        request.setContent("hello");
        request.setContentType(1);

        AppException ex = assertThrows(AppException.class, () -> messageService.sendMessage(request));

        assertEquals(ReturnCodeEnum.SERVICE_UNAVAILABLE, ex.getReturnCode());
        verify(messageMapper, never()).insert(any(Message.class));
    }

    @Test
    void createFriendConversation_whenUserServiceFallbackTriggers_throwsServiceUnavailable() {
        ReflectionTestUtils.setField(messageService, "userQueryService", new UserQueryServiceFallback());

        SingleConversationRequest request = new SingleConversationRequest();
        request.setToUserId(OTHER_ID);

        AppException ex = assertThrows(AppException.class, () -> messageService.createFriendConversation(request));

        assertEquals(ReturnCodeEnum.SERVICE_UNAVAILABLE, ex.getReturnCode());
    }

    @Test
    void recallMessage_whenSenderMockReturnsNull_stillSucceeds() {
        Message message = message(MSG_ID, USER_ID, 1, 1, LocalDateTime.now());
        when(messageMapper.selectById(MSG_ID)).thenReturn(message);
        when(conversationMemberMapper.selectByConversationAndUser(CONV_ID, USER_ID)).thenReturn(member(USER_ID));
        when(userQueryService.getUserById(USER_ID)).thenReturn(null);
        when(conversationMemberMapper.selectConversationAllMembers(CONV_ID)).thenReturn(List.of(member(USER_ID)));

        MessageDTO dto = messageService.recallMessage(MSG_ID);

        assertEquals(2, dto.getStatus());
        assertNull(dto.getSenderName());
        verify(wsMessageSender).sendToUser(eq(USER_ID), eq("MESSAGE_UPDATE"), any(MessageDTO.class));
    }

    @Test
    void recallMessage_whenUserServiceFallbackTriggers_degradesGracefully() {
        Message message = message(MSG_ID, USER_ID, 1, 1, LocalDateTime.now());
        when(messageMapper.selectById(MSG_ID)).thenReturn(message);
        when(conversationMemberMapper.selectByConversationAndUser(CONV_ID, USER_ID)).thenReturn(member(USER_ID));
        ReflectionTestUtils.setField(messageService, "userQueryService", new UserQueryServiceFallback());
        when(conversationMemberMapper.selectConversationAllMembers(CONV_ID)).thenReturn(List.of(member(USER_ID)));

        MessageDTO dto = messageService.recallMessage(MSG_ID);

        assertEquals(2, dto.getStatus());
        assertNull(dto.getSenderName());
        verify(wsMessageSender).sendToUser(eq(USER_ID), eq("MESSAGE_UPDATE"), any(MessageDTO.class));
    }

    @Test
    void getChatList_whenUserServiceFallbackTriggers_fallsBackToConversationMetadata() {
        Conversation conv = new Conversation();
        conv.setId(CONV_ID);
        conv.setType(1);
        conv.setName("Cached Peer");
        conv.setAvatar("cached.png");
        when(conversationMapper.selectMyConversations(USER_ID)).thenReturn(List.of(conv));
        when(conversationMemberMapper.countByConversationIds(List.of(CONV_ID))).thenReturn(List.of());
        when(conversationMemberMapper.selectMemberByConversationIds(List.of(CONV_ID)))
                .thenReturn(List.of(member(USER_ID), member(OTHER_ID)));
        when(conversationMemberMapper.countUnreadByConversationIds(USER_ID, List.of(CONV_ID))).thenReturn(List.of());
        ReflectionTestUtils.setField(messageService, "userQueryService", new UserQueryServiceFallback());

        List<ConversationResponse> result = messageService.getChatList();

        assertEquals(1, result.size());
        assertEquals("Cached Peer", result.get(0).getName());
        assertEquals("cached.png", result.get(0).getAvatar());
        assertEquals(OTHER_ID, result.get(0).getFriendId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getChatHistory_whenUserServiceFallbackTriggers_returnsMessagesWithoutSenderProfile() {
        when(conversationMemberMapper.selectByConversationAndUser(CONV_ID, USER_ID)).thenReturn(member(USER_ID));
        Message message = message(MSG_ID, OTHER_ID, 1, 1, LocalDateTime.now());
        Page<Message> page = new Page<>(1, 30);
        page.setRecords(List.of(message));
        page.setTotal(1);
        when(messageMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(userQueryService.batchGetUsers(anyList()))
                .thenThrow(AppException.serviceUnavailable("用户服务暂不可用"));

        PageResult<MessageDTO> result = messageService.getChatHistory(CONV_ID, null, 1, 30);

        assertEquals(1, result.getRecords().size());
        assertEquals(MSG_ID, result.getRecords().get(0).getId());
        assertNull(result.getRecords().get(0).getSenderName());
    }

    private static Message message(String id, String senderId, int status, int contentType, LocalDateTime createdAt) {
        Message message = new Message();
        message.setId(id);
        message.setConversationId(CONV_ID);
        message.setSenderId(senderId);
        message.setStatus(status);
        message.setContentType(contentType);
        message.setContent("hello");
        message.setCreatedAt(createdAt);
        return message;
    }

    private static ConversationMember member(String userId) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(CONV_ID);
        member.setUserId(userId);
        return member;
    }
}

package com.faust.easychat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import com.faust.easychat.context.UserContext;
import com.faust.easychat.dto.chat.MessageDTO;
import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.entity.Conversation;
import com.faust.easychat.entity.ConversationMember;
import com.faust.easychat.entity.Message;
import com.faust.easychat.exception.AppException;
import com.faust.easychat.api.user.UserQueryService;
import com.faust.easychat.mapper.ConversationMapper;
import com.faust.easychat.mapper.ConversationMemberMapper;
import com.faust.easychat.mapper.MessageMapper;
import com.faust.easychat.netty.WsMessageSender;
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
        when(messageMapper.selectCount(any())).thenReturn(0L);
        Conversation conversation = new Conversation();
        conversation.setId(CONV_ID);
        conversation.setLastMessage("hello");
        when(conversationMapper.selectById(CONV_ID)).thenReturn(conversation);

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
        verify(conversationMapper).updateById(conversation);
        assertEquals("[已撤回]", conversation.getLastMessage());
        verify(wsMessageSender).sendToUser(eq(USER_ID), eq("MESSAGE_UPDATE"), any(MessageDTO.class));
        verify(wsMessageSender).sendToUser(eq(OTHER_ID), eq("MESSAGE_UPDATE"), any(MessageDTO.class));
    }

    @Test
    void recallMessage_whenNewerMessageExists_doesNotUpdateConversationSummary() {
        Message message = message(MSG_ID, USER_ID, 1, 1, LocalDateTime.now());
        when(messageMapper.selectById(MSG_ID)).thenReturn(message);
        when(conversationMemberMapper.selectByConversationAndUser(CONV_ID, USER_ID)).thenReturn(member(USER_ID));
        when(messageMapper.selectCount(any())).thenReturn(1L);

        UserBriefDTO sender = new UserBriefDTO();
        sender.setUserId(USER_ID);
        when(userQueryService.getUserById(USER_ID)).thenReturn(sender);
        when(conversationMemberMapper.selectConversationAllMembers(CONV_ID))
                .thenReturn(List.of(member(USER_ID)));

        messageService.recallMessage(MSG_ID);

        verify(conversationMapper, never()).updateById(any(Conversation.class));
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

package com.faust.easychat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.faust.easychat.api.chat.ConversationDeleteService;
import com.faust.easychat.api.chat.FriendRequestService;
import com.faust.easychat.context.UserContext;
import com.faust.easychat.entity.User;
import com.faust.easychat.enums.ReturnCodeEnum;
import com.faust.easychat.exception.AppException;
import com.faust.easychat.mapper.FriendMapper;
import com.faust.easychat.mapper.FriendRequestMapper;
import com.faust.easychat.mapper.UserMapper;
import com.faust.easychat.mapper.UserSettingsMapper;

@ExtendWith(MockitoExtension.class)
class FriendServiceImplDeleteTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private FriendMapper friendMapper;
    @Mock
    private UserSettingsMapper userSettingsMapper;
    @Mock
    private FriendRequestMapper friendRequestMapper;
    @Mock
    private FriendRequestService friendRequestService;
    @Mock
    private ConversationDeleteService conversationDeleteService;

    @InjectMocks
    private FriendServiceImpl friendService;

    @BeforeEach
    void setUp() {
        UserContext.set("me");
        ReflectionTestUtils.setField(friendService, "conversationDeleteService", conversationDeleteService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void deleteFriend_whenChatUnavailable_throws() {
        User friend = new User();
        friend.setId("friend");
        when(userMapper.selectById("friend")).thenReturn(friend);
        doThrow(new IllegalStateException("down"))
                .when(conversationDeleteService).removeDirectConversation("me", "friend");

        AppException ex = assertThrows(AppException.class, () -> friendService.deleteFriend("friend"));

        assertEquals(ReturnCodeEnum.SERVICE_UNAVAILABLE, ex.getReturnCode());
    }
}

package com.faust.easychat.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.faust.easychat.api.fallback.UserQueryServiceFallback;
import com.faust.easychat.api.user.UserQueryService;
import com.faust.easychat.context.UserContext;
import com.faust.easychat.dto.chat.AcceptGroupInviteResponse;
import com.faust.easychat.dto.chat.AddGroupMembersRequest;
import com.faust.easychat.dto.chat.CreateGroupRequest;
import com.faust.easychat.dto.chat.EditMemberRoleRequest;
import com.faust.easychat.dto.chat.GroupJoinRequestDTO;
import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.entity.Conversation;
import com.faust.easychat.entity.ConversationJoinRequest;
import com.faust.easychat.entity.ConversationMember;
import com.faust.easychat.entity.GroupInvite;
import com.faust.easychat.entity.Message;
import com.faust.easychat.enums.ReturnCodeEnum;
import com.faust.easychat.exception.AppException;
import com.faust.easychat.mapper.ConversationJoinRequestMapper;
import com.faust.easychat.mapper.ConversationMapper;
import com.faust.easychat.mapper.ConversationMemberMapper;
import com.faust.easychat.mapper.GroupInviteMapper;
import com.faust.easychat.mapper.MessageMapper;
import com.faust.easychat.netty.WsMessageSender;
import com.faust.easychat.util.CosUtil;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    private static final String OWNER_ID = "owner-1";
    private static final String USER_2 = "user-2";
    private static final String GROUP_ID = "group-1";

    @Mock
    private CosUtil cosUtil;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private ConversationMapper conversationMapper;
    @Mock
    private ConversationMemberMapper conversationMemberMapper;
    @Mock
    private GroupInviteMapper groupInviteMapper;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private ConversationJoinRequestMapper conversationJoinRequestMapper;
    @Mock
    private WsMessageSender wsMessageSender;

    @InjectMocks
    private GroupServiceImpl groupService;

    @BeforeEach
    void setUp() {
        UserContext.set(OWNER_ID);
        ReflectionTestUtils.setField(groupService, "userQueryService", userQueryService);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createGroup_whenUserServiceUnavailableOnOwner_throwsServiceUnavailable() {
        ReflectionTestUtils.setField(groupService, "userQueryService", new UserQueryServiceFallback());
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Test Group");
        request.setMemberIds(List.of(USER_2));

        AppException ex = assertThrows(AppException.class, () -> groupService.createGroup(request));

        assertEquals(ReturnCodeEnum.SERVICE_UNAVAILABLE, ex.getReturnCode());
        verify(conversationMapper, never()).insert(any(Conversation.class));
    }

    @Test
    void createGroup_whenUserServiceUnavailableOnMembers_throwsServiceUnavailable() {
        UserBriefDTO owner = user(OWNER_ID, "Owner");
        when(userQueryService.getUserById(OWNER_ID)).thenReturn(owner);
        when(userQueryService.batchGetUsers(List.of(USER_2)))
                .thenThrow(AppException.serviceUnavailable("用户服务暂不可用"));

        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Test Group");
        request.setMemberIds(List.of(USER_2));

        AppException ex = assertThrows(AppException.class, () -> groupService.createGroup(request));

        assertEquals(ReturnCodeEnum.SERVICE_UNAVAILABLE, ex.getReturnCode());
        verify(conversationMapper, never()).insert(any(Conversation.class));
    }

    @Test
    void addMembers_whenUserServiceUnavailable_throwsServiceUnavailable() {
        Conversation group = group(GROUP_ID, OWNER_ID);
        when(conversationMapper.selectById(GROUP_ID)).thenReturn(group);
        ReflectionTestUtils.setField(groupService, "userQueryService", new UserQueryServiceFallback());

        AddGroupMembersRequest request = new AddGroupMembersRequest();
        request.setMemberIds(List.of(USER_2));

        AppException ex = assertThrows(AppException.class, () -> groupService.addMembers(GROUP_ID, request));

        assertEquals(ReturnCodeEnum.SERVICE_UNAVAILABLE, ex.getReturnCode());
    }

    @Test
    void editMemberRole_whenUserServiceUnavailable_throwsServiceUnavailable() {
        Conversation group = group(GROUP_ID, OWNER_ID);
        when(conversationMapper.selectById(GROUP_ID)).thenReturn(group);
        ReflectionTestUtils.setField(groupService, "userQueryService", new UserQueryServiceFallback());

        EditMemberRoleRequest request = new EditMemberRoleRequest();
        request.setRole(2);

        AppException ex = assertThrows(AppException.class,
                () -> groupService.editMemberRole(GROUP_ID, USER_2, request));

        assertEquals(ReturnCodeEnum.SERVICE_UNAVAILABLE, ex.getReturnCode());
    }

    @Test
    void inviteMembers_whenIsFriendFallbackTriggers_throwsServiceUnavailable() {
        Conversation group = group(GROUP_ID, OWNER_ID);
        when(conversationMapper.selectById(GROUP_ID)).thenReturn(group);
        when(userQueryService.getUserById(OWNER_ID)).thenReturn(user(OWNER_ID, "Owner"));
        when(userQueryService.batchGetUsers(List.of(USER_2))).thenReturn(List.of(user(USER_2, "Bob")));
        when(conversationMemberMapper.selectConversationAllMembers(GROUP_ID)).thenReturn(List.of());
        when(userQueryService.isFriend(OWNER_ID, USER_2))
                .thenThrow(AppException.serviceUnavailable("用户服务暂不可用"));

        AddGroupMembersRequest request = new AddGroupMembersRequest();
        request.setMemberIds(List.of(USER_2));

        AppException ex = assertThrows(AppException.class, () -> groupService.inviteMembers(GROUP_ID, request));

        assertEquals(ReturnCodeEnum.SERVICE_UNAVAILABLE, ex.getReturnCode());
        verify(groupInviteMapper, never()).insert(any(GroupInvite.class));
    }

    @Test
    void getGroupInfo_whenUserServiceUnavailable_throwsServiceUnavailable() {
        ConversationMember me = new ConversationMember();
        me.setConversationId(GROUP_ID);
        me.setUserId(OWNER_ID);
        when(conversationMemberMapper.selectByConversationAndUser(GROUP_ID, OWNER_ID)).thenReturn(me);
        when(conversationMapper.selectById(GROUP_ID)).thenReturn(group(GROUP_ID, OWNER_ID));
        when(conversationMemberMapper.selectConversationAllMembers(GROUP_ID)).thenReturn(List.of(me));
        ReflectionTestUtils.setField(groupService, "userQueryService", new UserQueryServiceFallback());

        AppException ex = assertThrows(AppException.class, () -> groupService.getGroupInfo(GROUP_ID));

        assertEquals(ReturnCodeEnum.SERVICE_UNAVAILABLE, ex.getReturnCode());
    }

    @Test
    void getJoinRequests_whenUserServiceUnavailable_degradesGracefully() {
        ConversationJoinRequest req = new ConversationJoinRequest();
        req.setId(10L);
        req.setConversationId(GROUP_ID);
        req.setUserId(USER_2);
        req.setMessage("hi");
        req.setCreatedAt(LocalDateTime.now());
        when(conversationJoinRequestMapper.selectPendingForAdmin(OWNER_ID, GROUP_ID)).thenReturn(List.of(req));
        when(conversationMapper.selectBatchIds(List.of(GROUP_ID))).thenReturn(List.of(group(GROUP_ID, OWNER_ID)));
        ReflectionTestUtils.setField(groupService, "userQueryService", new UserQueryServiceFallback());

        List<GroupJoinRequestDTO> result = groupService.getJoinRequests(GROUP_ID);

        assertEquals(1, result.size());
        assertEquals(USER_2, result.get(0).getUserId());
        assertEquals("", result.get(0).getNickname());
    }

    @Test
    void acceptInvite_whenInviteSenderLookupUnavailable_degradesGracefully() {
        UserContext.set(USER_2);
        GroupInvite invite = new GroupInvite();
        invite.setId(99L);
        invite.setGroupId(GROUP_ID);
        invite.setInviterId(OWNER_ID);
        invite.setInviteeId(USER_2);
        invite.setStatus(0);
        invite.setMessageId("msg-99");
        when(groupInviteMapper.selectById(99L)).thenReturn(invite);

        Conversation group = group(GROUP_ID, OWNER_ID);
        group.setJoinVerify(0);
        when(conversationMapper.selectById(GROUP_ID)).thenReturn(group);

        Message inviteMsg = new Message();
        inviteMsg.setId("msg-99");
        inviteMsg.setConversationId(OWNER_ID + "_" + USER_2);
        inviteMsg.setSenderId(OWNER_ID);
        inviteMsg.setContentType(4);
        inviteMsg.setContent("{\"inviteStatus\":\"pending\"}");
        inviteMsg.setStatus(1);
        when(messageMapper.selectById("msg-99")).thenReturn(inviteMsg);
        ReflectionTestUtils.setField(groupService, "userQueryService", new UserQueryServiceFallback());

        AcceptGroupInviteResponse response = groupService.acceptInvite(99L, null);

        assertEquals("joined", response.getStatus());
        assertEquals(GROUP_ID, response.getGroupConversationId());
    }

    @Test
    void uploadGroupAvatar_extensionlessFilename_throwsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "avatar", "image/png", new byte[] {1, 2, 3});

        AppException ex = assertThrows(AppException.class, () -> groupService.uploadGroupAvatar(GROUP_ID, file));

        assertEquals(ReturnCodeEnum.BAD_REQUEST, ex.getReturnCode());
        assertEquals("只支持 jpg/jpeg/png/gif/webp 格式", ex.getMessage());
        verify(cosUtil, never()).upload(any(), any());
    }

    private static Conversation group(String id, String ownerId) {
        Conversation group = new Conversation();
        group.setId(id);
        group.setType(2);
        group.setName("Group");
        group.setOwnerId(ownerId);
        return group;
    }

    private static UserBriefDTO user(String id, String nickname) {
        UserBriefDTO dto = new UserBriefDTO();
        dto.setUserId(id);
        dto.setNickname(nickname);
        dto.setAvatar(id + ".png");
        return dto;
    }
}

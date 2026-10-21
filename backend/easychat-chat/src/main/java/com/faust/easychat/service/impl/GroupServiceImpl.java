package com.faust.easychat.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.faust.easychat.context.UserContext;
import com.faust.easychat.dto.chat.AcceptGroupInviteRequest;
import com.faust.easychat.dto.chat.AcceptGroupInviteResponse;
import com.faust.easychat.dto.chat.AddGroupMembersRequest;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.faust.easychat.dto.chat.MessageDTO;
import com.faust.easychat.entity.Message;
import com.faust.easychat.mapper.MessageMapper;
import com.faust.easychat.dto.chat.ConversationResponse;
import com.faust.easychat.dto.chat.ConversationUpdateDTO;
import com.faust.easychat.dto.chat.CreateGroupRequest;
import com.faust.easychat.dto.chat.EditMemberRoleRequest;
import com.faust.easychat.dto.chat.GroupBriefDTO;
import com.faust.easychat.dto.chat.GroupDTO;
import com.faust.easychat.dto.chat.GroupInfoDTO;
import com.faust.easychat.dto.chat.GroupJoinRequestDTO;
import com.faust.easychat.dto.chat.GroupJoinRequestHandledDTO;
import com.faust.easychat.dto.chat.UpdateGroupRequest;
import com.faust.easychat.dto.user.UserBriefDTO;
import com.faust.easychat.entity.Conversation;
import com.faust.easychat.entity.ConversationJoinRequest;
import com.faust.easychat.entity.ConversationMember;
import com.faust.easychat.entity.GroupInvite;
import com.faust.easychat.api.dubbo.DubboFallbacks;
import com.faust.easychat.api.user.UserQueryService;
import com.faust.easychat.exception.AppException;
import com.faust.easychat.mapper.ConversationJoinRequestMapper;
import com.faust.easychat.mapper.ConversationMapper;
import com.faust.easychat.mapper.ConversationMemberMapper;
import com.faust.easychat.mapper.GroupInviteMapper;
import com.faust.easychat.netty.WsMessageSender;
import com.faust.easychat.service.GroupService;
import com.faust.easychat.util.CosUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final CosUtil cosUtil;

    @DubboReference(mock = DubboFallbacks.USER_QUERY, cluster = "failfast", retries = 0)
    private UserQueryService userQueryService;

    private final ConversationMapper conversationMapper;

    private final ConversationMemberMapper conversationMemberMapper;

    private final GroupInviteMapper groupInviteMapper;

    private final MessageMapper messageMapper;

    private final ConversationJoinRequestMapper conversationJoinRequestMapper;

    private final WsMessageSender wsMessageSender;

    @Override
    @Transactional
    public GroupInfoDTO createGroup(CreateGroupRequest request) {
        // 创建者从登录态取，忽略前端传的 ownerId（防伪造群主）
        String ownerId = UserContext.get();
        UserBriefDTO owner = userQueryService.getUserById(ownerId);
        if (owner == null) {
            throw AppException.userNotFound();
        }

        // 防御性去重 + 排除创建者（避免 owner 自插撞唯一索引）
        List<String> memberIds = request.getMemberIds().stream()
                .distinct()
                .filter(id -> !id.equals(ownerId))
                .toList();

        // 批量校验成员真实性：一次查询 + 数量比对（不存在的人不会出现在结果里）
        List<UserBriefDTO> members = userQueryService.batchGetUsers(memberIds);
        if (members == null || members.size() != memberIds.size()) {
            throw AppException.userNotFound();
        }

        Conversation conversation = new Conversation();
        conversation.setType(2);
        conversation.setName(request.getName());
        conversation.setAvatar(request.getAvatar());
        conversation.setAnnouncement(request.getAnnouncement());
        conversation.setJoinVerify(request.getJoinVerify() == null ? 1 : request.getJoinVerify());
        conversation.setOwnerId(ownerId);
        conversation.setStatus(1);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.insert(conversation);

        LocalDateTime now = LocalDateTime.now();
        // 群主入成员表（role=2）
        conversationMemberMapper.batchInsert(conversation.getId(), List.of(ownerId), 2, now);
        // 群成员入成员表（role=1）
        conversationMemberMapper.batchInsert(conversation.getId(), memberIds, 1, now);

        GroupInfoDTO groupInfo = new GroupInfoDTO();
        groupInfo.setId(conversation.getId());
        groupInfo.setName(conversation.getName());
        groupInfo.setAvatar(conversation.getAvatar());
        groupInfo.setOwnerId(owner.getUserId());
        groupInfo.setOwnerName(owner.getNickname());
        groupInfo.setAnnouncement(conversation.getAnnouncement());
        groupInfo.setJoinVerify(conversation.getJoinVerify());
        groupInfo.setMembers(members);
        return groupInfo;
    }

    @Override
    public String uploadGroupAvatar(String id, MultipartFile file) {
        if (file.isEmpty()) {
            throw AppException.badRequest("请选择文件");
        }

        String originalFilename = file.getOriginalFilename();
        String suffix = null;
        if (originalFilename != null) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        if (suffix == null || !Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".webp").contains(suffix.toLowerCase())) {
            throw AppException.badRequest("只支持 jpg/jpeg/png/gif/webp 格式");
        }

        if (file.getSize() > 2 * 1024 * 1024) {
            throw AppException.badRequest("文件大小不能超过2MB");
        }

        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw AppException.conversationNotFound();
        }
        if (conversation.getType() != 2) {
            throw AppException.badRequest("仅群聊可设置头像");
        }

        // 权限：仅群主或管理员可修改群头像
        String currentUserId = UserContext.get();
        if (!conversation.getOwnerId().equals(currentUserId)) {
            ConversationMember member = conversationMemberMapper.selectByConversationAndUser(id, currentUserId);
            if (member == null || member.getRole() != 2) {
                throw AppException.forbidden("无权限修改群头像");
            }
        }

        String path = "easychat/group-avatars/" + UUID.randomUUID() + suffix;
        String url = cosUtil.upload(file, path);

        try {
            conversation.setAvatar(url);
            conversationMapper.updateById(conversation);
        } catch (Exception e) {
            log.error("头像URL入库失败，COS文件成为孤儿: path={}, error={}", path, e.getMessage());
            throw AppException.internalError("头像上传失败");
        }

        return url;
    }

    @Override
    public List<GroupBriefDTO> getAllMyGroups() {
        String ownerId = UserContext.get();
        List<Conversation> conversations = conversationMapper.selectByOwner(ownerId);
        if (conversations.isEmpty()) {
            return List.of();
        }

        // 一次查出所有群的人数，避免 N+1
        List<String> ids = conversations.stream().map(Conversation::getId).toList();
        Map<String, Integer> totalMap = conversationMemberMapper.countByConversationIds(ids).stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("conversation_id"),
                        m -> ((Number) m.get("total")).intValue(),
                        (x, y) -> x));

        return conversations.stream().map(conversation -> {
            GroupBriefDTO group = new GroupBriefDTO();
            group.setId(conversation.getId());
            group.setName(conversation.getName());
            group.setAvatar(conversation.getAvatar());
            group.setTotal(totalMap.getOrDefault(conversation.getId(), 0));
            return group;
        }).toList();
    }

    @Override
    public List<GroupBriefDTO> getAllJoinGroups() {
        String userId = UserContext.get();
        List<Conversation> conversations = conversationMemberMapper.selectJoinedConversations(userId);
        if (conversations.isEmpty()) {
            return List.of();
        }

        // 一次查出所有群的人数，避免 N+1
        List<String> ids = conversations.stream().map(Conversation::getId).toList();
        Map<String, Integer> totalMap = conversationMemberMapper.countByConversationIds(ids).stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("conversation_id"),
                        m -> ((Number) m.get("total")).intValue(),
                        (x, y) -> x));

        return conversations.stream().map(conversation -> {
            GroupBriefDTO group = new GroupBriefDTO();
            group.setId(conversation.getId());
            group.setName(conversation.getName());
            group.setAvatar(conversation.getAvatar());
            group.setTotal(totalMap.getOrDefault(conversation.getId(), 0));
            return group;
        }).toList();
    }

    @Override
    public GroupDTO getGroupInfo(String id) {
        // 权限：仅群成员可查看群详情
        String currentUserId = UserContext.get();
        ConversationMember me = conversationMemberMapper.selectByConversationAndUser(id, currentUserId);
        if (me == null) {
            throw AppException.forbidden("你不是该群成员");
        }

        Conversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw AppException.conversationNotFound();
        }
        List<ConversationMember> conversationMembers = conversationMemberMapper.selectConversationAllMembers(id);

        // 批量拿用户信息，避免 N+1
        List<String> userIds = conversationMembers.stream().map(ConversationMember::getUserId).toList();
        List<UserBriefDTO> userList = userQueryService.batchGetUsers(userIds);
        if (userList == null) {
            throw AppException.internalError("获取群成员信息失败");
        }
        Map<String, UserBriefDTO> userMap = userList.stream()
                .collect(Collectors.toMap(UserBriefDTO::getUserId, u -> u, (x, y) -> x));

        List<GroupDTO.Member> members = conversationMembers.stream().map(conversationMember -> {
            UserBriefDTO user = userMap.get(conversationMember.getUserId());
            if (user == null) {
                // 用户已注销等情况，跳过该成员
                return null;
            }
            GroupDTO.Member member = new GroupDTO.Member();
            member.setUserId(user.getUserId());
            member.setNickname(user.getNickname());
            member.setAvatar(user.getAvatar());
            member.setRole(conversationMember.getRole());
            return member;
        }).filter(Objects::nonNull).toList();

        GroupDTO dto = new GroupDTO();
        dto.setId(conversation.getId());
        dto.setName(conversation.getName());
        dto.setOwnerId(conversation.getOwnerId());
        dto.setAvatar(conversation.getAvatar());
        dto.setAnnouncement(conversation.getAnnouncement());
        dto.setJoinVerify(conversation.getJoinVerify());
        dto.setTotal(members.size());
        dto.setMembers(members);
        return dto;
    }

    @Override
    public void updateGroup(String conversationId, UpdateGroupRequest request) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw AppException.conversationNotFound();
        }
        String currentUserId = UserContext.get();
        if (!conversation.getOwnerId().equals(currentUserId)) {
            ConversationMember member = conversationMemberMapper.selectByConversationAndUser(conversationId, currentUserId);
            if (member == null || member.getRole() != 2) {
                throw AppException.forbidden("无权限修改");
            }
        }
        if (request.getName() != null) {
            conversation.setName(request.getName());
        }
        if (request.getAnnouncement() != null) {
            conversation.setAnnouncement(request.getAnnouncement());
        }
        if (request.getJoinVerify() != null) {
            conversation.setJoinVerify(request.getJoinVerify());
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationMapper.updateById(conversation);
    }

    @Override
    @Transactional
    public void addMembers(String conversationId, AddGroupMembersRequest request) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw AppException.conversationNotFound();
        }
        if (conversation.getType() != 2) {
            throw AppException.badRequest("仅群聊可添加成员");
        }

        String currentUserId = UserContext.get();
        if (!conversation.getOwnerId().equals(currentUserId)) {
            ConversationMember me = conversationMemberMapper.selectByConversationAndUser(conversationId, currentUserId);
            if (me == null || me.getRole() != 2) {
                throw AppException.forbidden("无权限添加成员");
            }
        }

        List<String> memberIds = request.getMemberIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(id -> !id.equals(currentUserId))
                .toList();
        if (memberIds.isEmpty()) {
            return;
        }

        List<UserBriefDTO> users = userQueryService.batchGetUsers(memberIds);
        if (users == null || users.size() != memberIds.size()) {
            throw AppException.userNotFound();
        }

        List<String> existing = conversationMemberMapper.selectConversationAllMembers(conversationId).stream()
                .map(ConversationMember::getUserId)
                .toList();
        List<String> toAdd = memberIds.stream().filter(id -> !existing.contains(id)).toList();

        if (!toAdd.isEmpty()) {
            conversationMemberMapper.batchInsert(conversationId, toAdd, 1, LocalDateTime.now());
        }
    }

    @Override
    @Transactional
    public void quitGroup(String conversationId) {
        String currentUserId = UserContext.get();
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw AppException.conversationNotFound();
        }

        if (currentUserId.equals(conversation.getOwnerId())) {
            List<ConversationMember> conversationMembers = conversationMemberMapper.selectConversationAllMembers(conversationId);
            Map<String, String> removed = Map.of("id", conversationId);
            for (ConversationMember member : conversationMembers) {
                wsMessageSender.sendToUser(member.getUserId(), "CONVERSATION_REMOVED", removed);
            }
            List<String> userIds = conversationMembers.stream().map(ConversationMember::getUserId).toList();
            if (!userIds.isEmpty()) {
                conversationMemberMapper.batchDelete(conversationId, userIds);
            }
            conversationMapper.deleteById(conversationId);
            return;
        }

        // 普通成员：只删自己在该群的成员记录
        conversationMemberMapper.deleteByConversationAndUser(conversationId, currentUserId);
    }

    @Override
    @Transactional
    public void kickMember(String groupId, String userId) {
        Conversation group = requireGroup(groupId);
        String currentUserId = UserContext.get();
        assertCanKickMember(group, currentUserId, userId);

        ConversationMember target = conversationMemberMapper.selectByConversationAndUser(groupId, userId);
        if (target == null) {
            throw AppException.badRequest("目标不是群成员");
        }

        conversationMemberMapper.deleteByConversationAndUser(groupId, userId);

        Map<String, String> removed = Map.of("id", groupId);
        wsMessageSender.sendToUser(userId, "CONVERSATION_REMOVED", removed);

        pushGroupMemberCountUpdate(group);
    }

    private void assertCanKickMember(Conversation group, String operatorId, String targetUserId) {
        if (targetUserId.equals(operatorId)) {
            throw AppException.badRequest("不能移出自己");
        }
        if (targetUserId.equals(group.getOwnerId())) {
            throw AppException.badRequest("不能移出群主");
        }
        if (group.getOwnerId().equals(operatorId)) {
            return;
        }
        ConversationMember operator = conversationMemberMapper.selectByConversationAndUser(group.getId(), operatorId);
        if (operator == null || operator.getRole() == null || operator.getRole() != 2) {
            throw AppException.forbidden("无权限移出成员");
        }
        ConversationMember target = conversationMemberMapper.selectByConversationAndUser(group.getId(), targetUserId);
        if (target == null) {
            throw AppException.badRequest("目标不是群成员");
        }
        if (target.getRole() != null && target.getRole() == 2) {
            throw AppException.forbidden("管理员不能移出其他管理员");
        }
    }

    private void pushGroupMemberCountUpdate(Conversation group) {
        List<ConversationMember> members = conversationMemberMapper.selectConversationAllMembers(group.getId());
        ConversationUpdateDTO update = new ConversationUpdateDTO();
        update.setId(group.getId());
        update.setLastMessage(group.getLastMessage());
        update.setLastMessageAt(group.getLastMessageAt());
        update.setTotal(members.size());
        for (ConversationMember member : members) {
            wsMessageSender.sendToUser(member.getUserId(), "CONVERSATION_UPDATE", update);
        }
    }

    private void pushGroupMemberJoined(Conversation group, String newUserId) {
        pushGroupMemberCountUpdate(group);
        pushNewGroupConversation(group, newUserId);
    }

    private void pushNewGroupConversation(Conversation group, String userId) {
        if (conversationMemberMapper.selectByConversationAndUser(group.getId(), userId) == null) {
            return;
        }
        int total = conversationMemberMapper.selectConversationAllMembers(group.getId()).size();
        ConversationResponse response = new ConversationResponse();
        response.setId(group.getId());
        response.setName(group.getName());
        response.setAvatar(group.getAvatar());
        response.setType(2);
        response.setIsGroup(true);
        response.setIsGroupInvite(false);
        response.setLastMessage(group.getLastMessage());
        response.setLastMessageAt(group.getLastMessageAt());
        response.setUnread(0);
        response.setTotal(total);
        wsMessageSender.sendToUser(userId, "NEW_CONVERSATION", response);
    }

    @Override
    public void editMemberRole(String groupId, String userId, EditMemberRoleRequest request) {
        String currentUserId = UserContext.get();
        if (currentUserId == null) {
            throw AppException.userNotFound();
        }
        Conversation group = conversationMapper.selectById(groupId);
        if (group == null) {
            throw AppException.conversationNotFound();
        }
        if (group.getType() != 2) {
            throw AppException.badRequest("仅群聊可修改管理员");
        }
        if (!currentUserId.equals(group.getOwnerId())) {
            throw AppException.badRequest("只有群主可以修改管理员");
        }
        Integer role = request.getRole();
        if (role == null || (role != 1 && role != 2)) {
            throw AppException.badRequest("角色只能是1或2");
        }
        if (userQueryService.getUserById(userId) == null) {
            throw AppException.userNotFound();
        }
        ConversationMember member = conversationMemberMapper.selectByConversationAndUser(groupId, userId);
        if (member == null) {
            throw AppException.badRequest("目标必须是该群成员");
        }
        if (userId.equals(currentUserId)) {
            throw AppException.badRequest("目标不能是群主本人");
        }
        member.setRole(role);
        conversationMemberMapper.updateById(member);
    }

    @Override
    @Transactional
    public void inviteMembers(String groupId, AddGroupMembersRequest request) {
        Conversation group = requireGroup(groupId);
        String inviterId = UserContext.get();
        assertGroupAdmin(group, inviterId);

        UserBriefDTO inviter = userQueryService.getUserById(inviterId);
        if (inviter == null) {
            throw AppException.userNotFound();
        }

        List<String> inviteeIds = request.getMemberIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(id -> !id.equals(inviterId))
                .toList();
        if (inviteeIds.isEmpty()) {
            return;
        }

        List<UserBriefDTO> users = userQueryService.batchGetUsers(inviteeIds);
        if (users == null || users.size() != inviteeIds.size()) {
            throw AppException.userNotFound();
        }

        List<String> existingMembers = conversationMemberMapper.selectConversationAllMembers(groupId).stream()
                .map(ConversationMember::getUserId)
                .toList();

        LocalDateTime now = LocalDateTime.now();
        for (String inviteeId : inviteeIds) {
            if (existingMembers.contains(inviteeId)) {
                continue;
            }
            if (!userQueryService.isFriend(inviterId, inviteeId)) {
                throw AppException.badRequest("只能邀请好友加入群聊");
            }
            if (groupInviteMapper.selectActiveByGroupAndInvitee(groupId, inviteeId) != null) {
                continue;
            }

            String directConvId = ensureDirectConversation(inviterId, inviteeId);

            GroupInvite invite = new GroupInvite();
            invite.setGroupId(groupId);
            invite.setInviteeId(inviteeId);
            invite.setInviterId(inviterId);
            invite.setInviteConversationId(directConvId);
            invite.setStatus(0);
            invite.setCreatedAt(now);
            invite.setUpdatedAt(now);
            groupInviteMapper.insert(invite);

            String content = buildInviteContent(invite, group, inviter);
            Message message = new Message();
            message.setConversationId(directConvId);
            message.setSenderId(inviterId);
            message.setContentType(4);
            message.setContent(content);
            message.setCreatedAt(now);
            messageMapper.insert(message);

            invite.setMessageId(message.getId());
            groupInviteMapper.updateById(invite);

            String lastMessage = "[群邀请] " + group.getName();
            Conversation directConv = conversationMapper.selectById(directConvId);
            directConv.setLastMessage(lastMessage);
            directConv.setLastMessageAt(now);
            directConv.setUpdatedAt(now);
            conversationMapper.updateById(directConv);

            MessageDTO dto = new MessageDTO();
            dto.setId(message.getId());
            dto.setContent(message.getContent());
            dto.setContentType(message.getContentType());
            dto.setConversationId(message.getConversationId());
            dto.setCreatedAt(message.getCreatedAt());
            dto.setSenderId(inviterId);
            dto.setSenderName(inviter.getNickname());
            dto.setSenderAvatar(inviter.getAvatar());
            wsMessageSender.sendToUser(inviteeId, "NEW_MESSAGE", dto);
            pushConversationUpdate(directConvId, lastMessage, now, inviterId, inviteeId);
        }
    }

    private void pushConversationUpdate(String conversationId, String lastMessage, LocalDateTime lastMessageAt,
            String... userIds) {
        ConversationUpdateDTO update = new ConversationUpdateDTO();
        update.setId(conversationId);
        update.setLastMessage(lastMessage);
        update.setLastMessageAt(lastMessageAt);
        for (String userId : userIds) {
            wsMessageSender.sendToUser(userId, "CONVERSATION_UPDATE", update);
        }
    }

    @Override
    @Transactional
    public AcceptGroupInviteResponse acceptInvite(Long inviteId, AcceptGroupInviteRequest request) {
        GroupInvite invite = groupInviteMapper.selectById(inviteId);
        if (invite == null) {
            throw AppException.notFound("邀请不存在");
        }
        String currentUserId = UserContext.get();
        if (!currentUserId.equals(invite.getInviteeId())) {
            throw AppException.forbidden("无权处理该邀请");
        }
        if (invite.getStatus() == 1) {
            throw AppException.badRequest("你已加入该群");
        }
        if (invite.getStatus() == 3) {
            throw AppException.badRequest("邀请已失效");
        }
        if (invite.getStatus() == 2) {
            throw AppException.badRequest("已提交入群申请，请等待管理员审批");
        }

        Conversation group = requireGroup(invite.getGroupId());
        if (conversationMemberMapper.selectByConversationAndUser(group.getId(), currentUserId) != null) {
            finishInvite(invite);
            AcceptGroupInviteResponse response = new AcceptGroupInviteResponse();
            response.setStatus("joined");
            response.setGroupConversationId(group.getId());
            return response;
        }

        Integer joinVerify = group.getJoinVerify() == null ? 1 : group.getJoinVerify();
        if (joinVerify == 0) {
            joinGroupMember(group.getId(), currentUserId);
            finishInvite(invite);
            pushGroupMemberJoined(group, currentUserId);
            AcceptGroupInviteResponse response = new AcceptGroupInviteResponse();
            response.setStatus("joined");
            response.setGroupConversationId(group.getId());
            return response;
        }

        String message = request != null ? request.getMessage() : null;
        if (message == null || message.isBlank()) {
            throw AppException.badRequest("请填写验证信息");
        }
        if (conversationJoinRequestMapper.selectPendingByGroupAndUser(group.getId(), currentUserId) != null) {
            throw AppException.badRequest("已提交入群申请，请等待管理员审批");
        }

        LocalDateTime now = LocalDateTime.now();
        ConversationJoinRequest joinRequest = new ConversationJoinRequest();
        joinRequest.setConversationId(group.getId());
        joinRequest.setUserId(currentUserId);
        joinRequest.setMessage(message.trim());
        joinRequest.setStatus(0);
        joinRequest.setCreatedAt(now);
        joinRequest.setUpdatedAt(now);
        conversationJoinRequestMapper.insert(joinRequest);

        invite.setStatus(2);
        invite.setUpdatedAt(now);
        groupInviteMapper.updateById(invite);
        updateInviteMessageStatus(invite, "waiting");
        pushInviteMessageUpdate(invite);
        pushGroupJoinRequest(joinRequest, group);

        AcceptGroupInviteResponse response = new AcceptGroupInviteResponse();
        response.setStatus("pending");
        return response;
    }

    @Override
    @Transactional
    public void declineInvite(Long inviteId) {
        GroupInvite invite = groupInviteMapper.selectById(inviteId);
        if (invite == null) {
            throw AppException.notFound("邀请不存在");
        }
        if (!UserContext.get().equals(invite.getInviteeId())) {
            throw AppException.forbidden("无权处理该邀请");
        }
        invite.setStatus(3);
        invite.setUpdatedAt(LocalDateTime.now());
        groupInviteMapper.updateById(invite);
        updateInviteMessageStatus(invite, "expired");
        pushInviteMessageUpdate(invite);
    }

    @Override
    public List<GroupJoinRequestDTO> getJoinRequests(String conversationId) {
        String userId = UserContext.get();
        List<ConversationJoinRequest> requests = conversationJoinRequestMapper.selectPendingForAdmin(userId,
                conversationId);
        if (requests.isEmpty()) {
            return List.of();
        }

        List<String> groupIds = requests.stream().map(ConversationJoinRequest::getConversationId).distinct().toList();
        Map<String, Conversation> groupMap = conversationMapper.selectBatchIds(groupIds).stream()
                .collect(Collectors.toMap(Conversation::getId, c -> c, (x, y) -> x));

        List<String> userIds = requests.stream().map(ConversationJoinRequest::getUserId).distinct().toList();
        Map<String, UserBriefDTO> userMap = fetchUserMap(userIds);

        return requests.stream().map(r -> {
            Conversation group = groupMap.get(r.getConversationId());
            UserBriefDTO user = userMap.get(r.getUserId());
            GroupJoinRequestDTO dto = new GroupJoinRequestDTO();
            dto.setId(String.valueOf(r.getId()));
            dto.setConversationId(r.getConversationId());
            dto.setGroupName(group != null ? group.getName() : "");
            dto.setGroupAvatar(group != null ? group.getAvatar() : "");
            dto.setUserId(r.getUserId());
            dto.setNickname(user != null ? user.getNickname() : "");
            dto.setAvatar(user != null ? user.getAvatar() : "");
            dto.setMessage(r.getMessage());
            dto.setCreatedAt(r.getCreatedAt());
            return dto;
        }).toList();
    }

    @Override
    @Transactional
    public void handleJoinRequest(Long requestId, Integer status) {
        if (status == null || (status != 1 && status != 2)) {
            throw AppException.badRequest("status 只能是 1 或 2");
        }
        ConversationJoinRequest joinRequest = conversationJoinRequestMapper.selectById(requestId);
        if (joinRequest == null || joinRequest.getStatus() != 0) {
            throw AppException.notFound("申请不存在或已处理");
        }

        Conversation group = requireGroup(joinRequest.getConversationId());
        String currentUserId = UserContext.get();
        assertGroupAdmin(group, currentUserId);

        LocalDateTime now = LocalDateTime.now();
        joinRequest.setStatus(status);
        joinRequest.setHandledBy(currentUserId);
        joinRequest.setHandledAt(now);
        joinRequest.setUpdatedAt(now);
        conversationJoinRequestMapper.updateById(joinRequest);

        if (status == 1) {
            joinGroupMember(group.getId(), joinRequest.getUserId());
            GroupInvite waitingInvite = groupInviteMapper.selectWaitingByGroupAndInvitee(group.getId(),
                    joinRequest.getUserId());
            if (waitingInvite != null) {
                finishInvite(waitingInvite);
            }
            pushGroupMemberJoined(group, joinRequest.getUserId());
        } else if (status == 2) {
            GroupInvite waitingInvite = groupInviteMapper.selectWaitingByGroupAndInvitee(group.getId(),
                    joinRequest.getUserId());
            if (waitingInvite != null) {
                waitingInvite.setStatus(0);
                waitingInvite.setUpdatedAt(now);
                groupInviteMapper.updateById(waitingInvite);
                updateInviteMessageStatus(waitingInvite, "pending");
                pushInviteMessageUpdate(waitingInvite);
            }
        }
        pushGroupJoinRequestHandled(joinRequest, group, status);
    }

    private Conversation requireGroup(String groupId) {
        Conversation group = conversationMapper.selectById(groupId);
        if (group == null || group.getType() != 2) {
            throw AppException.conversationNotFound();
        }
        return group;
    }

    private void assertGroupAdmin(Conversation group, String userId) {
        if (group.getOwnerId().equals(userId)) {
            return;
        }
        ConversationMember member = conversationMemberMapper.selectByConversationAndUser(group.getId(), userId);
        if (member == null || member.getRole() != 2) {
            throw AppException.forbidden("无权限操作");
        }
    }

    private void joinGroupMember(String groupId, String userId) {
        if (conversationMemberMapper.selectByConversationAndUser(groupId, userId) != null) {
            return;
        }
        conversationMemberMapper.batchInsert(groupId, List.of(userId), 1, LocalDateTime.now());
    }

    private void finishInvite(GroupInvite invite) {
        invite.setStatus(1);
        invite.setUpdatedAt(LocalDateTime.now());
        groupInviteMapper.updateById(invite);
        updateInviteMessageStatus(invite, "joined");
        pushInviteMessageUpdate(invite);
    }

    private String ensureDirectConversation(String userId, String friendId) {
        String conversationId = userId.compareTo(friendId) < 0
                ? userId + "_" + friendId
                : friendId + "_" + userId;
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
            conversationMemberMapper.batchInsert(conversationId, List.of(userId, friendId), 1, now);
        }
        return conversationId;
    }

    private String buildInviteContent(GroupInvite invite, Conversation group, UserBriefDTO inviter) {
        JSONObject json = new JSONObject();
        json.set("inviteId", String.valueOf(invite.getId()));
        json.set("groupId", group.getId());
        json.set("groupName", group.getName());
        json.set("groupAvatar", group.getAvatar());
        json.set("joinVerify", group.getJoinVerify() == null ? 1 : group.getJoinVerify());
        json.set("inviteStatus", mapInviteStatus(invite.getStatus()));
        json.set("inviterName", inviter.getNickname());
        return json.toString();
    }

    private void updateInviteMessageStatus(GroupInvite invite, String inviteStatus) {
        if (invite.getMessageId() == null) {
            return;
        }
        Message message = messageMapper.selectById(invite.getMessageId());
        if (message == null) {
            return;
        }
        JSONObject json = JSONUtil.parseObj(message.getContent());
        json.set("inviteStatus", inviteStatus);
        message.setContent(json.toString());
        messageMapper.updateById(message);
    }

    private void pushInviteMessageUpdate(GroupInvite invite) {
        if (invite.getMessageId() == null) {
            return;
        }
        Message message = messageMapper.selectById(invite.getMessageId());
        if (message == null) {
            return;
        }
        UserBriefDTO sender = userQueryService.getUserById(message.getSenderId());
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setContent(message.getContent());
        dto.setContentType(message.getContentType());
        dto.setConversationId(message.getConversationId());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setSenderId(message.getSenderId());
        if (sender != null) {
            dto.setSenderName(sender.getNickname());
            dto.setSenderAvatar(sender.getAvatar());
        }
        dto.setStatus(message.getStatus() == null ? 1 : message.getStatus());
        wsMessageSender.sendToUser(invite.getInviteeId(), "MESSAGE_UPDATE", dto);
        wsMessageSender.sendToUser(invite.getInviterId(), "MESSAGE_UPDATE", dto);
    }

    private String mapInviteStatus(Integer status) {
        if (status == null) {
            return "pending";
        }
        return switch (status) {
            case 1 -> "joined";
            case 2 -> "waiting";
            case 3 -> "expired";
            default -> "pending";
        };
    }

    private Map<String, UserBriefDTO> fetchUserMap(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserBriefDTO> users = userQueryService.batchGetUsers(userIds);
        if (users == null) {
            return Map.of();
        }
        return users.stream().collect(Collectors.toMap(UserBriefDTO::getUserId, u -> u, (x, y) -> x));
    }

    private List<String> listGroupAdminIds(Conversation group) {
        List<String> adminIds = new ArrayList<>();
        adminIds.add(group.getOwnerId());
        conversationMemberMapper.selectConversationAllMembers(group.getId()).stream()
                .filter(m -> m.getRole() != null && m.getRole() == 2)
                .map(ConversationMember::getUserId)
                .forEach(adminIds::add);
        return adminIds.stream().distinct().toList();
    }

    private GroupJoinRequestDTO toJoinRequestDTO(ConversationJoinRequest joinRequest, Conversation group,
            UserBriefDTO user) {
        GroupJoinRequestDTO dto = new GroupJoinRequestDTO();
        dto.setId(String.valueOf(joinRequest.getId()));
        dto.setConversationId(joinRequest.getConversationId());
        dto.setGroupName(group.getName());
        dto.setGroupAvatar(group.getAvatar());
        dto.setUserId(joinRequest.getUserId());
        dto.setNickname(user != null ? user.getNickname() : "");
        dto.setAvatar(user != null ? user.getAvatar() : "");
        dto.setMessage(joinRequest.getMessage());
        dto.setCreatedAt(joinRequest.getCreatedAt());
        return dto;
    }

    private void pushGroupJoinRequest(ConversationJoinRequest joinRequest, Conversation group) {
        UserBriefDTO user = fetchUserMap(List.of(joinRequest.getUserId())).get(joinRequest.getUserId());
        GroupJoinRequestDTO dto = toJoinRequestDTO(joinRequest, group, user);
        for (String adminId : listGroupAdminIds(group)) {
            wsMessageSender.sendToUser(adminId, "GROUP_JOIN_REQUEST", dto);
        }
    }

    private void pushGroupJoinRequestHandled(ConversationJoinRequest joinRequest, Conversation group, int status) {
        GroupJoinRequestHandledDTO dto = new GroupJoinRequestHandledDTO();
        dto.setId(String.valueOf(joinRequest.getId()));
        dto.setConversationId(group.getId());
        dto.setGroupName(group.getName());
        dto.setStatus(status);
        for (String adminId : listGroupAdminIds(group)) {
            wsMessageSender.sendToUser(adminId, "GROUP_JOIN_REQUEST_HANDLED", dto);
        }
        wsMessageSender.sendToUser(joinRequest.getUserId(), "GROUP_JOIN_REQUEST_HANDLED", dto);
    }

}

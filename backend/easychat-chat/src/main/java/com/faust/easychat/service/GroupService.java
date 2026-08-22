package com.faust.easychat.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.faust.easychat.dto.chat.AcceptGroupInviteRequest;
import com.faust.easychat.dto.chat.AcceptGroupInviteResponse;
import com.faust.easychat.dto.chat.CreateGroupRequest;
import com.faust.easychat.dto.chat.EditMemberRoleRequest;
import com.faust.easychat.dto.chat.AddGroupMembersRequest;
import com.faust.easychat.dto.chat.GroupBriefDTO;
import com.faust.easychat.dto.chat.GroupDTO;
import com.faust.easychat.dto.chat.GroupInfoDTO;
import com.faust.easychat.dto.chat.GroupJoinRequestDTO;
import com.faust.easychat.dto.chat.UpdateGroupRequest;

public interface GroupService {

    GroupInfoDTO createGroup(CreateGroupRequest request);

    String uploadGroupAvatar(String id, MultipartFile file);

    List<GroupBriefDTO> getAllMyGroups();

    List<GroupBriefDTO> getAllJoinGroups();

    GroupDTO getGroupInfo(String id);

    void updateGroup(String conversationId, UpdateGroupRequest request);

    void addMembers(String conversationId, AddGroupMembersRequest request);

    void quitGroup(String conversationId);

    void kickMember(String groupId, String userId);

    void editMemberRole(String groupId, String userId, EditMemberRoleRequest request);

    void inviteMembers(String groupId, AddGroupMembersRequest request);

    AcceptGroupInviteResponse acceptInvite(Long inviteId, AcceptGroupInviteRequest request);

    void declineInvite(Long inviteId);

    List<GroupJoinRequestDTO> getJoinRequests(String conversationId);

    void handleJoinRequest(Long requestId, Integer status);
}

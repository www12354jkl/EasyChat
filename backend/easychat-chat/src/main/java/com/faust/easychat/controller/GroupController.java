package com.faust.easychat.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.faust.easychat.dto.chat.AcceptGroupInviteRequest;
import com.faust.easychat.dto.chat.AcceptGroupInviteResponse;
import com.faust.easychat.dto.chat.AddGroupMembersRequest;
import com.faust.easychat.dto.chat.CreateGroupRequest;
import com.faust.easychat.dto.chat.EditMemberRoleRequest;
import com.faust.easychat.dto.chat.GroupInfoDTO;
import com.faust.easychat.dto.chat.HandleJoinRequestBody;
import com.faust.easychat.dto.chat.GroupJoinRequestDTO;
import com.faust.easychat.dto.chat.UpdateGroupRequest;
import com.faust.easychat.result.Result;
import com.faust.easychat.service.GroupService;
import com.faust.easychat.dto.chat.GroupBriefDTO;
import com.faust.easychat.dto.chat.GroupDTO;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "群聊")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping("/group")
    public Result<GroupInfoDTO> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return Result.success(groupService.createGroup(request));
    }

    @PutMapping("/group/avatar")
    public Result<String> uploadGroupAvatar(@RequestParam("id") String id,@RequestParam("file") MultipartFile file) {
        return Result.success(groupService.uploadGroupAvatar(id, file));
    }

    @GetMapping("/group/my")
    public Result<List<GroupBriefDTO>> getMyGroups() {
        return Result.success(groupService.getAllMyGroups());
    }

    @GetMapping("/group/all-join")
    public Result<List<GroupBriefDTO>> getAllJoinGroups() {
        return Result.success(groupService.getAllJoinGroups());
    }

    @GetMapping("/group/{id}")
    public Result<GroupDTO> getGroupInfo(@Valid @PathVariable String id) {
        return Result.success(groupService.getGroupInfo(id));
    }

    @PutMapping("/group/{id}")
    public Result<Void> updateGroup(@Valid @PathVariable String id, @RequestBody UpdateGroupRequest request) {
        groupService.updateGroup(id, request);
        return Result.success();
    }

    @PostMapping("/group/{id}/members")
    public Result<Void> addMembers(@PathVariable String id, @Valid @RequestBody AddGroupMembersRequest request) {
        groupService.addMembers(id, request);
        return Result.success();
    }

    @DeleteMapping("/group/{id}")
    public Result<Void> quitGroup(@PathVariable("id") String conversationId) {
        groupService.quitGroup(conversationId);
        return Result.success();
    }

    @DeleteMapping("/group/{groupId}/member/{userId}")
    public Result<Void> kickMember(@PathVariable String groupId, @PathVariable String userId) {
        groupService.kickMember(groupId, userId);
        return Result.success();
    }

    @PutMapping("/group/{groupId}/member/{userId}/role")
    public Result<Void> editMembers(@PathVariable String groupId,@PathVariable String userId,@RequestBody EditMemberRoleRequest request) {
        groupService.editMemberRole(groupId, userId, request);
        return Result.success();
    }

    @PostMapping("/group/{groupId}/invite")
    public Result<Void> inviteMembers(@PathVariable String groupId,
            @Valid @RequestBody AddGroupMembersRequest request) {
        groupService.inviteMembers(groupId, request);
        return Result.success();
    }

    @PostMapping("/group/invite/{inviteId}/accept")
    public Result<AcceptGroupInviteResponse> acceptInvite(@PathVariable Long inviteId,
            @RequestBody(required = false) AcceptGroupInviteRequest request) {
        return Result.success(groupService.acceptInvite(inviteId, request));
    }

    @DeleteMapping("/group/invite/{inviteId}")
    public Result<Void> declineInvite(@PathVariable Long inviteId) {
        groupService.declineInvite(inviteId);
        return Result.success();
    }

    @GetMapping("/group/join-requests")
    public Result<List<GroupJoinRequestDTO>> getJoinRequests(
            @RequestParam(required = false) String conversationId) {
        return Result.success(groupService.getJoinRequests(conversationId));
    }

    @PutMapping("/group/join-request/{id}")
    public Result<Void> handleJoinRequest(@PathVariable Long id,
            @RequestBody HandleJoinRequestBody body) {
        groupService.handleJoinRequest(id, body.getStatus());
        return Result.success();
    }
}

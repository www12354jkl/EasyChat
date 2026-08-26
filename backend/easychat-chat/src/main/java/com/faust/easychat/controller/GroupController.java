package com.faust.easychat.controller;

import java.util.List;

import org.springframework.http.MediaType;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "群聊", description = "建群、成员、邀请、入群申请")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @Operation(summary = "创建群聊")
    @PostMapping("/group")
    public Result<GroupInfoDTO> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return Result.success(groupService.createGroup(request));
    }

    @Operation(summary = "上传群头像")
    @PutMapping(value = "/group/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadGroupAvatar(
            @Parameter(description = "群会话 id", required = true) @RequestParam("id") String id,
            @Parameter(description = "头像文件", required = true) @RequestParam("file") MultipartFile file) {
        return Result.success(groupService.uploadGroupAvatar(id, file));
    }

    @Operation(summary = "我创建的群")
    @GetMapping("/group/my")
    public Result<List<GroupBriefDTO>> getMyGroups() {
        return Result.success(groupService.getAllMyGroups());
    }

    @Operation(summary = "我加入的群")
    @GetMapping("/group/all-join")
    public Result<List<GroupBriefDTO>> getAllJoinGroups() {
        return Result.success(groupService.getAllJoinGroups());
    }

    @Operation(summary = "群详情")
    @GetMapping("/group/{id}")
    public Result<GroupDTO> getGroupInfo(
            @Parameter(description = "群会话 id") @PathVariable String id) {
        return Result.success(groupService.getGroupInfo(id));
    }

    @Operation(summary = "更新群资料")
    @PutMapping("/group/{id}")
    public Result<Void> updateGroup(
            @Parameter(description = "群会话 id") @PathVariable String id,
            @RequestBody UpdateGroupRequest request) {
        groupService.updateGroup(id, request);
        return Result.success();
    }

    @Operation(summary = "直接拉人进群")
    @PostMapping("/group/{id}/members")
    public Result<Void> addMembers(
            @Parameter(description = "群会话 id") @PathVariable String id,
            @Valid @RequestBody AddGroupMembersRequest request) {
        groupService.addMembers(id, request);
        return Result.success();
    }

    @Operation(summary = "退出群聊")
    @DeleteMapping("/group/{id}")
    public Result<Void> quitGroup(
            @Parameter(description = "群会话 id") @PathVariable("id") String conversationId) {
        groupService.quitGroup(conversationId);
        return Result.success();
    }

    @Operation(summary = "踢出群成员")
    @DeleteMapping("/group/{groupId}/member/{userId}")
    public Result<Void> kickMember(
            @Parameter(description = "群会话 id") @PathVariable String groupId,
            @Parameter(description = "被踢用户 id") @PathVariable String userId) {
        groupService.kickMember(groupId, userId);
        return Result.success();
    }

    @Operation(summary = "修改成员角色", description = "role：1=普通成员，2=管理员")
    @PutMapping("/group/{groupId}/member/{userId}/role")
    public Result<Void> editMembers(
            @Parameter(description = "群会话 id") @PathVariable String groupId,
            @Parameter(description = "成员用户 id") @PathVariable String userId,
            @RequestBody EditMemberRoleRequest request) {
        groupService.editMemberRole(groupId, userId, request);
        return Result.success();
    }

    @Operation(summary = "邀请入群")
    @PostMapping("/group/{groupId}/invite")
    public Result<Void> inviteMembers(
            @Parameter(description = "群会话 id") @PathVariable String groupId,
            @Valid @RequestBody AddGroupMembersRequest request) {
        groupService.inviteMembers(groupId, request);
        return Result.success();
    }

    @Operation(summary = "接受群邀请")
    @PostMapping("/group/invite/{inviteId}/accept")
    public Result<AcceptGroupInviteResponse> acceptInvite(
            @Parameter(description = "邀请 id") @PathVariable Long inviteId,
            @RequestBody(required = false) AcceptGroupInviteRequest request) {
        return Result.success(groupService.acceptInvite(inviteId, request));
    }

    @Operation(summary = "拒绝群邀请")
    @DeleteMapping("/group/invite/{inviteId}")
    public Result<Void> declineInvite(
            @Parameter(description = "邀请 id") @PathVariable Long inviteId) {
        groupService.declineInvite(inviteId);
        return Result.success();
    }

    @Operation(summary = "入群申请列表")
    @GetMapping("/group/join-requests")
    public Result<List<GroupJoinRequestDTO>> getJoinRequests(
            @Parameter(description = "群会话 id，不传则返回当前用户可见的全部申请") @RequestParam(required = false) String conversationId) {
        return Result.success(groupService.getJoinRequests(conversationId));
    }

    @Operation(summary = "处理入群申请", description = "status：1=同意，2=拒绝")
    @PutMapping("/group/join-request/{id}")
    public Result<Void> handleJoinRequest(
            @Parameter(description = "申请 id") @PathVariable Long id,
            @RequestBody HandleJoinRequestBody body) {
        groupService.handleJoinRequest(id, body.getStatus());
        return Result.success();
    }
}

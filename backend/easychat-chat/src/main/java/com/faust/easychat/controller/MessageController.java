package com.faust.easychat.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import com.faust.easychat.context.UserContext;
import com.faust.easychat.dto.chat.ConversationResponse;
import com.faust.easychat.dto.chat.MessageDTO;
import com.faust.easychat.dto.chat.SendMessageRequest;
import com.faust.easychat.dto.chat.SingleConversationRequest;
import com.faust.easychat.netty.WsSessionManager;
import com.faust.easychat.result.PageResult;
import com.faust.easychat.result.Result;
import com.faust.easychat.service.MessageService;
import com.faust.easychat.service.WsTicketService;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "消息处理", description = "发消息、会话、历史记录、在线状态、文件上传")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    private final WsSessionManager wsSessionManager;

    private final WsTicketService wsTicketService;

    @Operation(summary = "申请 WebSocket 连接票据", description = "30 秒内一次性使用，连接地址使用 ticket 而不是登录令牌")
    @PostMapping("/ws-ticket")
    public Result<Map<String, String>> issueWsTicket() {
        return Result.success(Map.of("ticket", wsTicketService.issue(UserContext.get())));
    }

    @Operation(summary = "发送消息")
    @PostMapping("/message")
    public Result<MessageDTO> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        return Result.success(messageService.sendMessage(request));
    }

    @Operation(summary = "撤回消息", description = "仅发送者可在发送后 2 分钟内撤回文本/图片/文件消息")
    @PostMapping("/message/{messageId}/recall")
    public Result<MessageDTO> recallMessage(
            @Parameter(description = "消息 id") @PathVariable String messageId) {
        return Result.success(messageService.recallMessage(messageId));
    }

    @Operation(summary = "会话消息历史")
    @GetMapping("/messages")
    public Result<PageResult<MessageDTO>> getChatHistory(
        @Parameter(description = "会话 id", required = true) @RequestParam String conversationId,
        @Parameter(description = "按标题筛选") @RequestParam(required = false) String title,
        @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
        @Parameter(description = "每页条数") @RequestParam(defaultValue = "30") int size
    ) {
        return Result.success(messageService.getChatHistory(conversationId, title, page, size));
    }

    @Operation(summary = "我的会话列表")
    @GetMapping("/conversations")
    public Result<List<ConversationResponse>> getChatList() {
        return Result.success(messageService.getChatList());
    }

    @Operation(summary = "创建/获取单聊会话")
    @PostMapping("/conversation/direct")
    public Result<ConversationResponse> createSingleConversation(@RequestBody SingleConversationRequest request) {
        return Result.success(messageService.createFriendConversation(request));
    }

    @Operation(summary = "批量查询在线状态", description = "userIds 逗号分隔")
    @GetMapping("/online")
    public Result<Map<String, Boolean>> getOnlineStatus(
            @Parameter(description = "用户 id，逗号分隔", example = "1,2,3") @RequestParam String userIds) {
        Map<String, Boolean> map = new HashMap<>();
        if (userIds != null && !userIds.isBlank()) {
            for (String id : userIds.split(",")) {
                String uid = id.trim();
                if (!uid.isEmpty()) {
                    map.put(uid, wsSessionManager.isOnline(uid));
                }
            }
        }
        return Result.success(map);
    }

    @Operation(summary = "上传聊天文件")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadChatFile(
            @Parameter(description = "文件", required = true) @RequestParam("file") MultipartFile file) {
        return Result.success(messageService.upload(file));
    }

    @Operation(summary = "清空会话消息（仅自己可见，不影响对方）")
    @DeleteMapping("/conversation/{conversationId}/messages")
    public Result<Integer> deleteChatHistory(
            @Parameter(description = "会话 id") @PathVariable String conversationId) {
        return Result.success(messageService.deleteChatHistory(conversationId));
    }

    @Operation(summary = "标记会话已读")
    @PutMapping("/conversation/{conversationId}/read")
    public Result<Void> markRead(
            @Parameter(description = "会话 id") @PathVariable String conversationId) {
        messageService.markRead(conversationId);
        return Result.success();
    }
}

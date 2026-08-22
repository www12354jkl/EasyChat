package com.faust.easychat.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import com.faust.easychat.dto.chat.ConversationResponse;
import com.faust.easychat.dto.chat.MessageDTO;
import com.faust.easychat.dto.chat.SendMessageRequest;
import com.faust.easychat.dto.chat.SingleConversationRequest;
import com.faust.easychat.netty.WsSessionManager;
import com.faust.easychat.result.PageResult;
import com.faust.easychat.result.Result;
import com.faust.easychat.service.MessageService;


import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "消息处理")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    private final WsSessionManager wsSessionManager;

    @PostMapping("/message")
    public Result<MessageDTO> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        return Result.success(messageService.sendMessage(request));
    }

    @GetMapping("/messages")
    public Result<PageResult<MessageDTO>> getChatHistory(
        @RequestParam(required = true) String conversationId,
        @RequestParam(required = false) String title,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "30") int size
    ) {
        return Result.success(messageService.getChatHistory(conversationId, title, page, size));
    }

    @GetMapping("/conversations")
    public Result<List<ConversationResponse>> getChatList() {
        return Result.success(messageService.getChatList());
    }

    @PostMapping("/conversation/direct")
    public Result<ConversationResponse> createSingleConversation(@RequestBody SingleConversationRequest request) {
        return Result.success(messageService.createFriendConversation(request));
    }

    /** 批量查询在线状态（前端 GET /api/chat/online?userIds=id1,id2,...） */
    @GetMapping("/online")
    public Result<Map<String, Boolean>> getOnlineStatus(@RequestParam String userIds) {
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

    @PostMapping("/upload")
    public Result<String> uploadChatFile(@RequestParam("file") MultipartFile file) {
        return Result.success(messageService.upload(file));
    }

    @DeleteMapping("/conversation/{conversationId}/messages")
    public Result<Integer> deleteChatHistory(@PathVariable String conversationId) {
        return Result.success(messageService.deleteChatHistory(conversationId));
    }

    @PutMapping("/conversation/{conversationId}/read")
    public Result<Void> markRead(@PathVariable String conversationId) {
        messageService.markRead(conversationId);
        return Result.success();
    }
}

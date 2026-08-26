package com.faust.easychat.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.faust.easychat.result.Result;
import com.faust.easychat.service.MessageService;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;

@Hidden
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalConversationController {

    private final MessageService messageService;

    /** 删好友后清理两人单聊会话（消息 + 成员 + 会话记录） */
    @DeleteMapping("/conversation/direct")
    public Result<Void> removeDirectConversation(@RequestParam String userId, @RequestParam String friendId) {
        messageService.removeDirectConversation(userId, friendId);
        return Result.success();
    }
}

package com.faust.easychat.dubbo;

import com.faust.easychat.api.chat.ConversationDeleteService;
import com.faust.easychat.service.MessageService;

import org.apache.dubbo.config.annotation.DubboService;

import lombok.RequiredArgsConstructor;

@DubboService
@RequiredArgsConstructor
public class ConversationDeleteServiceImpl implements ConversationDeleteService {

    private final MessageService messageService;

    @Override
    public void removeDirectConversation(String userId, String friendId) {
        messageService.removeDirectConversation(userId, friendId);
    }

}

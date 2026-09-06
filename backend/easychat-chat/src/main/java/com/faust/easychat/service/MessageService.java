package com.faust.easychat.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.faust.easychat.dto.chat.ConversationResponse;
import com.faust.easychat.dto.chat.MessageDTO;
import com.faust.easychat.dto.chat.SendMessageRequest;
import com.faust.easychat.dto.chat.SingleConversationRequest;
import com.faust.easychat.result.PageResult;



public interface MessageService {

    MessageDTO sendMessage(SendMessageRequest request);

    PageResult<MessageDTO> getChatHistory(String conversationId, String title, int page, int size);

    List<ConversationResponse> getChatList();

    ConversationResponse createFriendConversation(SingleConversationRequest request);

    String upload(MultipartFile file);

    Integer deleteChatHistory(String conversationId);

    void markRead(String conversationId);

    MessageDTO recallMessage(String messageId);

    void removeDirectConversation(String userId, String friendId);
}

package com.faust.easychat.api.chat;

public interface ConversationDeleteService {

    void removeDirectConversation(String userId, String friendId);

}

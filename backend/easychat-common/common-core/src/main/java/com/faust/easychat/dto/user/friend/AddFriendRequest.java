package com.faust.easychat.dto.user.friend;

import lombok.Data;

@Data
public class AddFriendRequest {

    private String toUserId;

    private String message;
    
}

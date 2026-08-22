package com.faust.easychat.dto.chat;

import lombok.Data;

@Data
public class UpdateGroupRequest {

    private String name;

    private String announcement;

    private Integer joinVerify;
    
}

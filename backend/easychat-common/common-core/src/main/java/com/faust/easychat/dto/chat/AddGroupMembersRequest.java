package com.faust.easychat.dto.chat;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class AddGroupMembersRequest {

    @NotEmpty(message = "成员不能为空")
    private List<String> memberIds;
}

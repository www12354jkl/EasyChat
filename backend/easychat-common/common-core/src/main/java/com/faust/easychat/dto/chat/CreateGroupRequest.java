package com.faust.easychat.dto.chat;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateGroupRequest {

    @NotBlank(message = "群名称不能为空")
    @Size(max = 64, message = "群名称过长")
    private String name;

    private String avatar;

    private String announcement;

    private Integer joinVerify;

    /** 群成员ID（不含创建者，创建者自动加入） */
    @NotEmpty(message = "群成员不能为空")
    private List<String> memberIds;

}

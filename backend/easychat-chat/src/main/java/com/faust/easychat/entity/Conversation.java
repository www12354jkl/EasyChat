package com.faust.easychat.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    // 1单聊 2群聊 3群邀请
    private Integer type;

    private String name;

    private String avatar;

    private String ownerId;

    private String announcement;

    // 入群验证：0=直接加入 1=需要管理员同意
    private Integer joinVerify;

    // 状态：1=正常 0=已解散
    private Integer status;

    private String lastMessage;

    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}

package com.faust.easychat.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("conversation_member")
public class ConversationMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationId;

    private String userId;

    // 1=普通成员 2=管理员
    private Integer role;

    private LocalDateTime createdAt;

    private LocalDateTime lastReadAt;

}

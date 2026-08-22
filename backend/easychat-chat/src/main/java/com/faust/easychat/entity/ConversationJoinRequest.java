package com.faust.easychat.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("conversation_join_request")
public class ConversationJoinRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationId;

    private String userId;

    private String message;

    // 0=待处理 1=同意 2=拒绝
    private Integer status;

    private String handledBy;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}

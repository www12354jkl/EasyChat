package com.faust.easychat.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("friend_request")
public class FriendRequest {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String fromUserId;

    private String toUserId;

    private String message;

    // 0待处理 1同意 2拒绝
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.faust.easychat.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("friend")
public class Friend {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String friendId;

    private LocalDateTime createdAt;
}

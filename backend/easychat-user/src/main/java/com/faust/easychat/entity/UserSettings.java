package com.faust.easychat.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("user_settings")
public class UserSettings {
    
    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;
    
    //1验证0不验证
    private Integer friendVerify;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}

package com.faust.easychat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String email;

    private String password;

    private String nickname;

    private String avatar;

    /**
     * 性别：0=保密（默认） 1=男 2=女
     */
    private Integer gender;

    private String region;

    /**
     * 行政区划代码(GB/T 2260)：省2位/市4位/区县6位
     */
    private String regionCode;

    private String signature;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

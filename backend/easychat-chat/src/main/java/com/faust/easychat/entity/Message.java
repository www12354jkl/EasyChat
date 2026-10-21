package com.faust.easychat.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String conversationId;

    private String senderId;

    /** 客户端消息号，空表示旧数据 */
    private String clientMsgId;

    // 1=文本 2=图片 3=文件 4=群邀请卡片
    private Integer contentType;

    private String content;

    /** 1=正常 2=已撤回 */
    private Integer status;

    private LocalDateTime createdAt;


}

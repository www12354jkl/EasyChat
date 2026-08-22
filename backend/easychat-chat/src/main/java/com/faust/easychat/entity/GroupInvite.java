package com.faust.easychat.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("group_invite")
public class GroupInvite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String groupId;

    private String inviteeId;

    private String inviterId;

    /** 邀请消息所在的单聊会话 id */
    private String inviteConversationId;

    private String messageId;

    /** 0=待处理 1=已加入 2=待管理员审批 3=已拒绝/失效 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

-- 群邀请表（在 easychat_chat 库执行）
CREATE TABLE IF NOT EXISTS group_invite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL COMMENT '目标群 conversation.id',
    invitee_id VARCHAR(36) NOT NULL COMMENT '被邀请人',
    inviter_id VARCHAR(36) NOT NULL COMMENT '邀请人',
    invite_conversation_id VARCHAR(73) NOT NULL COMMENT '邀请消息所在的单聊会话 id',
    message_id VARCHAR(36) NULL COMMENT '群邀请卡片消息 id',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=待处理 1=已加入 2=待管理员审批 3=已拒绝/失效',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_invite_conversation (invite_conversation_id),
    KEY idx_group_invitee (group_id, invitee_id),
    KEY idx_invitee_status (invitee_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- conversation.type: 1=单聊 2=群聊 3=群邀请

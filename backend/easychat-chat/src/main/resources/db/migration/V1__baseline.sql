-- easychat_chat 库基线 schema（已有表则跳过）
CREATE TABLE IF NOT EXISTS conversation (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    type TINYINT NOT NULL COMMENT '1单聊 2群聊 3群邀请',
    name VARCHAR(128) NULL,
    avatar VARCHAR(512) NULL,
    owner_id VARCHAR(36) NULL,
    announcement VARCHAR(1024) NULL,
    join_verify TINYINT NULL DEFAULT 0 COMMENT '0直接加入 1需管理员同意',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0已解散',
    last_message VARCHAR(512) NULL,
    last_message_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_conversation_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS conversation_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    role TINYINT NOT NULL DEFAULT 1 COMMENT '1成员 2管理员',
    created_at DATETIME NOT NULL,
    last_read_at DATETIME NULL,
    cleared_at DATETIME NULL COMMENT '单方面清空聊天记录时间',
    UNIQUE KEY uk_conversation_member (conversation_id, user_id),
    KEY idx_member_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS message (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    sender_id VARCHAR(36) NOT NULL,
    content_type TINYINT NOT NULL COMMENT '1文本 2图片 3文件 4群邀请卡片',
    content TEXT NOT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 2已撤回',
    created_at DATETIME NOT NULL,
    KEY idx_message_conversation_created (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS conversation_join_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    message VARCHAR(256) NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1同意 2拒绝',
    handled_by VARCHAR(36) NULL,
    handled_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_join_request_conversation (conversation_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS group_invite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL COMMENT '目标群 conversation.id',
    invitee_id VARCHAR(36) NOT NULL,
    inviter_id VARCHAR(36) NOT NULL,
    invite_conversation_id VARCHAR(73) NOT NULL COMMENT '邀请消息所在单聊会话 id',
    message_id VARCHAR(36) NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1已加入 2待审批 3已拒绝/失效',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_invite_conversation (invite_conversation_id),
    KEY idx_group_invitee (group_id, invitee_id),
    KEY idx_invitee_status (invitee_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

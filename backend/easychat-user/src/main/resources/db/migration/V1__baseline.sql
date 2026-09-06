-- easychat_user 库基线 schema（已有表则跳过）
CREATE TABLE IF NOT EXISTS `user` (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    email VARCHAR(128) NOT NULL,
    password VARCHAR(128) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    avatar VARCHAR(512) NULL,
    gender TINYINT NULL DEFAULT 0 COMMENT '0保密 1男 2女',
    region VARCHAR(64) NULL,
    region_code VARCHAR(12) NULL,
    signature VARCHAR(256) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    friend_verify TINYINT NOT NULL DEFAULT 1 COMMENT '1需验证 0允许任何人',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_user_settings_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS friend (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    friend_id VARCHAR(36) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_friend_pair (user_id, friend_id),
    KEY idx_friend_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS friend_request (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    from_user_id VARCHAR(36) NOT NULL,
    to_user_id VARCHAR(36) NOT NULL,
    message VARCHAR(256) NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1同意 2拒绝',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_friend_request_to_status (to_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 已有库升级：会话成员增加 cleared_at，用于单方面清空聊天记录（只改自己的可见窗口，不删 message 表）
-- 在 easychat_chat 库执行
ALTER TABLE conversation_member
    ADD COLUMN cleared_at DATETIME NULL COMMENT '该用户清空聊天记录的时间，仅对自己可见' AFTER last_read_at;

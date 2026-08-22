-- 已有库升级：单聊会话 id 为 uuid_uuid（73 字符），并去掉 invite_conversation_id 唯一约束
ALTER TABLE group_invite MODIFY COLUMN invite_conversation_id VARCHAR(73) NOT NULL COMMENT '邀请消息所在的单聊会话 id';
ALTER TABLE group_invite DROP INDEX uk_invite_conversation;
ALTER TABLE group_invite ADD KEY idx_invite_conversation (invite_conversation_id);

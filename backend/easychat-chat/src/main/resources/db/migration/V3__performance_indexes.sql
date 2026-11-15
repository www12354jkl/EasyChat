-- 性能索引（已存在则跳过）
SET @idx_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'message' AND INDEX_NAME = 'idx_message_conversation_created'
);
SET @ddl := IF(
    @idx_exists = 0,
    'CREATE INDEX idx_message_conversation_created ON message (conversation_id, created_at)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'conversation_member' AND INDEX_NAME = 'idx_member_user_conversation'
);
SET @ddl := IF(
    @idx_exists = 0,
    'CREATE INDEX idx_member_user_conversation ON conversation_member (user_id, conversation_id)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

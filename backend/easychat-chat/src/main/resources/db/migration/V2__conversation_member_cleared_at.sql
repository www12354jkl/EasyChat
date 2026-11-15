-- 已有库升级：conversation_member.cleared_at（新库 V1 已包含则跳过）
SET @col_exists := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'conversation_member'
      AND COLUMN_NAME = 'cleared_at'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE conversation_member ADD COLUMN cleared_at DATETIME NULL COMMENT ''单方面清空聊天记录时间'' AFTER last_read_at',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

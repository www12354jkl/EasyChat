-- 放宽会话 ID 长度至 VARCHAR(73) NOT NULL（兼容 32/36 位 UUID 拼接的单聊会话 id：uidA_uidB）
SET @col_len := (
    SELECT CHARACTER_MAXIMUM_LENGTH
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'conversation'
      AND COLUMN_NAME = 'id'
);
SET @ddl := IF(
    @col_len IS NOT NULL AND @col_len < 73,
    'ALTER TABLE conversation MODIFY COLUMN id VARCHAR(73) NOT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_len := (
    SELECT CHARACTER_MAXIMUM_LENGTH
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'conversation_member'
      AND COLUMN_NAME = 'conversation_id'
);
SET @ddl := IF(
    @col_len IS NOT NULL AND @col_len < 73,
    'ALTER TABLE conversation_member MODIFY COLUMN conversation_id VARCHAR(73) NOT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_len := (
    SELECT CHARACTER_MAXIMUM_LENGTH
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'message'
      AND COLUMN_NAME = 'conversation_id'
);
SET @ddl := IF(
    @col_len IS NOT NULL AND @col_len < 73,
    'ALTER TABLE message MODIFY COLUMN conversation_id VARCHAR(73) NOT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

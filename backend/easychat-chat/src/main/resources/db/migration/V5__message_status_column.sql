-- message.status 兜底：V4 未生效或历史库缺列时自动补齐
SET @col_exists := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'message'
      AND COLUMN_NAME = 'status'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE message ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT ''1正常 2已撤回'' AFTER content',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

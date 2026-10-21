ALTER TABLE message
    ADD COLUMN client_msg_id VARCHAR(64) NULL COMMENT '客户端消息号，同一发送者重复提交只保留一条' AFTER sender_id;

CREATE UNIQUE INDEX uk_message_sender_client_msg ON message (sender_id, client_msg_id);

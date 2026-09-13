-- 添加 reasoning_content 字段用于存储 AI 推理过程
ALTER TABLE t_chat_message ADD COLUMN IF NOT EXISTS reasoning_content TEXT;

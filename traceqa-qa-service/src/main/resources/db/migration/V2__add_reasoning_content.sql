-- 添加推理过程内容字段（DeepSeek R1 等推理模型的思考链）
ALTER TABLE t_chat_message ADD COLUMN IF NOT EXISTS reasoning_content TEXT;

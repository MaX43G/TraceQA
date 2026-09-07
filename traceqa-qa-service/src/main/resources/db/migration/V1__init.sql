-- 溯知 / TraceQA 问答服务初始表结构（Flyway V1）
-- t_chat_session、t_chat_message、t_system_prompt

CREATE TABLE IF NOT EXISTS t_chat_session
(
    id                BIGINT NOT NULL PRIMARY KEY,
    user_id           BIGINT NOT NULL,
    title             VARCHAR(128) DEFAULT '新对话',
    knowledge_base_id BIGINT       DEFAULT NULL,
    pinned            SMALLINT     DEFAULT 0,
    status            SMALLINT     DEFAULT 1,
    create_time       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT     DEFAULT 0
);

-- content/thinking_trace/references 用 TEXT，避免引用 JSON 超长
CREATE TABLE IF NOT EXISTS t_chat_message
(
    id             BIGINT       NOT NULL PRIMARY KEY,
    session_id     BIGINT       NOT NULL,
    role           VARCHAR(16)  NOT NULL,
    content        TEXT,
    thinking_trace TEXT,
    "references"   TEXT,
    latency_ms     BIGINT   DEFAULT 0,
    status         SMALLINT DEFAULT 1,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_system_prompt
(
    id          BIGINT       NOT NULL PRIMARY KEY,
    scenario    VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) DEFAULT '',
    content     TEXT,
    enabled     SMALLINT     DEFAULT 1,
    remark      VARCHAR(255) DEFAULT '',
    create_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_msg_session ON t_chat_message (session_id);
CREATE INDEX IF NOT EXISTS idx_session_user ON t_chat_session (user_id);
CREATE INDEX IF NOT EXISTS idx_prompt_scenario ON t_system_prompt (scenario);

-- 溯知 / TraceQA 管理服务初始表结构（Flyway V1）
-- t_announcement

CREATE TABLE IF NOT EXISTS t_announcement
(
    id          BIGINT    NOT NULL PRIMARY KEY,
    title       VARCHAR(128) DEFAULT '',
    content     TEXT,
    enabled     SMALLINT  DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT  DEFAULT 0
);

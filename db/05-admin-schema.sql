-- ============================================================
-- 管理服务数据库结构（traceqa_admin）
-- 表：t_announcement
-- ============================================================
USE traceqa_admin;

-- 系统公告表
CREATE TABLE IF NOT EXISTS t_announcement
(
    id          BIGINT NOT NULL PRIMARY KEY,
    title       VARCHAR(128) DEFAULT '',
    content     TEXT,
    enabled     TINYINT      DEFAULT 1,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      DEFAULT 0
);
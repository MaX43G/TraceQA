-- ============================================================
-- 用户服务数据库结构（traceqa_user）
-- 表：t_role、t_user
-- ============================================================
USE traceqa_user;

-- 角色表（RBAC）
CREATE TABLE IF NOT EXISTS t_role
(
    id          BIGINT      NOT NULL PRIMARY KEY,
    code        VARCHAR(32) NOT NULL,
    name        VARCHAR(64) NOT NULL,
    permissions VARCHAR(1024) DEFAULT '',
    description VARCHAR(255)  DEFAULT '',
    create_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME      DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT       DEFAULT 0
);

-- 用户表
CREATE TABLE IF NOT EXISTS t_user
(
    id          BIGINT       NOT NULL PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(128) NOT NULL,
    nickname    VARCHAR(64)  DEFAULT '',
    role_code   VARCHAR(32)  DEFAULT 'USER',
    status      TINYINT      DEFAULT 1,
    avatar      VARCHAR(512) DEFAULT '',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      DEFAULT 0
);

CREATE INDEX idx_role_code ON t_role (code);
CREATE INDEX idx_user_username ON t_user (username);
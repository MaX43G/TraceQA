#!/bin/bash
# ============================================================
# 溯知 / TraceQA 微服务数据库初始化
# 创建各微服务独立数据库并授权（由 PostgreSQL 容器 docker-entrypoint-initdb.d 以 postgres 用户执行）
# ============================================================
set -e

PG_USER="${PG_USER:-traceqa}"
PG_PASSWORD="${PG_PASSWORD:-traceqa123}"

# 创建应用用户
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE USER ${PG_USER} WITH PASSWORD '${PG_PASSWORD}';
EOSQL

# 创建各微服务独立数据库并授权
for db in traceqa_user traceqa_kb traceqa_qa traceqa_admin; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE ${db} OWNER ${PG_USER};
    GRANT ALL PRIVILEGES ON DATABASE ${db} TO ${PG_USER};
EOSQL
done

# 授予 postgres 用户创建数据库权限（供 Flyway baseline 使用）
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" -c "ALTER USER ${PG_USER} CREATEDB;"

echo "TraceQA microservices databases initialized (PostgreSQL)."

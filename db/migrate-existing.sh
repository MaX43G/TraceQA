#!/bin/bash
# ============================================================
# 溯知 / TraceQA 微服务数据库迁移（针对已存在的数据卷）
#
# docker-entrypoint-initdb.d 仅在 MySQL 数据卷首次初始化时执行；
# 若此前已运行过单体版本（traceqa 库已存在），需手动执行本脚本补建各微服务独立库并授权。
#
# 用法（在宿主机执行）：
#   docker compose exec mysql bash /docker-entrypoint-initdb.d/migrate-existing.sh
# ============================================================
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
MYSQL_USER="${MYSQL_USER:-traceqa}"

echo "创建各微服务数据库并授权..."
for db in traceqa_user traceqa_kb traceqa_qa traceqa_admin; do
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "CREATE DATABASE IF NOT EXISTS \`${db}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "GRANT ALL PRIVILEGES ON \`${db}\`.* TO '${MYSQL_USER}'@'%';"
done
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "FLUSH PRIVILEGES;"

echo "创建各微服务表结构..."
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" < "$DIR/02-user-schema.sql"
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" < "$DIR/03-kb-schema.sql"
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" < "$DIR/04-qa-schema.sql"
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" < "$DIR/05-admin-schema.sql"

echo "TraceQA 微服务数据库迁移完成。"
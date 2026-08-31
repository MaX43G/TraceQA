#!/bin/bash
# ============================================================
# 溯知 / TraceQA 微服务数据库初始化
# 创建各微服务独立数据库并授权（由 MySQL 容器 docker-entrypoint-initdb.d 以 root 执行）
# ============================================================
set -e

MYSQL_USER="${MYSQL_USER:-traceqa}"

for db in traceqa_user traceqa_kb traceqa_qa traceqa_admin; do
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "CREATE DATABASE IF NOT EXISTS \`${db}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "GRANT ALL PRIVILEGES ON \`${db}\`.* TO '${MYSQL_USER}'@'%';"
done

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "FLUSH PRIVILEGES;"
echo "TraceQA microservices databases initialized."
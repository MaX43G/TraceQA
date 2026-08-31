#!/bin/bash
# ============================================================
# 溯知 / TraceQA 微服务数据迁移（旧单体 traceqa 库 -> 各微服务独立库）
#
# 前置：先执行 migrate-existing.sh 完成建库/授权/建表。
# 本脚本将旧单体 `traceqa` 库中各表的数据复制到对应的微服务独立库，
# 复制成功后删除旧 `traceqa` 数据库（含全部旧表）。
#
# 用法（在宿主机执行）：
#   docker compose exec mysql bash /docker-entrypoint-initdb.d/migrate-existing.sh
#   docker compose exec mysql bash /docker-entrypoint-initdb.d/migrate-data.sh
# ============================================================
set -e

SRC_DB="${MYSQL_DATABASE:-traceqa}"   # 旧单体数据库名

echo "开始数据迁移：$SRC_DB -> traceqa_user/kb/qa/admin"

# copy_table <源库> <源表> <目标库> <目标表>
copy_table() {
  local src_db="$1" src_tbl="$2" dst_db="$3" dst_tbl="$4"
  local src="$src_db.$src_tbl" dst="$dst_db.$dst_tbl"

  # 源表不存在则跳过
  local src_exists
  src_exists=$(mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$src_db' AND table_name='$src_tbl';")
  if [ "$src_exists" != "1" ]; then
    echo "跳过（源表不存在）：$src"
    return
  fi

  # 清空目标表（避免与默认种子数据冲突），再复制
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "TRUNCATE TABLE $dst;"
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "INSERT INTO $dst SELECT * FROM $src;"
  echo "已复制：$src -> $dst"
}

# 旧单体库各表 -> 对应微服务独立库
copy_table "$SRC_DB" t_role           traceqa_user t_role
copy_table "$SRC_DB" t_user           traceqa_user t_user
copy_table "$SRC_DB" t_knowledge_base traceqa_kb   t_knowledge_base
copy_table "$SRC_DB" t_document       traceqa_kb   t_document
copy_table "$SRC_DB" t_chat_session   traceqa_qa   t_chat_session
copy_table "$SRC_DB" t_chat_message   traceqa_qa   t_chat_message
copy_table "$SRC_DB" t_system_prompt  traceqa_qa   t_system_prompt
copy_table "$SRC_DB" t_announcement   traceqa_admin t_announcement

echo "数据迁移完成，删除旧数据库 $SRC_DB（含全部旧表）..."
mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "DROP DATABASE IF EXISTS \`${SRC_DB}\`;"
echo "旧数据库 $SRC_DB 已删除。"
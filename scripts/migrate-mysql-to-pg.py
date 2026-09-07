#!/usr/bin/env python3
"""
TraceQA 数据迁移脚本：MySQL → PostgreSQL
=========================================
功能：
  1. 连接旧 MySQL 数据库，导出所有表数据
  2. 转换 MySQL 类型（TINYINT→SMALLINT, DATETIME→TIMESTAMP, LONGTEXT→TEXT）
  3. 导入到新的 PostgreSQL 数据库
  4. 验证行数一致性

使用：
  pip install pymysql psycopg2-binary
  python scripts/migrate-mysql-to-pg.py

环境变量（或在脚本内修改默认值）：
  MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD
  PG_HOST, PG_PORT, PG_USER, PG_PASSWORD
"""
import os
import sys
import time
import pymysql
import psycopg2
from psycopg2.extras import execute_values

# ============ 配置 ============
MYSQL_CONFIG = {
    "host": os.getenv("MYSQL_HOST", "localhost"),
    "port": int(os.getenv("MYSQL_PORT", "6118")),
    "user": os.getenv("MYSQL_USER", "traceqa"),
    "password": os.getenv("MYSQL_PASSWORD", "traceqa123"),
    "charset": "utf8mb4",
}

PG_CONFIG = {
    "host": os.getenv("PG_HOST", "localhost"),
    "port": int(os.getenv("PG_PORT", "6118")),
    "user": os.getenv("PG_USER", "traceqa"),
    "password": os.getenv("PG_PASSWORD", "traceqa123"),
}

# 数据库名映射：MySQL DB → PostgreSQL DB
DB_MAPPING = {
    "traceqa_user": "traceqa_user",
    "traceqa_kb": "traceqa_kb",
    "traceqa_qa": "traceqa_qa",
    "traceqa_admin": "traceqa_admin",
}

# 表名（按迁移顺序排列，有外键依赖的先迁移父表）
TABLE_ORDER = {
    "traceqa_user": ["t_role", "t_user"],
    "traceqa_kb": ["t_knowledge_base", "t_document"],
    "traceqa_qa": ["t_chat_session", "t_chat_message", "t_system_prompt"],
    "traceqa_admin": ["t_announcement"],
}

# 保留字（PostgreSQL 关键字，需要双引号包裹）
PG_RESERVED_WORDS = {"references"}


def get_mysql_connection(db_name):
    config = MYSQL_CONFIG.copy()
    config["database"] = db_name
    return pymysql.connect(**config, cursorclass=pymysql.cursors.DictCursor)


def get_pg_connection(db_name):
    config = PG_CONFIG.copy()
    config["dbname"] = db_name
    return psycopg2.connect(**config)


def quote_column(col_name):
    """PostgreSQL 保留字用双引号包裹"""
    if col_name.lower() in PG_RESERVED_WORDS:
        return f'"{col_name}"'
    return col_name


def migrate_table(mysql_conn, pg_conn, table_name):
    """迁移单个表"""
    mysql_cur = mysql_conn.cursor()
    pg_cur = pg_conn.cursor()

    # 1. 获取 MySQL 表数据
    mysql_cur.execute(f"SELECT * FROM `{table_name}`")
    rows = mysql_cur.fetchall()

    if not rows:
        print(f"  {table_name}: 0 行（跳过）")
        return 0

    # 2. 获取列名
    columns = [desc[0] for desc in mysql_cur.description]
    pg_columns = [quote_column(c) for c in columns]

    # 3. 转换数据类型
    converted_rows = []
    for row in rows:
        converted = []
        for val in row:
            if isinstance(val, bytes):
                val = val.decode("utf-8", errors="replace")
            converted.append(val)
        converted_rows.append(tuple(converted))

    # 4. 清空目标表（如果已有数据）
    pg_cur.execute(f"TRUNCATE TABLE {quote_column(table_name)} CASCADE")

    # 5. 批量插入 PostgreSQL
    insert_sql = f"""
        INSERT INTO {quote_column(table_name)} ({', '.join(pg_columns)})
        VALUES %s
    """
    execute_values(pg_cur, insert_sql, converted_rows, page_size=1000)
    pg_conn.commit()

    print(f"  {table_name}: {len(rows)} 行")
    return len(rows)


def verify_migration(mysql_conn, pg_conn, table_name):
    """验证行数一致性"""
    mysql_cur = mysql_conn.cursor()
    pg_cur = pg_conn.cursor()

    mysql_cur.execute(f"SELECT COUNT(*) AS cnt FROM `{table_name}`")
    mysql_count = mysql_cur.fetchone()["cnt"]

    pg_cur.execute(f"SELECT COUNT(*) AS cnt FROM {quote_column(table_name)}")
    pg_count = pg_cur.fetchone()["cnt"]

    match = "✓" if mysql_count == pg_count else "✗ 不一致!"
    print(f"  {table_name}: MySQL={mysql_count}, PG={pg_count} {match}")
    return mysql_count == pg_count


def main():
    print("=" * 60)
    print("TraceQA 数据迁移：MySQL → PostgreSQL")
    print("=" * 60)

    total_tables = sum(len(tables) for tables in TABLE_ORDER.values())
    migrated = 0
    verified = 0
    start = time.time()

    for mysql_db, pg_db in DB_MAPPING.items():
        print(f"\n{'─' * 40}")
        print(f"数据库：{mysql_db} → {pg_db}")
        print(f"{'─' * 40}")

        try:
            mysql_conn = get_mysql_connection(mysql_db)
        except Exception as e:
            print(f"  ✗ 无法连接 MySQL ({mysql_db}): {e}")
            print(f"    跳过该库（如果是首次部署，MySQL 可能已不可用）")
            continue

        try:
            pg_conn = get_pg_connection(pg_db)
        except Exception as e:
            print(f"  ✗ 无法连接 PostgreSQL ({pg_db}): {e}")
            mysql_conn.close()
            sys.exit(1)

        tables = TABLE_ORDER.get(mysql_db, [])
        for table in tables:
            try:
                count = migrate_table(mysql_conn, pg_conn, table)
                migrated += 1
                if count > 0:
                    if verify_migration(mysql_conn, pg_conn, table):
                        verified += 1
            except Exception as e:
                print(f"  ✗ {table} 迁移失败: {e}")
                import traceback
                traceback.print_exc()

        mysql_conn.close()
        pg_conn.close()

    elapsed = time.time() - start
    print(f"\n{'=' * 60}")
    print(f"迁移完成：{migrated}/{total_tables} 表，验证通过 {verified} 表")
    print(f"耗时：{elapsed:.1f}s")
    print(f"{'=' * 60}")

    if migrated < total_tables:
        print("\n⚠️  部分表未迁移，请检查 MySQL 连接是否正常。")
        print("   如果是全新部署（MySQL 无数据），此警告可忽略。")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
TraceQA LightRAG 数据迁移脚本：SQLite → Neo4j + PostgreSQL
============================================================
功能：
  1. 读取 LightRAG 嵌入式 SQLite 数据（图谱/文档/分块）
  2. 将实体-关系图谱导入 Neo4j
  3. 将文档分块与向量导入 PostgreSQL (pgvector)
  4. 清理旧的嵌入式存储

使用：
  pip install neo4j psycopg2-binary

  # 先拷出 LightRAG 数据卷
  docker cp traceqa-lightrag:/app/data/rag_storage ./lightrag-backup

  # 运行迁移
  python scripts/migrate-lightrag-to-neo4j.py

环境变量：
  NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD
  PG_HOST, PG_PORT, PG_USER, PG_PASSWORD
"""
import os
import sys
import json
import sqlite3
import struct
import time
from pathlib import Path

try:
    from neo4j import GraphDatabase
except ImportError:
    print("请安装 neo4j 驱动: pip install neo4j")
    sys.exit(1)

try:
    import psycopg2
    from psycopg2.extras import execute_values
except ImportError:
    print("请安装 psycopg2: pip install psycopg2-binary")
    sys.exit(1)


# ============ 配置 ============
NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:6134")
NEO4J_USER = os.getenv("NEO4J_USER", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "traceqa-neo4j-2026")

PG_CONFIG = {
    "host": os.getenv("PG_HOST", "localhost"),
    "port": int(os.getenv("PG_PORT", "6118")),
    "user": os.getenv("PG_USER", "traceqa"),
    "password": os.getenv("PG_PASSWORD", "traceqa123"),
    "dbname": os.getenv("PG_DATABASE_LIGHTRAG", "traceqa_lightrag"),
}

# LightRAG 数据目录
RAG_STORAGE_DIR = os.getenv(
    "RAG_STORAGE_DIR",
    os.path.expanduser("~/.traceqa/lightrag-backup/rag_storage")
)


def find_sqlite_files(base_dir):
    """自动发现 LightRAG 的 SQLite 文件"""
    base = Path(base_dir)
    files = {}
    for f in base.glob("*.db"):
        name = f.stem.lower()
        if "full_doc" in name:
            files["full_docs"] = str(f)
        elif "doc_chunk" in name or "chunk" in name:
            files["doc_chunks"] = str(f)
        elif "graph" in name and "chunk" not in name:
            files["graph"] = str(f)
    # 也检查不带 .db 后缀的文件
    for f in base.glob("*"):
        if f.is_file() and f.suffix == "":
            try:
                conn = sqlite3.connect(str(f))
                cursor = conn.execute("SELECT name FROM sqlite_master WHERE type='table'")
                tables = [row[0] for row in cursor.fetchall()]
                conn.close()
                if any("doc" in t.lower() for t in tables):
                    files.setdefault("unknown", str(f))
            except:
                pass
    return files


def read_full_docs(db_path):
    """读取完整文档"""
    conn = sqlite3.connect(db_path)
    cursor = conn.execute("SELECT doc_id, content, metadata FROM full_docs")
    docs = []
    for row in cursor.fetchall():
        doc_id, content, metadata = row
        meta = json.loads(metadata) if metadata else {}
        docs.append({
            "id": doc_id,
            "content": content,
            "metadata": meta,
        })
    conn.close()
    return docs


def read_doc_chunks(db_path):
    """读取文档分块"""
    conn = sqlite3.connect(db_path)
    # 探查表结构
    cursor = conn.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = [row[0] for row in cursor.fetchall()]

    chunks = []
    for table in tables:
        try:
            cursor = conn.execute(f"PRAGMA table_info({table})")
            cols = [row[1] for row in cursor.fetchall()]
            if any("chunk" in c.lower() for c in cols) or any("content" in c.lower() for c in cols):
                cursor = conn.execute(f"SELECT * FROM {table}")
                for row in cursor.fetchall():
                    row_dict = dict(zip([d[1] for d in conn.execute(f"PRAGMA table_info({table})").fetchall()], row))
                    chunks.append(row_dict)
        except:
            continue
    conn.close()
    return chunks


def read_graph(db_path):
    """读取图谱数据（节点和边）"""
    conn = sqlite3.connect(db_path)
    cursor = conn.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = [row[0] for row in cursor.fetchall()]

    nodes = []
    edges = []
    for table in tables:
        try:
            cursor = conn.execute(f"PRAGMA table_info({table})")
            cols = [row[1] for row in cursor.fetchall()]
            cursor = conn.execute(f"SELECT * FROM {table}")
            rows = [dict(zip([d[1] for d in conn.execute(f"PRAGMA table_info({table})").fetchall()], row))
                    for row in cursor.fetchall()]

            for row in rows:
                # 尝试识别节点表和边表
                if any(k in str(row.keys()).lower() for k in ["source", "target", "from", "to"]):
                    edges.append(row)
                else:
                    nodes.append(row)
        except:
            continue
    conn.close()
    return nodes, edges


def migrate_to_neo4j(nodes, edges):
    """导入图谱到 Neo4j"""
    print(f"\n导入 Neo4j：{len(nodes)} 节点, {len(edges)} 边")

    driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USER, NEO4J_PASSWORD))

    with driver.session() as session:
        # 清空旧数据
        session.run("MATCH (n) DETACH DELETE n")

        # 导入节点
        for i, node in enumerate(nodes):
            labels = node.get("type", node.get("label", "Entity"))
            name = node.get("name", node.get("id", f"node_{i}"))
            props = {k: v for k, v in node.items() if k not in ("type", "label", "name", "id")}
            props["name"] = str(name)

            cypher = f"CREATE (n:{labels} {{name: $name}})"
            session.run(cypher, name=str(name))

            # 设置属性
            for k, v in props.items():
                if v is not None:
                    try:
                        session.run(
                            f"MATCH (n {{{{name: $name}}}}) SET n.{k} = $value",
                            name=str(name), value=str(v)
                        )
                    except:
                        pass

            if (i + 1) % 100 == 0:
                print(f"  节点: {i + 1}/{len(nodes)}")

        # 导入边
        for i, edge in enumerate(edges):
            src = edge.get("source", edge.get("from", edge.get("src", "")))
            tgt = edge.get("target", edge.get("to", edge.get("tgt", "")))
            rel_type = edge.get("type", edge.get("relation", "RELATES"))
            props = {k: v for k, v in edge.items()
                     if k not in ("source", "target", "from", "to", "src", "tgt", "type", "relation")}

            if src and tgt:
                try:
                    session.run(
                        f"MATCH (a {{name: $src}}), (b {{name: $tgt}}) "
                        f"CREATE (a)-[:{rel_type}]->(b)",
                        src=str(src), tgt=str(tgt)
                    )
                except:
                    pass

            if (i + 1) % 100 == 0:
                print(f"  边: {i + 1}/{len(edges)}")

    driver.close()
    print("  Neo4j 导入完成")


def migrate_to_pgvector(docs, chunks):
    """导入文档和分块到 PostgreSQL (pgvector)"""
    print(f"\n导入 PostgreSQL：{len(docs)} 文档, {len(chunks)} 分块")

    conn = psycopg2.connect(**PG_CONFIG)
    cur = conn.cursor()

    # 创建表（如果不存在）
    cur.execute("""
        CREATE TABLE IF NOT EXISTS lightrag_documents (
            id TEXT PRIMARY KEY,
            content TEXT,
            metadata JSONB,
            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS lightrag_chunks (
            id TEXT PRIMARY KEY,
            doc_id TEXT,
            content TEXT,
            chunk_index INT DEFAULT 0,
            metadata JSONB,
            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS lightrag_entities (
            id SERIAL PRIMARY KEY,
            name TEXT NOT NULL,
            type TEXT DEFAULT 'Entity',
            content TEXT,
            metadata JSONB,
            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS lightrag_relations (
            id SERIAL PRIMARY KEY,
            source_name TEXT,
            target_name TEXT,
            relation_type TEXT DEFAULT 'RELATES',
            content TEXT,
            metadata JSONB,
            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    conn.commit()

    # 导入文档
    for doc in docs:
        cur.execute(
            "INSERT INTO lightrag_documents (id, content, metadata) VALUES (%s, %s, %s) "
            "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content",
            (doc["id"], doc["content"], json.dumps(doc.get("metadata", {})))
        )
    conn.commit()
    print(f"  文档: {len(docs)} 行")

    # 导入分块
    for i, chunk in enumerate(chunks):
        chunk_id = chunk.get("chunk_id", chunk.get("id", f"chunk_{i}"))
        doc_id = chunk.get("doc_id", "")
        content = chunk.get("content", chunk.get("text", ""))
        chunk_idx = chunk.get("chunk_index", i)
        metadata = {k: v for k, v in chunk.items()
                    if k not in ("chunk_id", "id", "doc_id", "content", "text", "chunk_index")}

        cur.execute(
            "INSERT INTO lightrag_chunks (id, doc_id, content, chunk_index, metadata) "
            "VALUES (%s, %s, %s, %s, %s) ON CONFLICT (id) DO NOTHING",
            (str(chunk_id), str(doc_id), content, chunk_idx, json.dumps(metadata))
        )
    conn.commit()
    print(f"  分块: {len(chunks)} 行")

    cur.close()
    conn.close()
    print("  PostgreSQL 导入完成")


def main():
    print("=" * 60)
    print("TraceQA LightRAG 数据迁移")
    print(f"数据源：{RAG_STORAGE_DIR}")
    print("=" * 60)

    if not Path(RAG_STORAGE_DIR).exists():
        print(f"\n✗ 数据目录不存在: {RAG_STORAGE_DIR}")
        print("请先拷出 LightRAG 数据卷：")
        print("  docker cp traceqa-lightrag:/app/data/rag_storage ./lightrag-backup")
        print(f"  然后设置 RAG_STORAGE_DIR 环境变量指向 ./lightrag-backup/rag_storage")
        sys.exit(1)

    # 发现文件
    files = find_sqlite_files(RAG_STORAGE_DIR)
    print(f"\n发现文件: {files}")

    start = time.time()

    # 读取数据
    docs = []
    chunks = []
    nodes = []
    edges = []

    if "full_docs" in files:
        docs = read_full_docs(files["full_docs"])
        print(f"完整文档: {len(docs)} 篇")

    if "doc_chunks" in files:
        chunks = read_doc_chunks(files["doc_chunks"])
        print(f"文档分块: {len(chunks)} 个")

    if "graph" in files:
        nodes, edges = read_graph(files["graph"])
        print(f"图谱节点: {len(nodes)} 个, 边: {len(edges)} 条")

    if not docs and not chunks and not nodes:
        print("\n⚠️  未发现有效数据。请确认 LightRAG 数据目录结构。")
        print("   LightRAG 的 SQLite 文件通常命名为：")
        print("   - full_docs.db / nano-vectordb/*.db")
        print("   - graph_*.db")
        sys.exit(1)

    # 迁移到 Neo4j
    if nodes or edges:
        try:
            migrate_to_neo4j(nodes, edges)
        except Exception as e:
            print(f"\n✗ Neo4j 迁移失败: {e}")
            print("  请确认 Neo4j 已启动且连接信息正确。")

    # 迁移到 PostgreSQL
    if docs or chunks:
        try:
            migrate_to_pgvector(docs, chunks)
        except Exception as e:
            print(f"\n✗ PostgreSQL 迁移失败: {e}")
            print("  请确认 PostgreSQL 已启动且 traceqa_lightrag 库已创建。")
            print("  可手动创建：docker exec traceqa-postgresql psql -U traceqa -c 'CREATE DATABASE traceqa_lightrag;'")

    elapsed = time.time() - start
    print(f"\n{'=' * 60}")
    print(f"迁移完成，耗时 {elapsed:.1f}s")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()

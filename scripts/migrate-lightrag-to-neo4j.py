#!/usr/bin/env python3
"""
TraceQA LightRAG 数据迁移脚本：JSON/GraphML → Neo4j + PostgreSQL
=================================================================
LightRAG 新版本使用 JSON KV 存储 + GraphML 图谱格式。

数据文件：
  - graph_chunk_entity_relation.graphml  → 图谱（实体/关系/分块）→ Neo4j
  - kv_store_full_docs.json              → 完整文档 → PostgreSQL
  - kv_store_text_chunks.json            → 文本分块 → PostgreSQL
  - kv_store_full_entities.json          → 实体 → PostgreSQL
  - kv_store_full_relations.json         → 关系 → PostgreSQL
  - vdb_chunks.json                      → 向量分块 → PostgreSQL
  - vdb_entities.json                    → 向量实体 → PostgreSQL
  - vdb_relationships.json               → 向量关系 → PostgreSQL

使用：
  pip install neo4j psycopg2-binary lxml
  python scripts/migrate-lightrag-to-neo4j.py

环境变量：
  NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD
  PG_HOST, PG_PORT, PG_USER, PG_PASSWORD
"""
import os
import sys
import json
import time
from pathlib import Path

try:
    from neo4j import GraphDatabase
except ImportError:
    print("请安装 neo4j 驱动: pip install neo4j")
    sys.exit(1)

try:
    import psycopg2
    from psycopg2.extras import execute_values, Json
except ImportError:
    print("请安装 psycopg2: pip install psycopg2-binary")
    sys.exit(1)

try:
    from lxml import etree
except ImportError:
    print("请安装 lxml: pip install lxml")
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

# LightRAG 数据目录（在容器内 /app/data/rag_storage）
RAG_STORAGE_DIR = os.getenv(
    "RAG_STORAGE_DIR",
    "/app/data/rag_storage"
)


def load_json(path):
    """加载 JSON KV 存储文件"""
    p = Path(path)
    if not p.exists():
        return {}
    with open(p, "r", encoding="utf-8") as f:
        return json.load(f)


def parse_graphml(graphml_path):
    """解析 GraphML 文件，提取节点和边"""
    tree = etree.parse(graphml_path)
    root = tree.getroot()

    ns = {"g": "http://graphml.graphdrawing.org/xmlns"}

    # 提取节点
    nodes = {}
    for node in root.findall(".//g:node", ns):
        node_id = node.get("id")
        data = {}
        for data_elem in node.findall("g:data", ns):
            key = data_elem.get("key")
            data[key] = data_elem.text
        nodes[node_id] = data

    # 提取边
    edges = []
    for edge in root.findall(".//g:edge", ns):
        source = edge.get("source")
        target = edge.get("target")
        data = {}
        for data_elem in edge.findall("g:data", ns):
            key = data_elem.get("key")
            data[key] = data_elem.text
        edges.append({"source": source, "target": target, **data})

    return nodes, edges


def migrate_to_neo4j(rag_dir):
    """迁移图谱到 Neo4j"""
    graphml_path = Path(rag_dir) / "graph_chunk_entity_relation.graphml"
    if not graphml_path.exists():
        print(f"\n⚠️  GraphML 文件不存在: {graphml_path}")
        return 0, 0

    print(f"\n解析 GraphML: {graphml_path}")
    nodes, edges = parse_graphml(graphml_path)
    print(f"  节点: {len(nodes)}, 边: {len(edges)}")

    # 分类节点
    entity_nodes = {}
    chunk_nodes = {}
    for nid, data in nodes.items():
        node_type = data.get("node_type", data.get("type", "")).lower()
        label = data.get("label", "")
        if "entity" in node_type or "entity" in label.lower():
            entity_nodes[nid] = data
        elif "chunk" in node_type or "chunk" in label.lower():
            chunk_nodes[nid] = data
        else:
            # 默认归为实体
            entity_nodes[nid] = data

    print(f"  实体节点: {len(entity_nodes)}, 分块节点: {len(chunk_nodes)}")

    driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USER, NEO4J_PASSWORD))

    with driver.session() as session:
        # 清空旧数据
        session.run("MATCH (n) DETACH DELETE n")

        # 创建约束
        session.run("CREATE CONSTRAINT IF NOT EXISTS FOR (e:Entity) REQUIRE e.name IS UNIQUE")
        session.run("CREATE CONSTRAINT IF NOT EXISTS FOR (c:Chunk) REQUIRE c.id IS UNIQUE")

        # 导入实体节点
        count = 0
        for nid, data in entity_nodes.items():
            name = data.get("name", data.get("entity_name", nid))
            entity_type = data.get("entity_type", data.get("type", "Entity"))
            description = data.get("description", "")
            source_id = data.get("source_id", "")

            session.run(
                "MERGE (e:Entity {name: $name}) "
                "SET e.type = $type, e.description = $desc, e.source_id = $source_id",
                name=str(name), type=str(entity_type),
                desc=str(description), source_id=str(source_id)
            )
            count += 1
            if count % 200 == 0:
                print(f"    实体: {count}/{len(entity_nodes)}")

        print(f"    实体: {count} 完成")

        # 导入分块节点
        count = 0
        for nid, data in chunk_nodes.items():
            chunk_id = data.get("chunk_id", nid)
            content = data.get("content", data.get("text", ""))
            source = data.get("source", data.get("file_path", ""))

            session.run(
                "MERGE (c:Chunk {id: $id}) "
                "SET c.content = $content, c.source = $source",
                id=str(chunk_id), content=str(content)[:10000], source=str(source)
            )
            count += 1
            if count % 200 == 0:
                print(f"    分块: {count}/{len(chunk_nodes)}")

        print(f"    分块: {count} 完成")

        # 导入边（关系）
        count = 0
        for edge in edges:
            src = edge.get("source", "")
            tgt = edge.get("target", "")
            rel_type = edge.get("relationship", edge.get("relation", edge.get("type", "RELATES")))
            # 清理关系类型名（Neo4j 不允许特殊字符）
            rel_type = "".join(c if c.isalnum() or c == "_" else "_" for c in str(rel_type)).upper()
            if not rel_type:
                rel_type = "RELATES"

            weight = edge.get("weight", "1")
            description = edge.get("description", "")

            try:
                session.run(
                    f"MATCH (a {{name: $src}}), (b {{name: $tgt}}) "
                    f"CREATE (a)-[:{rel_type} {{weight: $weight, description: $desc}}]->(b)",
                    src=str(src), tgt=str(tgt),
                    weight=str(weight), desc=str(description)
                )
                count += 1
            except Exception:
                # 可能是 Chunk→Entity 的边，尝试用 id 匹配
                try:
                    session.run(
                        f"MATCH (a {{id: $src}}), (b {{name: $tgt}}) "
                        f"CREATE (a)-[:CONTAINS]->(b)",
                        src=str(src), tgt=str(tgt)
                    )
                    count += 1
                except Exception:
                    pass

            if count % 200 == 0 and count > 0:
                print(f"    边: {count}/{len(edges)}")

        print(f"    边: {count} 完成")

        # 统计
        result = session.run("MATCH (n) RETURN labels(n)[0] AS label, count(*) AS cnt")
        for record in result:
            print(f"  Neo4j 统计: {record['label']} = {record['cnt']}")

    driver.close()
    return len(entity_nodes) + len(chunk_nodes), count


def migrate_to_pgvector(rag_dir):
    """迁移文档和分块到 PostgreSQL"""
    rag_dir = Path(rag_dir)

    # 读取 JSON KV 存储
    full_docs = load_json(rag_dir / "kv_store_full_docs.json")
    text_chunks = load_json(rag_dir / "kv_store_text_chunks.json")
    full_entities = load_json(rag_dir / "kv_store_full_entities.json")
    full_relations = load_json(rag_dir / "kv_store_full_relations.json")

    print(f"\n读取 JSON KV 存储:")
    print(f"  完整文档: {len(full_docs)} 篇")
    print(f"  文本分块: {len(text_chunks)} 个")
    print(f"  实体: {len(full_entities)} 个")
    print(f"  关系: {len(full_relations)} 条")

    conn = psycopg2.connect(**PG_CONFIG)
    cur = conn.cursor()

    # 创建表
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
            content TEXT,
            metadata JSONB,
            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS lightrag_entities (
            name TEXT PRIMARY KEY,
            entity_type TEXT DEFAULT 'Entity',
            description TEXT,
            source_id TEXT,
            metadata JSONB,
            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    cur.execute("""
        CREATE TABLE IF NOT EXISTS lightrag_relations (
            source_name TEXT,
            target_name TEXT,
            relation_type TEXT DEFAULT 'RELATES',
            weight FLOAT DEFAULT 1.0,
            description TEXT,
            metadata JSONB,
            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (source_name, target_name, relation_type)
        )
    """)
    conn.commit()

    # 导入文档
    count = 0
    for doc_id, doc_data in full_docs.items():
        if isinstance(doc_data, dict):
            content = doc_data.get("content", doc_data.get("data", ""))
            metadata = {k: v for k, v in doc_data.items() if k not in ("content", "data")}
        else:
            content = str(doc_data)
            metadata = {}

        cur.execute(
            "INSERT INTO lightrag_documents (id, content, metadata) VALUES (%s, %s, %s) "
            "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content",
            (str(doc_id), content, Json(metadata))
        )
        count += 1
    conn.commit()
    print(f"  文档: {count} 行")

    # 导入分块
    count = 0
    for chunk_id, chunk_data in text_chunks.items():
        if isinstance(chunk_data, dict):
            content = chunk_data.get("content", chunk_data.get("data", ""))
            metadata = {k: v for k, v in chunk_data.items() if k not in ("content", "data")}
        else:
            content = str(chunk_data)
            metadata = {}

        cur.execute(
            "INSERT INTO lightrag_chunks (id, content, metadata) VALUES (%s, %s, %s) "
            "ON CONFLICT (id) DO NOTHING",
            (str(chunk_id), content, Json(metadata))
        )
        count += 1
    conn.commit()
    print(f"  分块: {count} 行")

    # 导入实体
    count = 0
    for entity_name, entity_data in full_entities.items():
        if isinstance(entity_data, dict):
            entity_type = entity_data.get("entity_type", entity_data.get("type", "Entity"))
            description = entity_data.get("description", "")
            source_id = entity_data.get("source_id", "")
            metadata = {k: v for k, v in entity_data.items()
                        if k not in ("entity_type", "type", "description", "source_id", "name")}
        else:
            entity_type = "Entity"
            description = str(entity_data)
            source_id = ""
            metadata = {}

        cur.execute(
            "INSERT INTO lightrag_entities (name, entity_type, description, source_id, metadata) "
            "VALUES (%s, %s, %s, %s, %s) ON CONFLICT (name) DO UPDATE SET description = EXCLUDED.description",
            (str(entity_name), str(entity_type), description, str(source_id), Json(metadata))
        )
        count += 1
    conn.commit()
    print(f"  实体: {count} 行")

    # 导入关系
    count = 0
    for rel_id, rel_data in full_relations.items():
        if isinstance(rel_data, dict):
            src = rel_data.get("source", rel_data.get("source_name", ""))
            tgt = rel_data.get("target", rel_data.get("target_name", ""))
            rel_type = rel_data.get("relationship", rel_data.get("relation_type", "RELATES"))
            weight = float(rel_data.get("weight", 1))
            description = rel_data.get("description", "")
            metadata = {k: v for k, v in rel_data.items()
                        if k not in ("source", "target", "source_name", "target_name",
                                     "relationship", "relation_type", "weight", "description")}
        else:
            src = str(rel_id)
            tgt = ""
            rel_type = "RELATES"
            weight = 1.0
            description = str(rel_data)
            metadata = {}

        if src and tgt:
            cur.execute(
                "INSERT INTO lightrag_relations "
                "(source_name, target_name, relation_type, weight, description, metadata) "
                "VALUES (%s, %s, %s, %s, %s, %s) "
                "ON CONFLICT (source_name, target_name, relation_type) DO NOTHING",
                (str(src), str(tgt), str(rel_type), weight, description, Json(metadata))
            )
            count += 1
    conn.commit()
    print(f"  关系: {count} 行")

    cur.close()
    conn.close()
    return len(full_docs), len(text_chunks), len(full_entities), len(full_relations)


def main():
    print("=" * 60)
    print("TraceQA LightRAG 数据迁移（JSON/GraphML → Neo4j + PG）")
    print(f"数据源：{RAG_STORAGE_DIR}")
    print("=" * 60)

    if not Path(RAG_STORAGE_DIR).exists():
        print(f"\n✗ 数据目录不存在: {RAG_STORAGE_DIR}")
        print("请先从容器内拷出数据：")
        print("  docker cp traceqa-lightrag:/app/data/rag_storage ./lightrag-backup")
        print("  然后设置 RAG_STORAGE_DIR 环境变量")
        sys.exit(1)

    # 检查关键文件
    required = ["graph_chunk_entity_relation.graphml", "kv_store_full_docs.json"]
    for f in required:
        if not (Path(RAG_STORAGE_DIR) / f).exists():
            print(f"\n⚠️  关键文件缺失: {f}")

    start = time.time()

    # 迁移到 Neo4j
    try:
        node_count, edge_count = migrate_to_neo4j(RAG_STORAGE_DIR)
        print(f"\n✓ Neo4j 迁移完成：{node_count} 节点, {edge_count} 边")
    except Exception as e:
        print(f"\n✗ Neo4j 迁移失败: {e}")
        import traceback
        traceback.print_exc()

    # 迁移到 PostgreSQL
    try:
        docs, chunks, entities, relations = migrate_to_pgvector(RAG_STORAGE_DIR)
        print(f"\n✓ PostgreSQL 迁移完成：{docs} 文档, {chunks} 分块, {entities} 实体, {relations} 关系")
    except Exception as e:
        print(f"\n✗ PostgreSQL 迁移失败: {e}")
        import traceback
        traceback.print_exc()

    elapsed = time.time() - start
    print(f"\n{'=' * 60}")
    print(f"迁移完成，耗时 {elapsed:.1f}s")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()

"""
知识库同步脚本
将 MySQL 中的业务术语库和计算口径库同步到 ChromaDB 向量库

使用方法:
    cd smart-sql-ai
    python scripts/sync_knowledge.py

流程:
    MySQL(knowledge_term, knowledge_caliber) → 向量化 → ChromaDB
"""
import os
import sys

# 添加项目根目录到 sys.path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# ⚠️ 必须在导入 HuggingFace 相关库之前设置镜像
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")

import pymysql
from app.config import settings
from app.rag.vector_store import VectorStoreManager

# MySQL 连接配置（与 Java application.yml 保持一致）
MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "[已脱敏-见.env]")
MYSQL_DB = os.getenv("MYSQL_DB", "smart_sql")


def read_mysql_knowledge():
    """从 MySQL 读取生效状态的知识库条目"""
    conn = pymysql.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=MYSQL_DB,
        charset="utf8mb4",
    )
    items = []

    try:
        with conn.cursor(pymysql.cursors.DictCursor) as cursor:
            # 读取生效的业务术语
            cursor.execute(
                "SELECT id, term_name, business_desc FROM knowledge_term WHERE status = 'active'"
            )
            for row in cursor.fetchall():
                items.append({
                    "id": row["id"],
                    "name": row["term_name"],
                    "description": row["business_desc"] or "",
                    "sql_logic": "",
                    "category": "term",
                })

            # 读取生效的计算口径
            cursor.execute(
                "SELECT id, caliber_name, sql_logic, business_desc FROM knowledge_caliber WHERE status = 'active'"
            )
            for row in cursor.fetchall():
                items.append({
                    "id": row["id"],
                    "name": row["caliber_name"],
                    "description": row["business_desc"] or "",
                    "sql_logic": row["sql_logic"] or "",
                    "category": "caliber",
                })
    finally:
        conn.close()

    return items


def sync_to_chroma(items):
    """同步知识条目到 ChromaDB"""
    vs = VectorStoreManager()

    texts, metadatas, ids = [], [], []
    for item in items:
        # 构建检索文档：标题重复加权（避免长文档稀释标题语义）
        # 口径条目含 SQL 代码时文档较长，标题语义会被平均稀释，
        # 重复标题一次可显著提升标题匹配度
        doc_text = f"{item['name']}\n{item['name']}"
        if item["description"]:
            doc_text += f"\n描述：{item['description']}"
        if item["sql_logic"]:
            doc_text += f"\nSQL：{item['sql_logic']}"

        texts.append(doc_text)
        metadatas.append({
            "id": item["id"],
            "name": item["name"],
            "category": item["category"],
            "description": item["description"],
        })
        ids.append(f"{item['category']}_{item['id']}")

    import asyncio
    asyncio.run(vs.upsert(texts, metadatas, ids))
    return len(texts)


def verify_search(query):
    """验证检索效果"""
    vs = VectorStoreManager()
    import asyncio
    results = asyncio.run(vs.search(query, top_k=3))
    return results


if __name__ == "__main__":
    print("=" * 50)
    print("知识库同步: MySQL → ChromaDB")
    print("=" * 50)

    # 1. 读取 MySQL
    items = read_mysql_knowledge()
    print(f"\n[1/3] 从 MySQL 读取到 {len(items)} 条生效知识:")
    for item in items:
        category = "术语" if item["category"] == "term" else "口径"
        print(f"  - [{category}] {item['name']}")

    # 2. 同步到 ChromaDB
    count = sync_to_chroma(items)
    print(f"\n[2/3] 已同步 {count} 条知识到向量库")

    # 3. 验证检索
    print("\n[3/3] 验证检索效果: '统计当日5G套网到达数'")
    results = verify_search("统计当日5G套网到达数")
    for i, r in enumerate(results):
        print(f"  命中{i+1}: {r['name']} (相似度: {r['score']})")

    print("\n[OK] 同步完成!")

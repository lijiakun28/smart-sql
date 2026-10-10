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
from dotenv import load_dotenv
from app.config import settings
from app.rag.vector_store import VectorStoreManager
from app.rag.doc_builder import build_doc_text, build_knowledge_id

# 加载 .env（含 MYSQL_PASSWORD，该文件已被 .gitignore 排除，不会进版本库）
load_dotenv()

# MySQL 连接配置（与 Java application.yml 保持一致）
MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
# 安全要求：密码必须通过环境变量（.env）注入，禁止硬编码默认值
MYSQL_PASSWORD = os.environ.get("MYSQL_PASSWORD")
MYSQL_DB = os.getenv("MYSQL_DB", "smart_sql")


def read_mysql_knowledge():
    """从 MySQL 读取生效状态的知识库条目

    扩展性预留：当知识库达数万条时，应改为 keyset 分页批量读取
    （按 id 游标，每批 500 条，边读边同步），避免全量加载导致 OOM。
    当前 MVP 数据量小，全量读取可接受（YAGNI）。
    """
    if not MYSQL_PASSWORD:
        raise RuntimeError(
            "缺少 MYSQL_PASSWORD 环境变量。请在 smart-sql-ai/.env 中配置: "
            "MYSQL_PASSWORD=你的密码"
        )

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
        # ⚠️ 与 app/main.py 的实时同步路径共用同一份构造实现（app/rag/doc_builder.py）：
        # 两条路径写出不同结构的文档会导致向量漂移，检索得分随机变化且极难排查
        texts.append(build_doc_text(item["name"], item["description"], item["sql_logic"]))
        metadatas.append({
            "id": item["id"],
            "name": item["name"],
            "category": item["category"],
            "description": item["description"],
        })
        ids.append(build_knowledge_id(item["category"], item["id"]))

    import asyncio
    asyncio.run(vs.upsert(texts, metadatas, ids))
    return len(texts)


def verify_search(query):
    """验证检索效果"""
    vs = VectorStoreManager()
    import asyncio
    results = asyncio.run(vs.search(query, top_k=3))
    return results


def main(full_sync=False):
    print("=" * 50)
    print("知识库同步: MySQL → ChromaDB" + (" (全量对账模式)" if full_sync else ""))
    print("=" * 50)

    # 全量对账：清空向量库后从 MySQL 重建（清理历史垃圾数据）
    if full_sync:
        vs = VectorStoreManager()
        vs.wipe()   # wipe 是同步方法，直接调用
        print(f"\n[全量] 向量库已清空")

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


if __name__ == "__main__":
    # --full: 全量对账模式（清空向量库后从 MySQL 重建，清理历史垃圾数据）
    full_sync = "--full" in sys.argv

    # 异常处理 + 退出码：脚本将来会被 Java/CI 调用，退出码是调用方判断成败的依据
    try:
        main(full_sync=full_sync)
    except pymysql.MySQLError as e:
        print(f"\n[FAIL] MySQL 连接/查询失败: {e}")
        print("提示: 检查 MySQL 服务是否运行、MYSQL_PASSWORD 是否正确")
        sys.exit(1)
    except KeyboardInterrupt:
        print("\n[FAIL] 用户中断")
        sys.exit(130)
    except Exception as e:
        print(f"\n[FAIL] 同步失败: {e}")
        sys.exit(2)

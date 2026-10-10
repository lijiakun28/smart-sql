"""
ChromaDB 向量存储管理
负责知识库条目的向量化存储和语义检索
"""
import os
import asyncio
from typing import List, Dict, Optional

import chromadb
from chromadb.config import Settings as ChromaSettings
from app.config import settings
from app.rag.embedding import get_embedding_model


def distance_to_score(distance: Optional[float]) -> float:
    """cosine 距离 → 相似度分数（0~1）

    ⚠️ 必须用 `is not None` 判断，不能用真值判断。
    距离恰为 0.0 表示两向量完全相同（完全匹配），是最高分；
    而 `if distance` 会把 0.0 当成"缺列"，让最佳命中显示 0 分。
    只有 None（Chroma 未返回 distances）才代表"未知"。
    """
    if distance is None:
        return 0.0
    return round(1 - distance, 4)


class LangChainEmbeddingAdapter:
    """将 LangChain HuggingFaceEmbeddings 适配为 ChromaDB EmbeddingFunction 接口"""

    def __init__(self):
        self.model = get_embedding_model()

    def __call__(self, input):
        """旧版 ChromaDB 调用入口"""
        return self.embed_documents(input)

    def embed_query(self, input):
        """新版 ChromaDB (1.5+) 查询入口：List[str] -> List[List[float]]"""
        if isinstance(input, str):
            input = [input]
        return self.model.embed_documents(input)

    def embed_documents(self, input):
        """新版 ChromaDB (1.5+) 文档入口：List[str] -> List[List[float]]"""
        if isinstance(input, str):
            input = [input]
        return self.model.embed_documents(input)

    def name(self):
        """ChromaDB 要求 EmbeddingFunction 提供 name() 方法"""
        return settings.EMBEDDING_MODEL


class VectorStoreManager:
    """ChromaDB 向量存储管理器"""

    COLLECTION_NAME = "smart_sql_knowledge"

    # 混合检索：查询中明确点名的表 → 强制召回（解决"关联表语义占比小，被挤出 Top-K"问题）
    # 每个别名命中查询时，其对应知识条目插入结果最前
    TABLE_KEYWORDS = {
        "broadband_usage": ["broadband_usage", "宽带安装", "宽带开通"],
        # user_info 是唯一维度表（地市/性别/年龄），按这些维度统计的查询必须强制召回
        "user_info": ["user_info", "用户基本信息", "用户信息表", "性别", "年龄", "地市"],
        "data_usage": ["data_usage", "流量使用", "数据流量"],
        "revenue_statistics": ["revenue_statistics", "收入统计", "收入表"],
        "5g_package_order": ["5g_package_order", "套餐订购"],
        "async_def": ["async_def", "异步任务"],
        # 概念口径点名：查询提到"高价值用户"时强制召回 caliber_14（月流量≥50GB 换算）
        "caliber_14": ["高价值用户", "50gb", "月流量≥50gb"],
        # 查询提到"年龄段/年龄分段"时强制召回 caliber_15（青年<45/中年45-60/老年>60）
        "caliber_15": ["年龄段", "年龄分段", "年龄分组"],
    }

    def __init__(self):
        # 确保持久化目录存在
        os.makedirs(settings.CHROMA_PERSIST_DIR, exist_ok=True)

        # 初始化 ChromaDB 客户端（持久化模式）
        self.client = chromadb.PersistentClient(
            path=settings.CHROMA_PERSIST_DIR,
            settings=ChromaSettings(anonymized_telemetry=False)
        )

        # 获取或创建 collection
        # ⚠️ 必须传入 embedding_function，否则 ChromaDB 会尝试下载
        # 它自带的 ONNX 模型（国内网络无法访问）
        self.collection = self.client.get_or_create_collection(
            name=self.COLLECTION_NAME,
            embedding_function=LangChainEmbeddingAdapter(),
            metadata={"hnsw:space": "cosine"}  # 余弦相似度
        )

    def _extract_named_tables(self, query: str) -> List[str]:
        """从查询中提取明确点名的表名（混合检索第一路：精确点名召回）"""
        q = query.lower()
        mentioned = []
        for canonical, aliases in self.TABLE_KEYWORDS.items():
            if any(alias.lower() in q for alias in aliases):
                mentioned.append(canonical)
        return mentioned

    async def search(
        self,
        query: str,
        top_k: int = 5
    ) -> List[dict]:
        """
        混合检索知识库：向量语义检索 + 表名点名召回

        Args:
            query: 搜索关键词/问题
            top_k: 返回最相关的前 K 条结果（点名表额外加入，总量 = top_k + 点名表数）

        Returns:
            [{"name": ..., "description": ..., "content": ..., "distance": ...}, ...]
        """
        # ── 第一路：向量语义检索 ──
        results = self.collection.query(
            query_texts=[query],
            n_results=top_k,
            include=["documents", "metadatas", "distances"]
        )

        knowledge_items = []
        if results["ids"] and results["ids"][0]:
            for i, doc_id in enumerate(results["ids"][0]):
                metadata = results["metadatas"][0][i] if results["metadatas"] else {}
                document = results["documents"][0][i] if results["documents"] else ""
                distance = results["distances"][0][i] if results["distances"] else 999

                knowledge_items.append({
                    # ⚠️ 必须用 ChromaDB 唯一 doc_id 做标识：metadata.id 是录入时的数值编号，
                    # term_x 与 caliber_x 编号不唯一（如 term_11 和 caliber_11 的 id 都是 11）
                    "id": doc_id,
                    "name": metadata.get("name", "未知"),
                    "category": metadata.get("category", ""),
                    "description": metadata.get("description", ""),
                    "content": document,
                    "score": distance_to_score(distance),  # cosine -> similarity
                })

        # ── 第二路：表名点名召回（查询中明确提到的表强制进入上下文）──
        # ⚠️ ChromaDB 1.5.x 的 where $contains 对 metadata 不生效，故拉全量在内存子串匹配
        #（知识库条目量级很小，全量拉取开销可忽略；若条目量级大应改为扫描 id 列表）
        named_tables = self._extract_named_tables(query)
        if named_tables:
            all_got = self.collection.get(include=["documents", "metadatas"])
            for table_name in named_tables:
                for i, doc_id in enumerate(all_got["ids"]):
                    metadata = all_got["metadatas"][i] if all_got["metadatas"] else {}
                    name = metadata.get("name", "") or ""
                    # 命中条件：canonical id 精确匹配（如 caliber_14），或 name 含表名（如 'broadband_usage'）
                    if doc_id != table_name and table_name.lower() not in name.lower():
                        continue
                    document = all_got["documents"][i] if all_got["documents"] else ""
                    item = {
                        "id": doc_id,
                        "name": name,
                        "category": metadata.get("category", ""),
                        "description": metadata.get("description", ""),
                        "content": document,
                        "score": 1.0,  # 点名召回视为满分相关
                    }
                    # 点名表优先级最高：无论是否已在向量结果中，强制置顶
                    #（若在向量结果中则先移除原位置，避免重复）
                    for j, k in enumerate(knowledge_items):
                        if k["id"] == item["id"]:
                            knowledge_items.pop(j)
                            break
                    knowledge_items.insert(0, item)

        return knowledge_items

    async def upsert(
        self,
        texts: List[str],
        metadatas: List[dict],
        ids: List[str]
    ):
        """
        插入或更新知识条目

        Args:
            texts: 文档文本列表
            metadatas: 元数据列表（id, name, category 等）
            ids: 唯一ID列表
        """
        self.collection.upsert(
            documents=texts,
            metadatas=metadatas,
            ids=ids
        )

    async def delete(self, ids: List[str]):
        """删除知识条目"""
        existing = self.collection.get(ids=ids)
        if existing["ids"]:
            self.collection.delete(ids=ids)

    def count(self) -> int:
        """获取向量库中条目数"""
        return self.collection.count()

    def wipe(self):
        """清空整个向量库（全量对账模式使用）"""
        all_ids = self.collection.get()["ids"]
        if all_ids:
            self.collection.delete(ids=all_ids)

    def close(self):
        """关闭连接（ChromaDB PersistentClient 无需特殊关闭）"""
        pass

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


class VectorStoreManager:
    """ChromaDB 向量存储管理器"""

    COLLECTION_NAME = "smart_sql_knowledge"

    def __init__(self):
        # 确保持久化目录存在
        os.makedirs(settings.CHROMA_PERSIST_DIR, exist_ok=True)

        # 初始化 ChromaDB 客户端（持久化模式）
        self.client = chromadb.PersistentClient(
            path=settings.CHROMA_PERSIST_DIR,
            settings=ChromaSettings(anonymized_telemetry=False)
        )

        # Embedding 函数
        self.embedding_fn = get_embedding_model()

        # 获取或创建 collection
        self.collection = self.client.get_or_create_collection(
            name=self.COLLECTION_NAME,
            metadata={"hnsw:space": "cosine"}  # 余弦相似度
        )

    async def search(
        self,
        query: str,
        top_k: int = 5
    ) -> List[dict]:
        """
        语义搜索知识库

        Args:
            query: 搜索关键词/问题
            top_k: 返回最相关的前 K 条结果

        Returns:
            [{"name": ..., "description": ..., "content": ..., "distance": ...}, ...]
        """
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
                    "id": metadata.get("id", doc_id),
                    "name": metadata.get("name", "未知"),
                    "category": metadata.get("category", ""),
                    "description": metadata.get("description", ""),
                    "content": document,
                    "score": round(1 - distance, 4) if distance else 0,  # cosine -> similarity
                })

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

    def close(self):
        """关闭连接（ChromaDB PersistentClient 无需特殊关闭）"""
        pass

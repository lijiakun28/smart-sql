"""
RAG 检索增强生成链
组合向量检索 + LLM 生成
"""
from langchain_core.output_parsers import StrOutputParser
from langchain_core.runnables import RunnablePassthrough

from app.chains.nl_to_sql import build_sql_generation_chain
from app.rag.vector_store import VectorStoreManager


async def build_rag_chain():
    """
    构建 RAG 检索链（预留扩展接口）

    流程：用户问题 → 向量检索 → 注入 Prompt → LLM 生成 → 输出
    """
    vs = VectorStoreManager()

    async def retrieve_and_generate(question: str, context: dict = None):
        """检索 + 生成"""
        # 检索
        knowledge_texts = await vs.search(question, top_k=5)

        # 生成
        chain = build_sql_generation_chain(knowledge_texts)

        result = await chain.ainvoke({
            "question": question,
            "context": str(context) if context else "无"
        })

        return result

    return retrieve_and_generate

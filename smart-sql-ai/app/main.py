"""
智能SQL - Python AI 引擎入口
FastAPI 服务，负责 NL→SQL、RAG 检索、流式输出
"""
import os
import time
import json
import asyncio

# ⚠️ 必须在导入任何 HuggingFace 相关库之前设置镜像地址
# （huggingface_hub 在首次导入时缓存 ENDPOINT，之后修改无效）
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

from app.config import settings
from app.models.schemas import ChatRequest, KnowledgeSyncRequest, HealthResponse
from app.chains.nl_to_sql import build_sql_generation_chain
from app.chains.rag_chain import build_rag_chain
from app.rag.vector_store import VectorStoreManager

app = FastAPI(
    title="智能SQL AI Engine",
    version="1.0.0",
    description="基于 LangChain 的 NL→SQL 智能引擎"
)

# 跨域配置
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 全局：向量存储管理器（懒加载）
vector_store_manager: VectorStoreManager = None


def get_vector_store_manager() -> VectorStoreManager:
    global vector_store_manager
    if vector_store_manager is None:
        vector_store_manager = VectorStoreManager()
    return vector_store_manager


@app.on_event("shutdown")
async def shutdown_event():
    """服务关闭时清理资源"""
    global vector_store_manager
    if vector_store_manager:
        vector_store_manager.close()
        vector_store_manager = None


# ────────────────────────────────
#  健康检查
# ────────────────────────────────
@app.get("/ai/health", response_model=HealthResponse)
async def health_check():
    return HealthResponse(
        status="ok",
        model=settings.LLM_MODEL,
        vector_store=settings.EMBEDDING_MODEL
    )


# ────────────────────────────────
#  非流式 NL→SQL 生成
# ────────────────────────────────
@app.post("/ai/generate")
async def generate_sql(request: ChatRequest):
    """非流式生成 SQL（用于调试/简单场景）"""
    vs = get_vector_store_manager()

    # RAG 检索相关知识点
    knowledge_texts = await vs.search(
        query=request.question,
        top_k=5
    )

    # 构建生成链
    chain = build_sql_generation_chain(knowledge_texts)

    result = await chain.ainvoke({
        "question": request.question,
        "context": json.dumps(request.context) if request.context else "无"
    })

    return {"answer": result, "sources": knowledge_texts}


# ────────────────────────────────
#  SSE 流式 NL→SQL 生成（核心端点）
# ────────────────────────────────
@app.post("/ai/generate/stream")
async def generate_sql_stream(request: ChatRequest):
    """流式生成 SQL - SSE 协议"""
    vs = get_vector_store_manager()

    async def event_stream():
        start_time = time.time()
        thinking_parts = []

        try:
            # ── 阶段1：发送思考过程 - 检索中 ──
            yield f"data: {json.dumps({'type': 'thinking', 'content': '🔍 正在检索相关知识...'}, ensure_ascii=False)}\n\n"
            await asyncio.sleep(0.05)

            # RAG 检索
            knowledge_texts = await vs.search(
                query=request.question,
                top_k=5
            )

            # 发送检索到的知识库信息
            if knowledge_texts:
                for i, kt in enumerate(knowledge_texts):
                    thinking_msg = f"📚 匹配到知识库 [{i+1}]: {kt.get('name', '未知')}"
                    thinking_parts.append(thinking_msg)
                    yield f"data: {json.dumps({'type': 'thinking', 'content': thinking_msg}, ensure_ascii=False)}\n\n"
                    # Day 12：额外发送结构化 knowledge 事件，前端渲染可点击引用+详情浮框
                    yield f"data: {json.dumps({'type': 'knowledge', 'index': i+1, 'name': kt.get('name', '未知'), 'description': kt.get('description', ''), 'sql': kt.get('content', '')}, ensure_ascii=False)}\n\n"
                    await asyncio.sleep(0.03)

            # ── 阶段2：调用 LLM 流式生成 ──
            yield f"data: {json.dumps({'type': 'thinking', 'content': '🤖 正在生成SQL...'}, ensure_ascii=False)}\n\n"
            await asyncio.sleep(0.03)

            chain = build_sql_generation_chain(knowledge_texts)

            # 流式生成
            # Day 12 修复：原实现漏传 history（多轮测试通过是 RAG 巧合，不是历史注入生效）
            full_response = []
            async for chunk in chain.astream({
                "question": request.question,
                "context": request.context,
                "history": request.history,
            }):
                chunk_text = chunk if isinstance(chunk, str) else str(chunk)
                full_response.append(chunk_text)
                yield f"data: {json.dumps({'type': 'content', 'content': chunk_text}, ensure_ascii=False)}\n\n"
                await asyncio.sleep(0.01)

            # ── 阶段3：完成 ──
            elapsed = round(time.time() - start_time, 1)
            yield f"data: {json.dumps({'type': 'thinking', 'content': f'✅ 已思考 {elapsed}秒'}, ensure_ascii=False)}\n\n"
            yield f"data: {json.dumps({'type': 'done', 'thinking': '\n'.join(thinking_parts), 'elapsed': elapsed}, ensure_ascii=False)}\n\n"

        except Exception as e:
            yield f"data: {json.dumps({'type': 'error', 'content': str(e)}, ensure_ascii=False)}\n\n"

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",  # 禁用 Nginx 缓冲
        }
    )


# ────────────────────────────────
#  知识库同步
# ────────────────────────────────
@app.post("/ai/knowledge/sync")
async def sync_knowledge(request: KnowledgeSyncRequest):
    """同步知识库条目到向量存储"""
    vs = get_vector_store_manager()

    if request.action == "delete":
        # ChromaDB 中存储的 id 格式为 f"{category}_{id}"（与 upsert 一致）
        ids = [f"{item.category}_{item.id}" for item in request.items]
        await vs.delete(ids)
        return {"status": "deleted", "count": len(ids)}

    # upsert
    texts = []
    metadatas = []
    ids = []

    for item in request.items:
        # 构建检索文本：名称 + 描述 + SQL
        doc_text = f"{item.name}"
        if item.description:
            doc_text += f"\n描述：{item.description}"
        if item.sql_logic:
            doc_text += f"\nSQL：{item.sql_logic}"

        texts.append(doc_text)
        metadatas.append({
            "id": item.id,
            "name": item.name,
            "category": item.category,
            "description": item.description or ""
        })
        ids.append(f"{item.category}_{item.id}")

    await vs.upsert(texts, metadatas, ids)
    return {"status": "synced", "count": len(texts)}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host=settings.SERVER_HOST, port=settings.SERVER_PORT, reload=True)

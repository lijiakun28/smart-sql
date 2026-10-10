"""
智能SQL - Python AI 引擎入口
FastAPI 服务，负责 NL→SQL、RAG 检索、流式输出
"""
import os
import time
import json
import asyncio
import logging
import secrets

# ⚠️ 必须在导入任何 HuggingFace 相关库之前设置镜像地址
# （huggingface_hub 在首次导入时缓存 ENDPOINT，之后修改无效）
os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse

logger = logging.getLogger("smart_sql.ai")

from app.config import settings
from app.models.schemas import ChatRequest, KnowledgeSyncRequest, HealthResponse
from app.chains.nl_to_sql import build_sql_generation_chain
from app.chains.rag_chain import build_rag_chain
from app.rag.vector_store import VectorStoreManager
from app.rag.doc_builder import build_doc_text, build_knowledge_id

app = FastAPI(
    title="智能SQL AI Engine",
    version="1.0.0",
    description="基于 LangChain 的 NL→SQL 智能引擎"
)

# 跨域配置
# ⚠️ 原来是 allow_origins=["*"] 叠加 allow_credentials=True ——
# 浏览器规范下这是非法组合（凭据请求会被拒），且等于对所有源开放。
# 本服务实际只被 Java 中间层服务端调用，浏览器不直连，白名单收窄到前端来源即可。
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Content-Type", "X-Internal-Token"],
)


async def verify_internal_token(x_internal_token: str = Header(default="")):
    """校验内部调用令牌，用于 /ai/* 下的全部业务接口（/ai/health 除外）。

    ⚠️ 未配置 INTERNAL_API_TOKEN 时一律拒绝（fail-closed）：
    "忘了配"不能等价于"没有防护"。
    比较用 secrets.compare_digest，避免逐字符比较带来的时序侧信道。

    调用方只有 Java 中间层（AiProxyService 通过 WebClient 的 defaultHeader 统一携带）。
    注意：因为 fail-closed，令牌未配置或两端不一致时**整个问答功能都会不可用**
    —— 这是刻意的：安全配置缺失必须立刻可见，而不是悄悄退化成无防护。
    /ai/health 保持公开，供部署探活使用（只暴露模型名，无敏感信息）。
    """
    expected = settings.INTERNAL_API_TOKEN
    if not expected:
        logger.error("INTERNAL_API_TOKEN 未配置，拒绝全部 /ai 业务接口请求")
        raise HTTPException(status_code=503, detail="服务未配置内部令牌，接口不可用")
    if not secrets.compare_digest(x_internal_token, expected):
        logger.warning("内部令牌校验失败（调用方未携带或令牌不一致）")
        raise HTTPException(status_code=401, detail="未授权")


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
@app.post("/ai/generate", dependencies=[Depends(verify_internal_token)])
async def generate_sql(request: ChatRequest):
    """非流式生成 SQL（用于调试/简单场景）

    需要内部令牌：本接口会消耗 LLM 额度，裸奔等于把 key 借给任何能访问到端口的人。
    """
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
        # ⚠️ 必须传结构化 context 本身，不能 json.dumps。
        # format_context 要求 dict/list 输入，收到字符串会直接返回"无"，
        # 用户选中的上下文被**静默丢弃**（流式端点传的是 request.context，两端曾不一致）。
        "context": request.context
    })

    return {"answer": result, "sources": knowledge_texts}


# ────────────────────────────────
#  SSE 流式 NL→SQL 生成（核心端点）
# ────────────────────────────────
@app.post("/ai/generate/stream", dependencies=[Depends(verify_internal_token)])
async def generate_sql_stream(request: ChatRequest):
    """流式生成 SQL - SSE 协议

    需要内部令牌：同 /ai/generate，本接口同样消耗 LLM 额度。
    """
    vs = get_vector_store_manager()

    async def event_stream():
        start_time = time.time()
        thinking_parts = []

        try:
            # ── 阶段1：发送思考过程 - 检索中 ──
            yield f"data: {json.dumps({'type': 'thinking', 'content': '🔍 正在检索相关知识...'}, ensure_ascii=False)}\n\n"
            await asyncio.sleep(0.05)

            # RAG 检索（top_k=8：知识库 22 条体量小，多注入保证关联表
            # user_info 等在点名条目占位后仍能进入上下文，如题目2 5G流量分析）
            knowledge_texts = await vs.search(
                query=request.question,
                top_k=8
            )

            # 发送检索到的知识库信息
            if knowledge_texts:
                for i, kt in enumerate(knowledge_texts):
                    thinking_msg = f"📚 匹配到知识库 [{i+1}]: {kt.get('name', '未知')}"
                    thinking_parts.append(thinking_msg)
                    yield f"data: {json.dumps({'type': 'thinking', 'content': thinking_msg}, ensure_ascii=False)}\n\n"
                    # 额外发送结构化 knowledge 事件，前端渲染可点击引用+详情浮框
                    yield f"data: {json.dumps({'type': 'knowledge', 'index': i+1, 'name': kt.get('name', '未知'), 'description': kt.get('description', ''), 'sql': kt.get('content', '')}, ensure_ascii=False)}\n\n"
                    await asyncio.sleep(0.03)

            # ── 阶段2：调用 LLM 流式生成 ──
            yield f"data: {json.dumps({'type': 'thinking', 'content': '🤖 正在生成SQL...'}, ensure_ascii=False)}\n\n"
            await asyncio.sleep(0.03)

            chain = build_sql_generation_chain(knowledge_texts)

            # 流式生成
            # 修复：原实现漏传 history（多轮测试通过是 RAG 巧合，不是历史注入生效）
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

        except Exception:
            # 不回吐 str(e)：内部异常可能含文件路径、连接串等信息。
            # 真实异常写服务端日志，对外只给通用文案。
            logger.exception("流式生成失败")
            yield f"data: {json.dumps({'type': 'error', 'content': 'AI 引擎处理失败，请稍后重试'}, ensure_ascii=False)}\n\n"

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
@app.post("/ai/knowledge/sync", dependencies=[Depends(verify_internal_token)])
async def sync_knowledge(request: KnowledgeSyncRequest):
    """同步知识库条目到向量存储（仅限携带内部令牌的 Java 中间层调用）"""
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
        # ⚠️ doc_text 与 doc_id 必须走公共构造函数：
        # 这里是实时同步路径，与 scripts/sync_knowledge.py 的全量对账路径共用同一份实现，
        # 否则两条路径会写出不同结构的文档，导致向量漂移
        texts.append(build_doc_text(item.name, item.description, item.sql_logic))
        metadatas.append({
            "id": item.id,
            "name": item.name,
            "category": item.category,
            "description": item.description or ""
        })
        ids.append(build_knowledge_id(item.category, item.id))

    await vs.upsert(texts, metadatas, ids)
    return {"status": "synced", "count": len(texts)}


if __name__ == "__main__":
    import uvicorn
    # 模块全限定名必须是 app.main：从项目根 `python -m app.main` 调用时，
    # 裸 "main:app" 找不到模块（只有 cd 进 app/ 才碰巧可用）
    uvicorn.run("app.main:app", host=settings.SERVER_HOST, port=settings.SERVER_PORT, reload=True)

"""
智能SQL - Pydantic 数据模型
"""
from pydantic import BaseModel, Field
from typing import Optional, List


class ChatRequest(BaseModel):
    """问答请求"""
    question: str = Field(..., description="用户自然语言问题")
    command_type: str = Field(default="generate", description="命令类型: generate|explain|find_table|find_code")
    session_id: Optional[str] = Field(default=None, description="会话ID，用于多轮对话")
    context: Optional[dict] = Field(default=None, description="上下文信息（表名/代码片段等）")
    history: Optional[list] = Field(default=None, description="多轮对话历史: [{role, content}, ...]，由 Java 从 MySQL 读取传入")


class KnowledgeItem(BaseModel):
    """知识库条目（用于同步到向量库）"""
    id: int
    name: str
    description: Optional[str] = None
    sql_logic: Optional[str] = None
    category: str = "term"  # term | caliber


class KnowledgeSyncRequest(BaseModel):
    """知识库同步请求"""
    items: List[KnowledgeItem]
    action: str = "upsert"  # upsert | delete


class ChatResponse(BaseModel):
    """问答响应（非流式）"""
    answer: str
    thinking: Optional[str] = None
    sources: List[dict] = Field(default_factory=list)


class HealthResponse(BaseModel):
    """健康检查"""
    status: str = "ok"
    model: str
    vector_store: str

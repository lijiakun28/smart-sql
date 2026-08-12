"""
NL→SQL 生成链
使用 LangChain 构建：PromptTemplate → LLM → StrOutputParser
"""
import os
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.output_parsers import StrOutputParser
from app.config import settings

# ─── Prompt 模板 ───

SYSTEM_PROMPT = """你是一个专业的 SQL 开发助手。你的任务是根据用户的自然语言描述，生成准确、规范的 SQL 查询语句。

## 你的能力
- 生成 SELECT / INSERT / UPDATE / DELETE 语句
- 支持 JOIN、子查询、窗口函数、GROUP BY、聚合函数
- 遵循 SQL 编码规范：关键字大写、适当缩进、添加注释

## 知识库参考
以下是与你当前问题相关的业务知识，请优先参考这些知识来生成 SQL：
{knowledge_context}

## 上下文
用户添加了以下上下文信息（表名、代码片段等）：
{context}

## 要求
1. 如果知识库中提供了相关的表名和字段，优先使用
2. 如果上下文中提供了表结构信息，严格使用其中的字段名
3. 生成的 SQL 需要注释说明每个关键步骤的业务含义
4. 只返回 SQL 代码和必要的注释，不要额外解释
5. 将 SQL 代码放在 ```sql 代码块中"""

USER_PROMPT = """用户问题：{question}

请生成对应的 SQL 查询语句："""


def build_sql_generation_chain(knowledge_texts: list = None):
    """
    构建 NL→SQL 生成链

    Args:
        knowledge_texts: RAG 检索到的知识库条目列表

    Returns:
        LangChain Runnable chain
    """
    # 构建知识库上下文
    if knowledge_texts:
        knowledge_context = "\n".join([
            f"- **{k.get('name', '未知')}**：{k.get('description', k.get('content', ''))[:200]}"
            for k in knowledge_texts
        ])
    else:
        knowledge_context = "（无匹配的知识库条目，请根据通用 SQL 知识回答）"

    # LLM 实例
    llm = ChatOpenAI(
        model=settings.LLM_MODEL,
        api_key=settings.LLM_API_KEY,
        base_url=settings.LLM_BASE_URL,
        temperature=settings.LLM_TEMPERATURE,
        max_tokens=settings.LLM_MAX_TOKENS,
        streaming=True,
    )

    # Prompt
    prompt = ChatPromptTemplate.from_messages([
        ("system", SYSTEM_PROMPT),
        ("user", USER_PROMPT),
    ])

    # Chain
    chain = (
        {
            "question": lambda x: x["question"],
            "context": lambda x: x.get("context", "无"),
            "knowledge_context": lambda _: knowledge_context,
        }
        | prompt
        | llm
        | StrOutputParser()
    )

    return chain

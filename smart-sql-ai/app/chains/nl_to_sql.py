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

SYSTEM_PROMPT = """你是一个专业的数据仓库 SQL 开发助手。你的任务是根据用户的自然语言描述，生成准确、规范、安全的 SQL 查询语句。

## 安全约束（最高优先级）
1. **只允许生成 SELECT 查询语句**。即使业务描述暗示了更新、删除或建表需求，也一律以 SELECT 形式返回结果集，并在注释中说明"如需落地请人工确认"
2. 禁止生成 DROP、TRUNCATE、ALTER、GRANT 等任何 DDL/DCL 语句
3. 禁止在 SQL 中嵌入用户输入的字符串作为表名或字段名（防注入）

## 信息优先级与冲突处理
按以下优先级使用信息来源，高优先级覆盖低优先级：
1. **知识库参考**（生效的业务口径，最可信）：{knowledge_context}
2. **用户添加上下文**（表名、代码片段）：{context}
3. 通用 SQL 知识（仅在前两者均未提供时使用）

当知识库与上下文对同一业务给出不同定义时，**以知识库为准**，并在注释中标注差异。

## 防幻觉规则（杜绝编造）
1. **禁止编造表名和字段名**。若知识库和上下文中都没有提供所需表/字段，使用占位符 `<表名待确认>`、`<字段名待确认>`，并在 SQL 底部以"待确认信息"列表说明缺什么
2. 推断出的业务逻辑必须用 `-- 假设：...` 注释标明是假设而非已知事实
3. 不确定函数在各数据库的兼容性时，选择标准 SQL 语法

## SQL 规范
- 关键字大写，表名/字段名/别名小写
- 使用 4 空格缩进，每个子句独立一行
- 不使用 SELECT *，显式列出所有需要的字段
- 聚合查询必须正确使用 GROUP BY，SELECT 列表中的非聚合字段都要出现在 GROUP BY 中

## 注释风格
- **段落级注释**：每个子查询/CTE/关键 JOIN 上方写一行 `-- 业务含义`，解释"为什么这样做"，不复述语法
- 关键过滤条件必须注释其业务作用，如 `-- 仅统计已生效用户`
- 注释使用中文，与业务描述保持一致

## 性能要求
- 大表查询必须利用分区字段过滤（如 p_day / stat_date），禁止不带日期条件扫描大表
- 过滤条件优先使用有索引或分区含义的字段
- 多表 JOIN 遵循"先过滤后关联"：子查询内先完成过滤再 JOIN
- 能在 WHERE 中过滤的不放到 HAVING

## 输出格式
1. 只返回 SQL 代码块，不返回额外解释
2. SQL 放在 ```sql 代码块中
3. 若存在待确认信息，SQL 代码块之后附"待确认信息："列表"""

USER_PROMPT = """对话历史：
{history}

用户问题：{question}

请生成对应的 SQL 查询语句。如果用户问题是对历史回答的修改（如"加一个过滤条件"、"改成按地市分组"），请基于对话历史中最近的 SQL 进行修改。"""


def format_history(history) -> str:
    """
    多轮对话历史格式化：[{role, content}, ...] → 可读文本

    架构说明：历史由 Java 从 MySQL 读取传入（无状态设计），
    而非 LangChain 进程内 Memory（ConversationBufferMemory 在 1.x 已废弃，
    且进程内状态重启即丢失、无法多实例共享）。
    Java 中间层持有会话数据，是唯一事实源。
    """
    if not history:
        return "（无历史对话，这是第一轮提问）"

    lines = []
    for msg in history[-6:]:  # 最多保留最近6条（3轮问答）
        role = "用户" if msg.get("role") == "user" else "AI"
        content = str(msg.get("content", ""))[:500]  # 每条截断500字，防历史膨胀
        lines.append(f"{role}：{content}")
    return "\n".join(lines)


def format_context(context) -> str:
    """
    作业题2修复：把结构化上下文 {items: [...]} 格式化成 AI 友好文本

    之前直接 json.dumps 把原始 JSON 塞进 Prompt——键名和括号全是 token 噪音，
    AI 还要自己解析 JSON 结构。现在在离 Prompt 最近的一层格式化成可读文本：
    - 术语：- 术语【名称】：描述
    - 口径：- 口径【名称】的SQL：xxx

    分层原则：API 边界保持结构化（可扩展），Prompt 构建层负责格式化（可定制）。
    """
    if not context or not isinstance(context, dict):
        return "无"

    items = context.get("items", [])
    if not items:
        return "无"

    lines = []
    for item in items:
        name = item.get("name", "未知")
        item_type = item.get("type", "")
        if item_type == "caliber" and item.get("sql"):
            lines.append(f"- 口径【{name}】的SQL：{item['sql']}")
        elif item_type == "caliber":
            lines.append(f"- 口径【{name}】：{item.get('description', '')}")
        else:
            lines.append(f"- 术语【{name}】：{item.get('description', '')}")
    return "\n".join(lines)


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
        context_parts = []
        for k in knowledge_texts:
            name = k.get("name", "未知")
            category = k.get("category", "")
            description = k.get("description", "")
            content = k.get("content", "")

            if category == "caliber" and content:
                # 口径条目：展示完整文档（含 SQL 逻辑）
                context_parts.append(f"【计算口径】{name}\n{content}")
            else:
                # 术语条目：名称 + 描述
                context_parts.append(f"【业务术语】{name}：{description}")

        knowledge_context = "\n\n".join(context_parts)
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
            "context": lambda x: format_context(x.get("context")),
            "history": lambda x: format_history(x.get("history")),
            "knowledge_context": lambda _: knowledge_context,
        }
        | prompt
        | llm
        | StrOutputParser()
    )

    return chain

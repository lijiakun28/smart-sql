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

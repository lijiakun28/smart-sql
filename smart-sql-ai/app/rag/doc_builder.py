"""
知识条目 → 向量库文档 的构造逻辑（唯一事实源）

⚠️ 这段逻辑被两条路径共用：
  1. 实时同步：Java 知识库 CRUD 后 @Async 调 /ai/knowledge/sync（app/main.py）
  2. 全量对账：手动跑 scripts/sync_knowledge.py --full
两条路径只要写出**不同结构**的 doc_text，同一份知识在两个入口就会产生不同向量，
检索得分随机漂移且极难排查（项目文档 PITFALL-检索分数0.42到0.53详解 §4.1 已警告过）。
因此这里必须是唯一实现——测试里有断言守住这一点。
"""


def build_knowledge_id(category: str, item_id) -> str:
    """ChromaDB 唯一 doc_id。

    term 与 caliber 各自自增，id 会重号（term_11 与 caliber_11 同时存在），
    所以必须带类别前缀；metadata.id 里的裸数值不做唯一标识用。
    """
    return f"{category}_{item_id}"


def build_doc_text(name: str, description: str = "", sql_logic: str = "") -> str:
    """构造用于向量化的检索文档。

    标题重复 5 次是**刻意的**，不是笔误：embedding 是平均池化，口径条目的 SQL
    和维表说明很长，标题语义会被稀释。实测同一查询的相似度演进为
    1x → 0.42（排第 4）、2x → 0.53、3x → 0.81、5x → 0.86。
    详见 PITFALL-43-标题加权详解.md。
    """
    doc_text = "\n".join([name] * 5)
    if description:
        doc_text += f"\n描述：{description}"
    if sql_logic:
        doc_text += f"\nSQL：{sql_logic}"
    return doc_text

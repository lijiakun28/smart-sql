"""
doc_text / doc_id 构造逻辑的回归断言

这段逻辑是检索得分的直接输入，且被两条同步路径共用，改动必须是有意识的。
"""
import pathlib

from app.rag.doc_builder import build_doc_text, build_knowledge_id


def test_doc_text_golden_value():
    """钉住"标题 5 次加权"与两个前缀的确切格式。

    标题重复次数直接决定检索相似度（实测 1x→0.42、5x→0.86），
    这里改成什么值都会被断言拦下，必须是有意识的改动。
    """
    assert build_doc_text("用户数", "统计用户数量", "SELECT 1") == (
        "用户数\n用户数\n用户数\n用户数\n用户数\n描述：统计用户数量\nSQL：SELECT 1"
    )


def test_doc_text_omits_empty_sections():
    """空描述/空 SQL 不应留下孤零零的前缀行"""
    assert build_doc_text("用户数") == "用户数\n用户数\n用户数\n用户数\n用户数"
    assert "描述：" not in build_doc_text("用户数", "", "SELECT 1")
    assert "SQL：" not in build_doc_text("用户数", "统计用户数量", "")


def test_knowledge_id_disambiguates_categories():
    """term 与 caliber 的自增 id 会重号，doc_id 必须带类别前缀"""
    assert build_knowledge_id("term", 11) == "term_11"
    assert build_knowledge_id("caliber", 11) == "caliber_11"
    assert build_knowledge_id("term", 11) != build_knowledge_id("caliber", 11)


def test_both_sync_paths_share_the_builder():
    """架构守卫：实时同步与全量对账必须共用同一份实现。

    这两条路径曾经是逐行复制的双写，项目文档（PITFALL-检索分数0.42到0.53详解 §4.1）
    警告过"两条路径写不同结构 → 向量随机漂移"。此断言防止它再次退化成双写。
    """
    root = pathlib.Path(__file__).resolve().parents[1]
    for rel in ("app/main.py", "scripts/sync_knowledge.py"):
        src = (root / rel).read_text(encoding="utf-8")
        assert "build_doc_text(" in src, f"{rel} 未使用公共 doc_text 构造函数"
        assert "build_knowledge_id(" in src, f"{rel} 未使用公共 doc_id 构造函数"

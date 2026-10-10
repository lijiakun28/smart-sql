"""
回归断言：针对体检发现的具体缺陷。

每个用例对应一个真实 bug 的失败断言，先看它失败、再改代码。
"""
from app.chains.nl_to_sql import format_context
from app.rag.vector_store import distance_to_score


# ────────────────────────────────────────────────
# A5: 余弦距离为 0.0（完全匹配）不应被当成"缺失"
# ────────────────────────────────────────────────
def test_perfect_match_scores_one_not_zero():
    """距离 0.0 = 完全匹配，必须记满分。

    原实现 `round(1 - distance, 4) if distance else 0` 把 0.0 当 falsy，
    导致最佳命中反而显示 0 分。
    """
    assert distance_to_score(0.0) == 1.0


def test_missing_distance_scores_zero():
    """缺列/None 才是"未知"，记 0 分。"""
    assert distance_to_score(None) == 0.0


def test_normal_distance_converts_to_similarity():
    assert distance_to_score(0.5) == 0.5
    assert distance_to_score(0.13) == 0.87


# ────────────────────────────────────────────────
# A4: format_context 必须容忍 list / None / 空结构
# ────────────────────────────────────────────────
def test_format_context_accepts_dict_with_items():
    """前端实际发送的形状：{items: [...]}"""
    ctx = {"items": [{"type": "term", "name": "用户数", "description": "统计用户数量"}]}
    assert "用户数" in format_context(ctx)


def test_format_context_accepts_bare_list():
    """裸数组也应被处理，而不是静默丢弃成"无"。

    原实现对非 dict 输入直接 return "无"——用户选中的上下文凭空消失，
    且没有任何日志或报错。
    """
    ctx = [{"type": "term", "name": "用户数", "description": "统计用户数量"}]
    assert "用户数" in format_context(ctx)


def test_format_context_renders_caliber_sql():
    """口径要注入完整 SQL（术语只注入描述）——这是刻意设计，防回归。"""
    ctx = {"items": [{"type": "caliber", "name": "高价值用户", "sql": "SELECT 1"}]}
    out = format_context(ctx)
    assert "高价值用户" in out and "SELECT 1" in out


def test_format_context_empty_returns_none_marker():
    for empty in (None, {}, {"items": []}, []):
        assert format_context(empty) == "无"

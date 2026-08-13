"""
Embedding 模型封装
使用本地 sentence-transformers 模型，无需外部 API
"""
import os

# 国内网络环境下使用 HuggingFace 镜像加速模型下载
# （海外网络可删除此设置）
if not os.environ.get("HF_ENDPOINT"):
    os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"

from app.config import settings


def get_embedding_model():
    """
    获取 Embedding 模型实例（懒加载）

    使用的是 paraphrase-multilingual-MiniLM-L12-v2：
    - 支持中文和 50+ 语言
    - 768 维向量
    - 模型约 470MB（首次自动下载到本地）
    - 纯本地运行，不产生 API 费用
    """
    from langchain_huggingface import HuggingFaceEmbeddings

    return HuggingFaceEmbeddings(
        model_name=settings.EMBEDDING_MODEL,
        model_kwargs={"device": "cpu"},
        encode_kwargs={"normalize_embeddings": True}
    )

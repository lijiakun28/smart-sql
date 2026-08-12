"""
智能SQL - AI 引擎配置
所有敏感信息通过 .env 文件注入，不写入代码
"""
import os
from dotenv import load_dotenv

load_dotenv()


class Settings:
    # LLM 配置（兼容 OpenAI 接口，默认使用 DeepSeek）
    LLM_API_KEY = os.getenv("LLM_API_KEY", "your-api-key-here")
    LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.deepseek.com")
    LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-chat")
    LLM_TEMPERATURE = float(os.getenv("LLM_TEMPERATURE", "0.1"))
    LLM_MAX_TOKENS = int(os.getenv("LLM_MAX_TOKENS", "4096"))

    # Embedding 模型（本地 sentence-transformers，无需外部 API）
    EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "paraphrase-multilingual-MiniLM-L12-v2")

    # ChromaDB 持久化路径
    CHROMA_PERSIST_DIR = os.getenv("CHROMA_PERSIST_DIR", "./chroma_db")

    # 服务端口
    SERVER_HOST = os.getenv("SERVER_HOST", "0.0.0.0")
    SERVER_PORT = int(os.getenv("SERVER_PORT", "8000"))

    # Java 中间层地址（用于回调同步知识库）
    JAVA_SERVER_URL = os.getenv("JAVA_SERVER_URL", "http://localhost:8080")


settings = Settings()

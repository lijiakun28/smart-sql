# 智能SQL - AI 驱动的 SQL 开发助手

> 基于 LangChain + RAG 的企业级智能 SQL 助手：自然语言转 SQL、知识库检索增强、多轮对话记忆、流式输出。
> 复刻自《智能SQL需求说明书 v2.0》核心功能，14 天从零搭建。

## 🏗 架构总览

```
┌──────────────────────────────────────────────┐
│        Vue 3 + Element Plus (localhost:5173)  │
│        会话管理  │  问答对话  │  知识库管理      │
└──────────────────┬───────────────────────────┘
                   │ HTTP REST + SSE
┌──────────────────▼───────────────────────────┐
│     Java Spring Boot 3.5 (localhost:8080)     │
│     会话CRUD  │  知识库CRUD  │  AI代理  │  日志  │
└──────────────────┬───────────────────────────┘
                   │ REST API
┌──────────────────▼───────────────────────────┐
│    Python FastAPI + LangChain (localhost:8000) │
│     NL→SQL  │  RAG检索  │  多轮记忆  │  SSE    │
└──────────┬──────────────┬────────────────────┘
           │              │
    ┌──────▼──────┐  ┌───▼────────┐
    │   MySQL 8   │  │  ChromaDB   │
    │  业务数据    │  │  向量存储   │
    └─────────────┘  └────────────┘
```

## ✨ 核心功能

| 模块 | 功能 |
|------|------|
| AI 问答 | 自然语言转 SQL（带业务注释）、代码注释、快捷找表/找代码 |
| RAG 检索增强 | 业务术语库/计算口径库向量化检索，注入 Prompt 生成 |
| 多轮对话 | 会话历史记忆，追问自动基于上文修改 SQL |
| 流式输出 | SSE 逐字渲染 + 思考过程展示 + 知识引用可点击 |
| 会话管理 | 新建/切换/重命名/删除（右键菜单）、历史浮框搜索 |
| 知识库管理 | 术语/口径 CRUD + 状态机（新增→生效→失效）+ 自动同步向量库 |
| 上下文管理 | 表/代码选择注入，已添加置灰，最多 20 条 |
| 安全防护 | 仅生成 SELECT、防幻觉占位符、参数校验、XSS 防护 |

## 🛠 技术栈

- **前端**: Vue 3 + Element Plus + Pinia + Vite
- **中间层**: Java 25 + Spring Boot 3.5 + MyBatis-Plus + WebFlux
- **AI 引擎**: Python 3.14 + FastAPI + LangChain + ChromaDB
- **模型**: DeepSeek (OpenAI 兼容接口) + paraphrase-multilingual-MiniLM（本地 Embedding）
- **存储**: MySQL 8 + ChromaDB（进程内持久化）

## 🚀 快速启动

### 1. 初始化数据库
```bash
mysql -u root -p --default-character-set=utf8mb4 < smart-sql-server/src/main/resources/db/schema.sql
```

### 2. 配置密钥
```bash
cd smart-sql-ai
cp .env.example .env   # 填入 LLM_API_KEY 和 MYSQL_PASSWORD
```

### 3. 启动 AI 引擎（8000）
```bash
cd smart-sql-ai
python -m venv .venv && source .venv/Scripts/activate
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
python -m uvicorn app.main:app --port 8000
```

### 4. 同步知识库到向量库
```bash
python scripts/sync_knowledge.py --full
```

### 5. 启动 Java 中间层（8080）
```bash
cd smart-sql-server
mvn spring-boot:run   # 需 JDK 25 环境
```

### 6. 启动前端（5173）
```bash
cd smart-sql-frontend
npm install
npm install -D sass-embedded
npm run dev
```

访问 http://localhost:5173

## 📚 项目文档

| 文件 | 内容 |
|------|------|
| `DESIGN.md` | 完整设计方案（14 天计划、API 设计、数据库设计） |
| `PITFALLS.md` | 36 个工程踩坑记录（现象/原因/解决/教训） |
| `LEARNING_LOG.md` | 学习者成长记录（作业、点评、简历素材） |
| `RESUME.md` | 简历三件套（项目介绍/认知成长/贡献量化） |

## 📊 核心量化数据

- RAG 对照实验：无 RAG 时表名幻觉率 100%，接入后真实表名命中率 100%
- 检索优化：标题加权使关键知识相似度 0.42 → 0.53（+26%）
- 多轮记忆：追问场景 SQL 修改正确率 100%
- 上下文格式化：Prompt token 开销减少约 50%
- API 回归测试 22/22、7/7 通过

## 📝 License

个人学习项目。

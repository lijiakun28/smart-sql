# 智能SQL

智能SQL是一个面向业务分析场景的自然语言转 SQL 应用。用户可用自然语言提问，系统结合知识库检索结果生成仅包含 `SELECT` 的 SQL，并提供流式输出、多轮会话、知识库管理与上下文引用功能。

## 功能概览

- 自然语言转 SQL：通过 DeepSeek 生成带业务注释的只读 SQL。
- RAG 知识增强：从业务术语与统计口径中检索相关信息，降低表名和字段名幻觉。
- 流式对话：通过 SSE 展示生成过程、回答内容和知识引用。
- 会话管理：支持新建、切换、重命名、删除与重新生成回答。
- 知识库管理：支持术语、统计口径的新增、编辑、状态切换与向量同步。
- 上下文引用：可将知识库条目作为当前问题的显式上下文。
- 安全约束：提示词与后端共同限制只生成 `SELECT`；前端避免将模型内容作为未清洗 HTML 渲染。

## 架构

```text
Vue 3 + Vite (5173)
        │ REST / SSE
Spring Boot (8080)
        │ REST
FastAPI + LangChain (8000)
        ├── MySQL 8
        └── ChromaDB
```

## 技术栈

- 前端：Vue 3、Vite、Element Plus、Pinia、Axios、highlight.js
- 后端：Java 25、Spring Boot 3.5、MyBatis-Plus、WebFlux
- AI 服务：Python 3.14、FastAPI、LangChain、ChromaDB、sentence-transformers
- 模型：DeepSeek（OpenAI 兼容接口）
- 数据库：MySQL 8

## 快速启动

完整的 Windows 安装、配置和验证步骤见 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)。

已完成环境配置时，可以按以下顺序启动：

```powershell
# 终端 1：AI 服务
cd D:\智能SQL\smart-sql-ai
.\.venv\Scripts\Activate.ps1
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000

# 终端 2：Java 服务（将占位密码替换为本机 MySQL 密码）
cd D:\智能SQL\smart-sql-server
$env:MYSQL_PASSWORD = 'YOUR_MYSQL_PASSWORD'
mvn spring-boot:run

# 终端 3：前端
cd D:\智能SQL\smart-sql-frontend
npm run dev
```

打开 http://localhost:5173 。若只需要恢复前端页面，可在 `smart-sql-frontend` 目录运行第三段命令。

## 项目文档

| 文档 | 内容 |
| --- | --- |
| [DESIGN.md](DESIGN.md) | 系统架构、模块边界、数据模型与关键设计决策 |
| [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) | 从零下载、配置、启动、功能验证与故障排查 |
| [PITFALLS.md](PITFALLS.md) | 已处理的工程问题及其解决方案 |

## 目录结构

```text
smart-sql-ai/        # FastAPI、LangChain、RAG 和知识库同步脚本
smart-sql-server/    # Spring Boot API 与 MySQL 持久化
smart-sql-frontend/  # Vue 用户界面
```

## 质量与性能记录

- 检索标题权重优化后，关键口径的检索分数从 0.42 提升至 0.53。
- 上下文由 JSON 结构转换为精简文本，提示词 token 消耗约减少 50%。
- 会话历史改为数据库侧限制最近记录，避免随会话长度增长进行全量读取。

## License

MIT License

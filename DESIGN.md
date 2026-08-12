# 智能SQL - 复刻项目设计方案

> **项目核心指导文档** — 所有开发决策、模块划分、逐日计划均以此为准。
> 原始需求来源：《智能SQL需求说明书v2.0+.doc》

---

## 项目定位

复刻"智能SQL"需求说明书中的核心功能，构建一个 AI 驱动的智能 SQL 开发助手。

- **目标**：用于 CS 本科申请硕士简历，体现 AI / 全栈 / 系统工程能力
- **时间**：14 天 × 5.5h ≈ 77h
- **范围**：核心 MVP（会话管理 + NL→SQL 问答 + 知识库 RAG + 流式输出）
- **双周分支**：可按周分为 milestone-1（Day1-7 核心闭环）和 milestone-2（Day8-14 完善打磨）

---

## 一、架构总览

```
┌──────────────────────────────────────────────┐
│        Vue 3 + Element Plus (localhost:5173)  │
│        会话管理  │  问答对话  │  知识库管理      │
└──────────────────┬───────────────────────────┘
                   │ HTTP REST + SSE
┌──────────────────▼───────────────────────────┐
│     Java Spring Boot 2.7 (localhost:8080)     │
│     会话CRUD  │  知识库CRUD  │  AI代理  │  日志  │
└──────────────────┬───────────────────────────┘
                   │ REST API
┌──────────────────▼───────────────────────────┐
│    Python FastAPI + LangChain (localhost:8000) │
│     NL→SQL  │  RAG检索  │  多轮对话  │  SSE    │
└──────────┬──────────────┬────────────────────┘
           │              │
    ┌──────▼──────┐  ┌───▼────────┐
    │   MySQL     │  │  ChromaDB   │
    │  业务数据    │  │  向量存储   │
    └─────────────┘  └────────────┘
```

**请求流**：用户输入自然语言 → Vue 发 SSE 请求 → Java 转发 → Python RAG 检索 + LLM 生成 → 流式 token 返回 → Vue 逐字渲染

**为什么三层？**
- **Python 单独一层**：LangChain / LLM / 向量库属于 Python 生态，独立部署调试
- **Java 中间层**：承接企业级业务逻辑（CRUD / 日志），Spring Boot 成熟稳定
- **Vue 前端**：现代化 SPA，SSE 流式渲染自然，Element Plus 组件丰富

---

## 二、技术栈

| 层 | 技术 | 版本 | 说明 |
|----|------|------|------|
| 前端 | Vue 3 + Element Plus | 3.4+ / 2.x | SPA，SSE 流式消费，Pinia 状态管理 |
| Java 中间层 | Spring Boot + MyBatis-Plus | 2.7.x (JDK 8) | REST API，Maven 构建 |
| Python AI | FastAPI + LangChain | 3.10+ / 0.3+ | NL→SQL，RAG，SSE 流式 |
| LLM | OpenAI 兼容接口 | deepseek-chat | 便宜，中文能力强（≈2元/百万token） |
| 关系数据库 | MySQL | 8.0 | 会话、知识库、日志 |
| 向量数据库 | ChromaDB | 0.5+ | 进程内嵌，零配置，MVP 阶段无需单独部署 |
| 构建工具 | Maven + npm/vite | - | - |
| 版本管理 | Git | - | 所有代码在 D:\智能SQL\ 下 |

---

## 三、项目目录结构

```
D:\智能SQL\
├── DESIGN.md                     # 本文件 - 核心指导文档
├── README.md                     # 项目总 README（Day 14 完成）
│
├── smart-sql-frontend/           # Vue 3 前端
│   ├── src/
│   │   ├── views/
│   │   │   ├── ChatView.vue          # 问答对话主界面
│   │   │   ├── KnowledgeBase.vue     # 知识库管理
│   │   │   └── HistorySessions.vue   # 历史会话浮框
│   │   ├── components/
│   │   │   ├── SessionPanel.vue      # 会话侧边栏
│   │   │   ├── ChatMessage.vue       # 单条消息（含代码高亮）
│   │   │   ├── ContextSelector.vue   # 添加上下文选择器
│   │   │   └── CommandShortcut.vue   # 命令快捷键
│   │   ├── api/                      # axios + SSE 封装
│   │   │   ├── request.js            # axios 实例
│   │   │   ├── session.js            # 会话 API
│   │   │   ├── knowledge.js          # 知识库 API
│   │   │   └── chat.js               # 问答 SSE API
│   │   ├── store/                    # Pinia 状态
│   │   │   ├── chat.js               # 问答状态
│   │   │   └── session.js            # 会话状态
│   │   └── router/
│   │       └── index.js
│   ├── index.html
│   ├── vite.config.js
│   └── package.json
│
├── smart-sql-server/             # Java Spring Boot 中间层
│   ├── src/main/java/com/smartsql/
│   │   ├── SmartSqlApplication.java
│   │   ├── controller/
│   │   │   ├── SessionController.java
│   │   │   ├── KnowledgeController.java
│   │   │   └── ChatController.java
│   │   ├── service/
│   │   │   ├── SessionService.java
│   │   │   ├── KnowledgeService.java
│   │   │   ├── ChatService.java
│   │   │   └── AiProxyService.java   # 调用 Python AI
│   │   ├── mapper/
│   │   │   ├── SessionMapper.java
│   │   │   ├── MessageMapper.java
│   │   │   ├── KnowledgeTermMapper.java
│   │   │   └── KnowledgeCaliberMapper.java
│   │   ├── entity/
│   │   │   ├── Session.java
│   │   │   ├── Message.java
│   │   │   ├── KnowledgeTerm.java
│   │   │   └── KnowledgeCaliber.java
│   │   ├── dto/
│   │   │   └── Result.java           # 统一响应
│   │   └── config/
│   │       ├── WebMvcConfig.java     # 跨域配置
│   │       └── MyBatisPlusConfig.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/
│   │       └── schema.sql            # 建表 DDL
│   └── pom.xml
│
├── smart-sql-ai/                  # Python AI 引擎
│   ├── app/
│   │   ├── main.py                   # FastAPI 入口
│   │   ├── config.py                 # 配置（API Key 等）
│   │   ├── chains/
│   │   │   ├── nl_to_sql.py          # NL→SQL 链
│   │   │   ├── sql_explain.py        # SQL 解释链
│   │   │   └── rag_chain.py          # RAG 检索链
│   │   ├── rag/
│   │   │   ├── vector_store.py       # ChromaDB 操作
│   │   │   └── embedding.py          # Embedding 封装
│   │   ├── prompts/
│   │   │   ├── nl_to_sql.txt         # NL→SQL prompt 模板
│   │   │   ├── sql_explain.txt       # SQL 解释 prompt 模板
│   │   │   └── find_table.txt        # 找表 prompt 模板
│   │   └── models/
│   │       └── schemas.py            # Pydantic 模型
│   ├── requirements.txt
│   └── README.md
│
└── docs/
    └── 智能SQL需求说明书v2.0+.doc
```

---

## 四、逐日计划（14 天 × 5.5h）

### Day 1：环境搭建 + 数据库设计 + Git 初始化

| 事项 | 内容 |
|------|------|
| **你的任务 (~3h)** | 安装/确认 MySQL 8.0；创建数据库 `smart_sql`；执行建表 DDL；理解表关系 |
| **AI 输出** | project-level 建表 DDL；三个子项目骨架 + .gitignore；Git 首次 commit |
| **检查点** | MySQL 可连接，5 张表创建成功，`git log` 有记录 |

---

### Day 2：Python AI - 骨架 + 基础 NL→SQL

| 事项 | 内容 |
|------|------|
| **你的任务 (~2.5h)** | 理解 LangChain 三大核心：Chain / PromptTemplate / LLM；注册 DeepSeek API Key |
| **AI 输出** | FastAPI 项目骨架；`/api/generate` 端点（基础 NL→SQL）；Prompt 模板文件；requirements.txt |
| **检查点** | uvicorn 启动成功，curl 简单问题能返回 SQL |

---

### Day 3：Python AI - RAG 知识检索 + 流式输出

| 事项 | 内容 |
|------|------|
| **你的任务 (~2h)** | 理解 RAG：为什么要把知识向量化？；测试检索效果 |
| **AI 输出** | ChromaDB + Embedding 封装；RAG 检索链；SSE 流式端点 `/api/generate/stream` |
| **检查点** | 写入知识库条目后，提问能检索到并生成 SQL；SSE 流式正常 |

---

### Day 4：Java 中间层 - 项目搭建 + 会话管理 API

| 事项 | 内容 |
|------|------|
| **你的任务 (~2.5h)** | 理解 Controller → Service → Mapper 三层；Postman 测试 API |
| **AI 输出** | Spring Boot + MyBatis-Plus 骨架；Session + Message CRUD 完整链路；统一响应 `Result<T>` + 异常处理 |
| **检查点** | Postman CRUD 会话正常，数据库写入正确 |

---

### Day 5：Java 中间层 - 知识库 API + AI 服务代理

| 事项 | 内容 |
|------|------|
| **你的任务 (~2h)** | 理解"为什么 Java 不直接调 LLM"；测试知识库状态流转 |
| **AI 输出** | 业务术语库 + 计算口径库 CRUD API；AiProxyService（RestTemplate → Python）；AOP 日志 |
| **检查点** | 知识库增删改查 + 状态切换正常；Java 代理连通 Python |

---

### Day 6：Vue 前端 - 项目搭建 + 问答主界面

| 事项 | 内容 |
|------|------|
| **你的任务 (~3h)** | 理解 Vue 3 Composition API；参与写聊天气泡/输入框样式 |
| **AI 输出** | Vue 3 + Element Plus + Vite 骨架；ChatView 主界面；API 封装层；Pinia store |
| **检查点** | `npm run dev` 能看到界面，输入框可输入（先 mock 回复） |

---

### Day 7：Vue 前端 - SSE 流式渲染 + 会话管理 ⭐

| 事项 | 内容 |
|------|------|
| **你的任务 (~2.5h)** | 理解 SSE vs HTTP；见证流式输出！ |
| **AI 输出** | SSE 客户端消费；SessionPanel 侧边栏；HistorySessions 浮框 |
| **检查点** | **核心闭环！** 新建会话 → 输入问题 → 流式返回 SQL → 切换会话 |

---

### Day 8：Vue 前端 - 知识库管理页面

| 事项 | 内容 |
|------|------|
| **你的任务 (~2.5h)** | 参与写表格/表单样式；理解分页+搜索交互 |
| **AI 输出** | KnowledgeBase 页面（Tab 切换 + 表格 + 弹窗表单 + 状态管理） |
| **检查点** | 知识库增删改查、生效/失效全部正常 |

---

### Day 9：前后端联调 + Bug 修复

| 事项 | 内容 |
|------|------|
| **你的任务 (~3h)** | 端到端测试；记录 bug；积累调试经验 |
| **AI 任务** | 排查跨域/格式/SSE 中断等典型问题；修 bug |
| **检查点** | 全链路（知识库→提问→RAG→流式SQL→保存）顺畅 |

---

### Day 10：SQL 代码高亮 + 复制/插入 + 点赞点踩

| 事项 | 内容 |
|------|------|
| **你的任务 (~2.5h)** | 理解 highlight.js 原理；写点赞/点踩样式 |
| **AI 输出** | 代码语法高亮；复制+插入按钮；点赞/点踩/停止/重新回答；思考过程展示区 |
| **检查点** | 代码高亮美观、复制正常、点赞可切换 |

---

### Day 11：上下文管理功能

| 事项 | 内容 |
|------|------|
| **你的任务 (~2h)** | 理解 Prompt 上下文注入；测试添加上下文后的回答质量 |
| **AI 输出** | ContextSelector 组件（表/代码两个 tab，搜索+选择+标签展示）；知识库搜索 API |
| **检查点** | 添加表上下文后，生成的 SQL 包含该表字段 |

---

### Day 12：多轮对话记忆 + 思考过程

| 事项 | 内容 |
|------|------|
| **你的任务 (~2h)** | 理解 LangChain Memory；连续追问测试 |
| **AI 输出** | ConversationBufferMemory 集成；思考过程流式展示（含知识库引用） |
| **检查点** | 追问"加时间过滤"，AI 在之前的 SQL 基础上修改 |

---

### Day 13：缓冲日 / 补充功能 / 全面测试

| 事项 | 内容 |
|------|------|
| **你的任务 (~3h)** | 根据进度决定优先级 |
| **AI 任务** | 进度超前→加功能；正常→测试+边界修复；落后→裁减保核心 |
| **检查点** | MVP 稳定版本 |

---

### Day 14：README + 文档 + 简历素材

| 事项 | 内容 |
|------|------|
| **你的任务 (~3h)** | 写 README；截屏/录屏 Demo；整理简历三件套 |
| **AI 输出** | README 模板；润色简历描述；`git tag v1.0.0-mvp` |
| **检查点** | README 完整、架构图清晰、你口头能讲清楚项目 |

---

## 五、数据库设计

```sql
-- 会话表
CREATE TABLE session (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_name VARCHAR(200)  NOT NULL DEFAULT '新会话',
    user_id     VARCHAR(64)    NOT NULL DEFAULT 'default',
    status      TINYINT        NOT NULL DEFAULT 1,  -- 1=active 0=deleted
    create_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 消息表
CREATE TABLE message (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id       BIGINT       NOT NULL,
    role             VARCHAR(16)  NOT NULL,  -- 'user' | 'assistant'
    content          TEXT         NOT NULL,
    thinking_process TEXT,                   -- AI 思考过程
    context_json     TEXT,                   -- 上下文 JSON
    command_type     VARCHAR(32),            -- 命令类型
    feedback         VARCHAR(16),            -- 'like' | 'dislike' | NULL
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id)
);

-- 业务术语库
CREATE TABLE knowledge_term (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    term_name     VARCHAR(200)  NOT NULL,
    business_desc TEXT,
    status        VARCHAR(16)   NOT NULL DEFAULT 'new',  -- new|active|inactive
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 计算口径库
CREATE TABLE knowledge_caliber (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    caliber_name  VARCHAR(200)  NOT NULL,
    sql_logic     TEXT,
    business_desc TEXT,
    status        VARCHAR(16)   NOT NULL DEFAULT 'new',  -- new|active|inactive
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 知识库-表关联
CREATE TABLE knowledge_table_rel (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id   BIGINT       NOT NULL,
    knowledge_type VARCHAR(16)  NOT NULL,  -- 'term' | 'caliber'
    table_en_name  VARCHAR(200) NOT NULL,
    table_cn_name  VARCHAR(200),
    INDEX idx_knowledge (knowledge_id, knowledge_type)
);
```

---

## 六、API 设计（Java 中间层对外）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/sessions` | 创建会话 |
| `GET` | `/api/sessions` | 会话列表 |
| `PUT` | `/api/sessions/{id}/name` | 改名 |
| `DELETE` | `/api/sessions/{id}` | 删除会话 |
| `GET` | `/api/sessions/{id}/messages` | 查询消息列表 |
| `POST` | `/api/sessions/{id}/chat` | 问答（**SSE 流式响应**） |
| `GET` | `/api/knowledge/terms` | 业务术语库列表（支持 ?keyword= 模糊搜索） |
| `POST` | `/api/knowledge/terms` | 新增业务术语 |
| `PUT` | `/api/knowledge/terms/{id}` | 修改业务术语 |
| `DELETE` | `/api/knowledge/terms/{id}` | 删除业务术语 |
| `PUT` | `/api/knowledge/terms/{id}/status` | 生效/失效 |
| `GET` | `/api/knowledge/calibers` | 计算口径库列表 |
| `POST` | `/api/knowledge/calibers` | 新增计算口径 |
| `PUT` | `/api/knowledge/calibers/{id}` | 修改计算口径 |
| `DELETE` | `/api/knowledge/calibers/{id}` | 删除计算口径 |
| `PUT` | `/api/knowledge/calibers/{id}/status` | 生效/失效 |
| `GET` | `/api/knowledge/search?type=table&keyword=` | 上下文搜索 |

### Python AI 内部 API（Java 调用）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/ai/generate` | 非流式 NL→SQL |
| `POST` | `/ai/generate/stream` | **SSE 流式** NL→SQL |
| `POST` | `/ai/knowledge/sync` | 知识库同步到向量库 |

---

## 七、学习路径

| 阶段 | 学什么 | 关键词 |
|------|--------|--------|
| Day 1-3 | Python 虚拟环境、FastAPI 基础、LangChain 入门 | chain, prompt, llm, RAG |
| Day 4-5 | Spring Boot 分层架构、MyBatis-Plus CRUD | Controller, Service, Mapper |
| Day 6-8 | Vue 3 Composition API、组件通信、SSE 客户端 | ref, reactive, EventSource |
| Day 9-11 | 前后端联调、跨域、流式渲染、调试技巧 | CORS, streaming, debug |
| Day 12-14 | 系统闭环、项目文档、简历包装 | README, architecture, demo |

---

## 八、验证标准

1. **Day 7 核心闭环** ⭐：新建会话 → 输入自然语言 → 流式返回 SQL → 代码高亮 → 复制可用
2. **Day 9 知识闭环**：添加业务术语 → 生效 → 提问 → AI 引用该术语生成 SQL
3. **Day 14 终验**：所有 API 可调、前端页面可操作、Git log 清晰、README 完整

---

## 九、Git 提交规范

```
feat: 新功能（feat: 实现 NL→SQL 生成链）
fix: 修复 bug（fix: SSE 流式中断问题）
docs: 文档变更（docs: 更新 API 文档）
refactor: 重构
style: 样式调整
test: 测试相关
chore: 构建/工具变动
```

**分支策略**：`main` 为稳定分支，每天结束时 commit，每个里程碑打 tag。

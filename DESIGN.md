# 智能SQL - 设计文档

> 本文档记录系统的架构设计、关键技术决策与模块划分，作为开发与维护的参考依据。

---

## 一、项目概述

智能SQL 是一个 AI 驱动的 SQL 开发助手，解决数据开发中的三大问题：

1. **需求分析**：业务人员用自然语言描述需求，系统生成符合规范的 SQL
2. **知识传递**：企业业务术语与计算口径沉淀为知识库，RAG 检索注入生成
3. **效率提升**：多轮对话、流式输出、上下文管理缩短从需求到 SQL 的路径

## 二、架构总览

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

**请求流**：用户输入自然语言 → Vue 发 SSE 请求 → Java 读取会话历史并转发 → Python RAG 检索 + LLM 生成 → 流式 token 返回 → Vue 逐字渲染。

## 三、技术选型与理由

| 层 | 技术 | 选型理由 |
|----|------|----------|
| 前端 | Vue 3 + Element Plus + Pinia | SPA 支持 SSE 流式消费；组件生态成熟；Composition API 利于状态拆分 |
| 中间层 | Java 25 + Spring Boot 3.5 + MyBatis-Plus | 企业级业务逻辑（CRUD/状态机/审计）；WebFlux 支持响应式转发 SSE |
| AI 引擎 | Python 3.14 + FastAPI + LangChain | LangChain/向量库生态集中于 Python；FastAPI 异步原生支持 SSE 流式 |
| LLM | DeepSeek（OpenAI 兼容接口） | 中文能力强；成本低（约 2 元/百万 token） |
| Embedding | paraphrase-multilingual-MiniLM-L12-v2 | 本地运行零 API 成本；支持中文；384 维足够 MVP 检索 |
| 向量库 | ChromaDB（进程内持久化） | 零配置；MVP 阶段无需独立部署 |
| 关系库 | MySQL 8 | 会话/消息/知识库结构化存储，成熟稳定 |

**为什么 Java 不直接调 LLM？** 职责分离：Java 管理业务状态（会话、审计、状态机），Python 管理 AI 能力（LangChain、向量库）。AI 引擎可独立扩缩容，业务层不感知模型细节。

## 四、模块划分

```
smart-sql-frontend/    前端 SPA
  ├── views/           页面（问答主界面、知识库管理）
  ├── components/      组件（会话面板、消息卡片、上下文选择器、历史浮框）
  ├── store/           Pinia 状态（会话、聊天）
  └── api/             REST + SSE 客户端封装

smart-sql-server/     Java 中间层
  ├── controller/      REST 端点（会话、知识库、问答）
  ├── service/         业务逻辑（会话/消息/知识库/聊天/AI代理）
  ├── mapper/          MyBatis-Plus 数据访问
  ├── entity/          数据实体
  └── config/          跨域/分页/异步/日志切面/异常处理

smart-sql-ai/         Python AI 引擎
  ├── chains/          LangChain 链（NL→SQL、RAG）
  ├── rag/             ChromaDB 向量存储 + Embedding 适配
  ├── prompts/         Prompt 模板
  └── scripts/         知识库同步/对账脚本
```

## 五、数据库设计

```sql
-- 会话表：逻辑删除（status=1/0），会话名取首条消息前100字
session (id, session_name, user_id, status, create_time, update_time)

-- 消息表：含思考过程、上下文JSON、反馈
message (id, session_id, role, content, thinking_process,
         context_json, command_type, feedback, create_time)

-- 业务术语库：状态机 new→active→inactive，只有 active 对 AI 可见
knowledge_term (id, term_name, business_desc, status, create_time, update_time)

-- 计算口径库：含 SQL 逻辑，RAG 检索的核心知识源
knowledge_caliber (id, caliber_name, sql_logic, business_desc, status, ...)

-- 知识-表关联（预留）
knowledge_table_rel (id, knowledge_id, knowledge_type, table_en_name, table_cn_name)
```

**状态机设计**（需求约束"只有生效的条目 AI 才能使用"）：

| 操作 | MySQL 状态 | 向量库动作 |
|------|-----------|-----------|
| 新增 | new | 不写入 |
| 生效 | active | upsert |
| 修改 | modified | 移出 |
| 失效 | inactive | 移出 |
| 删除 | 物理删除 | 移出 |

## 六、API 设计

### Java 对外（前端调用）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST/GET/PUT/DELETE | `/api/sessions...` | 会话 CRUD + 名称搜索 |
| GET | `/api/sessions/{id}/messages` | 消息列表 |
| POST | `/api/sessions/{id}/chat` | 问答（SSE 流式） |
| PUT | `/api/messages/{id}/feedback` | 点赞/点踩 |
| GET/POST/PUT/DELETE | `/api/knowledge/terms...` | 业务术语库 CRUD |
| GET/POST/PUT/DELETE | `/api/knowledge/calibers...` | 计算口径库 CRUD |
| PUT | `/api/knowledge/{type}/{id}/status` | 生效/失效（@Pattern 校验） |
| GET | `/api/knowledge/search` | 上下文搜索 |

### Python 内部（Java 调用）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ai/generate` | 非流式生成 |
| POST | `/ai/generate/stream` | SSE 流式生成 |
| POST | `/ai/knowledge/sync` | 知识库向量同步（upsert/delete） |

## 七、关键设计决策

### 1. RAG 检索增强
- **为什么**：LLM 训练数据不含企业私有元数据，不注入必然幻觉表名
- **流程**：MySQL 知识库 → 同步向量库 → 提问时检索 Top-5 → 注入 Prompt → 生成
- **验证结果**：对照实验表明接入 RAG 后表名幻觉率从 100% 降至 0
- **文档构建**：标题重复加权（长文档稀释标题语义，加权后相似度 +26%）
- **知识注入**：口径条目注入完整 SQL 逻辑，术语注入名称+描述

### 2. 多轮对话记忆（无状态设计）
- **选择**：Java 从 MySQL 读取最近 6 条消息传入 Python，而非 LangChain 进程内 Memory
- **理由**：MySQL 是唯一事实源，重启不丢、多实例共享；ConversationBufferMemory 进程内且已废弃
- **截断策略**：单点控制（Java 侧 SQL LIMIT 6），Python 不做二次截断

### 3. SSE 流式协议
- **事件类型**：thinking（思考过程）/ content（回答内容）/ knowledge（知识引用结构化数据）/ done（含耗时）/ error
- **停止语义**：客户端断开 → 取消上游订阅（停止消耗 LLM token）+ 保存部分回答
- **落库规范**：Jackson 解析提取纯文本后再入库，原始 JSON 不落库

### 4. 知识库同步与对账
- **同步**：CRUD 后 @Async 异步触发 Python 向量库更新（best-effort，失败告警不阻断主流程）
- **对账**：`sync_knowledge.py --full` 以 MySQL 为权威源清空重建向量库，兜底历史失败残留

### 5. 安全设计
- **SQL 约束**：Prompt 强制仅生成 SELECT，拒绝 DDL/DCL
- **防幻觉**：信息不足时使用占位符 + "待确认信息"列表，禁止编造表名
- **参数校验**：状态变更 @Pattern 校验 + HTTP 400；已删除会话拒绝问答
- **前端安全**：v-text 渲染杜绝 XSS；用户消息按纯文本处理
- **密钥管理**：.env 不入库，数据库密码环境变量注入

### 6. 上下文管理
- **结构化边界**：API 边界保持结构化数据（{items:[...]}），Prompt 构建层格式化（token 开销 -50%）
- **已添加状态**：props 向下传 selectedIds，已添加条目置灰禁止重复选择

## 八、已验证指标

- RAG：表名幻觉率 100% → 0
- 多轮记忆：追问修改正确率 100%
- 检索：标题加权使相似度 0.42 → 0.53
- 回归：API 测试 22/22、7/7
- 安全：非法参数拦截率 100%（400 响应）

# 学习任务记录（Learning Log）

> 记录"我"（学习者）每天的负责部分、完成情况、问题答案。
> 与 PITFALLS.md（技术踩坑）互补：那个记录"机器遇到的问题"，这个记录"人学到的知识"。
> 面试素材：能讲清楚"我负责了什么、我学到了什么"。

---

## Day 1（环境搭建 + 数据库设计 + Git 初始化）

### 任务 1：确认 MySQL 安装与登录
- **负责内容**：找到 MySQL 安装位置、回忆起 root 密码
- **完成情况**：✅
- **你的成果**：
  - 找到了安装目录：`D:\Program Files\MySQL\MySQL Server 8.0\`
  - 回忆起密码：`[已脱敏-见.env]`（自己设置的，最终想起）
- **学到的**：
  - MySQL 服务在 Windows 中以"服务"形式运行（`sc qc MySQL80` 可查安装路径）
  - 安装目录（bin/）与数据目录（ProgramData）是分离的

### 任务 2：理解 ER 关系（数据库表设计）
- **负责内容**：理解 5 张表之间的关系
- **完成情况**：✅（建表时逐表确认）
- **你的答案**（用一句话描述表关系）：
  - `session`（会话）1:N `message`（消息）——一个会话包含多条消息
  - `knowledge_term`（术语）N:M 数据表——通过 `knowledge_table_rel` 关联
  - `knowledge_caliber`（口径）独立存储 SQL 逻辑，供 AI 检索
- **学到的**：一对多用外键，多对多用中间关联表

### 任务 3：注册 DeepSeek API Key
- **负责内容**：在 DeepSeek 平台创建 API
- **完成情况**：✅
- **你的成果**：创建了名为"智能体SQL"的 API Key
- **学到的**：API Key = 你访问大模型的"门禁卡"，泄露等于别人花你的钱

---

## Day 2（Python AI 引擎跑通 NL→SQL）

### 任务 1：理解 LangChain 三大核心概念
- **负责内容**：理解 Chain、PromptTemplate、LLM 调用链
- **完成情况**：📖 已讲解（在等依赖安装时完成理论学习）
- **你的理解**（用自己的话复述）：
  - LLM：只会"文字接龙"的大脑，没有记忆、没有工具
  - PromptTemplate：带变量占位符的模板，运行时填空
  - Chain：把 Prompt → LLM → 输出解析串成流水线
- **面试金句**：`RunnableSequence`（LangChain 链的类型名）

### 任务 2：设计第一条 Prompt 模板 ⭐（作业）
- **负责内容**：改进 `nl_to_sql.py` 中的 SYSTEM_PROMPT
- **完成情况**：⏳ 待完成
- **作业题目**：
  1. AI 生成 SQL 用 `CURRENT_DATE` 对不对？（提示：数据仓库按分区日期查询，如 `p_day='20260812'`）
  2. 知识库里有"当日5G套网到达数"这条口径，为什么 AI 没用 `dwd_5g_user_info` 这个表？
- **答案预留区**：（完成后填写）

```
你的改进版 Prompt：
（写在这里）
```

---

## Day 3（RAG 知识检索全链路）— 进行中

### 任务 1：理解 RAG 概念
- **负责内容**：理解"为什么要把知识向量化"
- **完成情况**：⏳ 待完成
- **预习问题**：
  1. 为什么不直接把全部知识库塞进 Prompt？（提示：token 成本 + 上下文长度限制）
  2. "向量相似度"为什么能代表"语义相关"？（提示：相近含义的句子在向量空间中距离近）

### 任务 2：测试检索效果
- **负责内容**：写入知识条目后提问，观察 AI 是否引用了知识库
- **完成情况**：⏳ 待完成
- **答案预留区**：

```
（记录你的测试问题和观察结果）
```

---

## 简历素材积累

### 项目总述（面试 30 秒版）
> 我独立开发了一个智能 SQL 助手（Smart SQL），基于 LangChain + DeepSeek 实现自然语言转 SQL，集成 RAG 检索增强生成，将业务知识库向量化后注入大模型上下文，大幅提升生成 SQL 的业务准确性。前后端采用 Vue 3 + Spring Boot + Python 三层架构。

### 你的个人贡献点（持续更新）
- [x] 负责数据库 5 张表的结构设计（含 ER 关系梳理）
- [x] 负责 DeepSeek API 的申请与配置
- [ ] 设计并优化 NL→SQL 的 Prompt 模板
- [ ] 测试 RAG 检索效果并调优
- [ ] 端到端系统测试（Day 9）
- [ ] 项目 README 撰写（Day 14）

### 技术关键词（面试前复习清单）
- [x] LangChain: Chain / PromptTemplate / RunnableSequence
- [ ] RAG: 向量化 / 语义检索 / Top-K 召回
- [ ] SSE: 服务端推送 / 流式渲染
- [ ] Spring Boot: 三层架构 / MyBatis-Plus
- [ ] Vue 3: Composition API / Pinia

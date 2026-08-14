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
- **完成情况**：✅ 已提交答案并评审，5 条意见全部落地到代码

**作业第 1 题：为什么 AI 没用 `dwd_5g_user_info`？**
- 我的答案：不知道
- 正确答案（评审后理解）：**AI 根本不知道这个表存在**。知识库数据只在 MySQL 里，还没同步到 AI 检索的 ChromaDB 向量库（检索结果为空 `[]`）。LLM 训练数据里没有企业私有表，只能"幻觉"编造表名。
- 面试升华版："LLM 静态训练数据无法覆盖企业私有元数据，必须用 RAG 在推理时动态注入。Day 2 无 RAG 时 LLM 幻觉表名，Day 3 接入 RAG 后消除——这是 RAG 价值的实验对照。"

**作业第 2 题：跨时区查询用 TIMESTAMP 还是 DATETIME？**
- 我的答案：TIMESTAMP 更好
- 评分：70 分，方向对但太绝对
- 补充 nuance：
  - TIMESTAMP 存 UTC 时刻，随会话时区自动转换 → 适合"时刻"语义（日志、下单时间）
  - DATETIME 存字面时间，不做转换 → 适合"钟表时间/业务日历"（分区日期 p_day）
  - 陷阱：TIMESTAMP 有 2038 年上限；"当地钟表时间"不该随时区漂移
  - 金句："TIMESTAMP 存时刻、DATETIME 存日历，取决于这个时间会不会随观察者时区变化"

**作业第 3 题：Prompt 改进意见（5 条，全部采纳）**
| # | 我的意见 | 评审 | 落地 |
|---|---------|------|------|
| 1 | 知识库与上下文冲突时无处理规则 | ✅ 有效，严重度高 | 增加"信息优先级与冲突处理"章节：知识库 > 上下文 > 通用知识 |
| 2 | UPDATE/DELETE 无安全约束 | ✅✅ 最专业的一条 | 增加"安全约束"章节：只允许 SELECT，拒绝 DDL/DCL |
| 3 | 信息不足时模型编造字段 | ✅ 有效 | 增加"防幻觉规则"：占位符 + 假设注释 + 待确认列表 |
| 4 | 注释风格未细化 | ✅ 有效 | 增加"注释风格"：段落级、业务含义、不复述语法 |
| 5 | 未提及性能 | ✅ 有效 | 增加"性能要求"：分区过滤、先过滤后 JOIN |

**测试验证**（改进后）：
- 幻觉防护：AI 将假设字段标注 `-- 假设：` 并附"待确认信息"列表 ✅
- 安全约束：诱导"删除失效用户"，AI 只返回 SELECT + 占位符 ✅

**评审总结**：发现的问题里，安全约束（#2）和防幻觉（#3）摸到了 Prompt Engineering 三大支柱（角色定位、边界约束、失败模式处理）中的两个——很多工作三年的工程师写 Prompt 都想不到。

---

## Day 3（RAG 知识检索全链路）✅

### 任务 1：理解 RAG 概念
- **负责内容**：理解"为什么要把知识向量化"
- **完成情况**：✅ 通过实战理解
- **核心理解**：
  - LLM 训练数据里没有企业私有表（`dwd_5g_user_info`），不注入就幻觉
  - 向量化 = 把文本变成高维空间中的点，语义相近的文本距离近
  - RAG 流程：MySQL 知识库 → 向量化入库 → 提问时检索 Top-K → 注入 Prompt → LLM 生成
  - 三层验证："检索到 ≠ 模型看到了 ≠ 模型用上了"

### 任务 2：测试检索效果
- **负责内容**：写入知识条目后提问，观察 AI 是否引用了知识库
- **完成情况**：✅ 见证了完整对比实验
- **实验记录**：
  - Day 2（无 RAG）：AI 编造表名 `user_5g_package`
  - Day 3（有 RAG）：AI 使用真实表名 `dwd_5g_user_info` + 真实字段 `p_day`/`package_status`
  - 泛化测试："查一下近7天活跃用户的情况"（非原文提问）→ AI 正确使用 `dwd_user_behavior_log`，且标注"均来自知识库参考"

### 今日学到的 RAG 工程细节
1. **标题加权**：含 SQL 的长文档会稀释标题语义，文档构建时重复标题可提升检索排名（0.42→0.53）
2. **知识注入格式**：检索结果如何拼进 Prompt 直接影响效果——口径类要带完整 SQL
3. **多进程一致性**：ChromaDB 被外部脚本更新后，服务需重启加载
4. **防幻觉闭环**：无知识 → 占位符+待确认列表；有知识 → 明确标注来源

---

## Day 4（Java 中间层编译启动 + 会话管理 API）✅

### 任务：理解 Spring Boot 三层架构
- **负责内容**：理解 Controller → Service → Mapper 分层；用测试脚本验证每个 API
- **完成情况**：✅ 全程参与排查 4 个连环坑
- **三层架构理解**（用自己的话）：
  - **Controller**（表现层）：接收 HTTP 请求，解析参数，调用 Service，不写业务逻辑
  - **Service**（业务层）：处理业务规则（如逻辑删除 status=0 而非物理删除）
  - **Mapper**（数据层）：MyBatis-Plus 负责 SQL 生成，继承 `BaseMapper` 就有 CRUD

### 今日实战学到的
1. **JDK 23+ 注解处理变更**：Lombok 报"找不到符号"未必是依赖问题，可能是注解处理器没运行——排查顺序：版本兼容 → 依赖树 → 工具链行为
2. **构建插件也需支持 JDK**：Spring Boot 3.3.5 编译过了但 run 不了（class 版本 69）
3. **两套字符集命名体系**：MySQL 叫 utf8mb4，Java 叫 UTF-8，JDBC URL 用 Java 的
4. **逻辑删除 vs 物理删除**：status=0 保留数据可追溯，DELETE 只是 UPDATE 的语法糖
5. **Windows 进程管理**：`netstat -ano` 查端口占用 → `taskkill //F //PID` 杀进程

### 测试验证记录
- 会话创建/列表/改名/模糊搜索/删除/删除后列表 6 项测试全部通过 ✅
- MySQL 数据验证：status=0 逻辑删除生效，时间戳正确 ✅

---

## Day 3+4 综合作业（7 题，总分 88）

### 题 1：向量相似度为什么代表语义相关？— 90 分
**代码来源**：`smart-sql-ai/app/rag/vector_store.py`（search 方法，cosine 空间）、`app/rag/embedding.py`（MiniLM 模型）
**我的回答**：自监督学习捕捉语义模式，编码为高维空间点；语义等价 → 向量表征一致性；"语义内核"被编码为相似向量组合。
**点评**：表述精准。补充三个机制：① 分布假说（MLM 猜词任务使模型被迫理解上下文）② sentence-transformers 对比学习微调（专门优化句向量）③ 余弦距离看方向=语义（normalize 后等于内积）。

### 题 2：新增知识能立即被 AI 用上吗？— 78 分
**代码来源**：`smart-sql-ai/scripts/sync_knowledge.py`、`app/main.py`（/ai/knowledge/sync 端点，无人调用）
**我的回答**：不能，缺实时同步机制；改进=实时索引更新 + 检索服务热更新 + 缓存失效。
**点评**：方向全对，但漏了项目细节——`/ai/knowledge/sync` 端点早已写好却无人调用；正确方案是 Java CRUD 后自动调它。另"重建索引"措辞不准（upsert 是增量）；缓存建议超前（YAGNI）。

### 题 3：sync_knowledge.py 代码 review — 92 分
**代码来源**：`smart-sql-ai/scripts/sync_knowledge.py`（MYSQL_PASSWORD 硬编码默认值；无 try/except；全量读取）
**我的回答**：安全=硬编码密码进 GitHub 泄漏凭据；修法=强制环境变量 + .gitignore。健壮=无异常处理/重试，全量读取 OOM；修法=不知道。
**点评**：安全 100 分（"默认值=泄漏"意识强，且 .gitignore 被默认密码绕过这个细节值得展开）。修法教学：try/except + 退出码、keyset 分页、upsert 幂等。
**已修复** ✅：密码改为强制环境变量；主流程 try/except + 退出码；批量方案注释预留。

### 题 4：逻辑删除 vs 物理删除 — 85 分
**代码来源**：`smart-sql-server/src/main/java/com/smartsql/service/SessionService.java`（delete 方法）
**我的回答**：逻辑删=审计/追溯/可恢复；物理删=敏感数据/临时数据/超大日志；会话表用逻辑删除因"强制下线"需求。
**点评**：通用场景满分。"强制下线"是通用场景但未扣住需求文档：我们项目的原因是①历史会话是产品功能（保留7天）②行为分析日志保留3个月 ③message 子表关联完整性。

### 题 5：@RequestParam vs @RequestBody — 88 分
**代码来源**：`ChatController.java`（chat 方法 @RequestBody Map）、`SessionController.java`（@RequestParam）
**我的回答**：前者 URL/表单键值对、简单参数；后者请求体 JSON 反序列化、复杂结构。场景：分页参数用前者；高级查询/多轮上下文/模型配置用后者。
**点评**：场景全部正确，多轮对话例子已预演 Day 12。补充：GET 无 body、URL 长度限制（我们的问题输入限 99999 字符，必须 body）、URL 参数进 access log 的安全差异。

### 题 6：ChatService 效率问题 + 业务 bug — 100 分 ⭐
**代码来源**：`smart-sql-server/src/main/java/com/smartsql/service/ChatService.java`（updateSessionNameIfFirstMessage 方法）
**我的回答**：效率=`sessionService.list()` 全表拉取 + Stream 内存过滤；bug=以名称等于"新会话"判断首条消息，用户手动改回会被覆盖。
**点评**：双命中满分。核心思想："判断状态要用状态本身的证据（消息数），不要用可修改的表面属性（名称）"。
**已修复** ✅：SessionService 新增 getById 主键查询 + MessageService 新增 countBySession，以 msgCount==1 判断。

### 题 7：ChatController SSE 落库 bug — 95 分
**代码来源**：`smart-sql-server/src/main/java/com/smartsql/controller/ChatController.java`（chat 方法 fullContent.append）
**我的回答**：chunk 是原始 SSE JSON 而非纯文本，入库的是 JSON 拼接串（非合法 JSON 也非纯文本），导致存储混乱/功能失效/数据膨胀；修法=只提取 content 字段，具体代码不会写。
**点评**：三层问题全看穿。教学：Jackson readTree + JsonNode.path()（防御性）+ 解析失败 log 不中断。
**已修复** ✅：ObjectMapper 解析提取 content/thinking 纯文本落库。

### 作业总评
**优势**：代码 review 能力突出（题 3/6/7 平均 96 分）——这是工程师最值钱的能力。
**短板**：通用知识与"我们的项目"结合不够（题 2/4 扣分点都在这），多问"为什么我们这么设计"。

---

## Day 5（知识库 API 联调 + 自动同步 + AOP 日志）✅

### 任务：测试知识库完整流程 + 理解"为什么 Java 不直接调 LLM"
- **完成情况**：✅ 全程见证"题 2 修复落地"的终极验证

### 今日核心成果（你的作业题 2 变成了真实功能）
1. **自动同步落地**：Java 知识库 CRUD → 自动调用 Python `/ai/knowledge/sync` → 向量库即时更新
   - 规则实现：只有 active 状态入向量库（需求原文"只有生效的条目，智能SQL在做回答时才能使用"）
   - 同步失败 best-effort：告警但不阻断主流程，可手动脚本兜底
2. **终极验证**：新增口径"5G套餐退订用户数" → 生效 → 提问 → **AI 立即使用新知识，无需手动同步和重启**

### 今日学到的
1. **"为什么 Java 不直接调 LLM"的答案**：职责分离——Java 管业务（CRUD/日志/状态机），Python 管 AI（LangChain/向量库生态）。硬在 Java 里调 LLM 会失去 Python AI 生态；且企业里 AI 引擎常需独立扩缩容
2. **字段语义是表级的**：logic-delete 全局配置踩坑——status 在会话表是删除标记、在知识表是业务状态，同一名字不同语义
3. **静默退化最危险**：分页插件缺失时 selectPage 不报错，只返回 total=0——验证接口时不仅要看"通不通"，还要看"数据对不对"
4. **AOP 是什么**：不修改业务代码，横切织入日志/事务/权限——`@Before`/`@AfterReturning` 两个注解搞定问答日志

### 代码来源
- `KnowledgeService.java`（syncTermToAi/syncCaliberToAi/syncToAi）
- `AiProxyService.java`（syncKnowledge 调用 Python）
- `ChatLogAspect.java`（AOP 问答日志）
- `MyBatisPlusConfig.java`（分页插件注册）
- `app/main.py`（/ai/knowledge/sync 的 delete 修复为 f"{category}_{id}"）

---

## Day 5 作业（代码改进与漏洞排查 4 题，总分 82.5）

> 规则调整：从 Day 5 起每天只分配代码改进/漏洞题（最多4道）；概念理解题留到项目完成后统一问。

### 题 1：status 参数未校验漏洞 — 90 分
**代码来源**：`KnowledgeController.java`（changeTermStatus 接口）、`KnowledgeService.java`（同步逻辑只判断 active）
**我的回答**：任意 status 写入导致脏数据，条目陷入非合法状态、查询忽略、需手动修库；修法=Controller 加 @Pattern 校验，理由=职责分离+尽早失败。
**点评**：标准答案。补充陷阱：Spring Boot 3 的 @RequestParam 校验必须加类级 @Validated 才生效（静默失效家族）；进阶方案=枚举参数类型（类型安全>正则安全，非法值自动 400）。
**已修复** ✅：@Validated + @Pattern(regexp="active|inactive")

### 题 2：.block() 阻塞隐患 — 60 分
**代码来源**：`AiProxyService.java`（syncKnowledge 的 .block()）
**我的回答**：上 RabbitMQ/Kafka，CRUD 后发消息，消费者异步处理+重试死信。
**点评**：MQ 方案本身正确但是过度设计（无视题干 @Async 提示）。MVP 答案=@Async 一行注解+线程池。上 MQ 的条件：消息不能丢/多消费者/削峰——我们 best-effort 可手动兜底，不满足任何一条。暴露第二次"超前设计"倾向，口诀：**先量级、后方案**。
**已修复** ✅：@EnableAsync + knowledgeSyncExecutor 线程池 + @Async

### 题 3：WebClient 无超时漏洞 — 95 分
**代码来源**：`AiProxyService.java`（WebClient.create() 无超时配置）
**我的回答**：默认超时无穷大，请求永久阻塞、线程池耗尽、应用不可用；前端一直"加载中"直到浏览器超时；修法不知道。
**点评**：识别满分，尤其"线程池耗尽"的雪崩洞察。教学：连接超时(5s快速失败) vs 响应超时(120s兼顾SSE长响应)两个层次；超时后要转成用户可见的明确错误。
**已修复** ✅：Reactor Netty HttpClient 配置 connectTimeout 5s + responseTimeout 120s

### 题 4：耗时统计陷阱 — 85 分
**代码来源**：`ChatLogAspect.java`、`ChatController.java`（chat 方法只建立 SSE 通道）
**我的回答**：@Around 注解捕获前后时间点算耗时；具体代码不知道。
**点评**：@Around 选对了，但没识破陷阱——chat() 只建 SSE 通道（~1ms），真正耗时在流里。指标与业务语义错位的经典案例。正确做法：消费 Python done 事件自带的 elapsed 字段。
**已修复** ✅：appendChunkContent 解析 done 事件记录 AI 真实耗时

### 作业模式总结
第二次暴露"超前设计"倾向（题2 的 MQ vs @Async）——选型口诀：**先问量级，再选方案**。

---

## Day 6 作业（前端代码改进与漏洞排查 4 题，总分 85）

### 题 1：XSS 漏洞 — 98 分 ⭐
**代码来源**：`smart-sql-frontend/src/components/ChatMessage.vue`（renderedText + v-html）
**我的回答**：v-html 原样插入 DOM，onerror 触发 JS 执行；攻击向量含用户输入和 prompt injection 诱导 AI 输出恶意内容；修法=v-text + CSS 换行。
**点评**：攻击链+双向量（用户输入+prompt injection）完整。补充：同文件 SQL 高亮区 v-html 碰巧安全（highlight.js 默认 HTML 转义）——"碰巧安全≠设计安全"。
**已修复** ✅：v-text + white-space: pre-wrap

### 题 2：停止按钮缺失 — 70 分
**代码来源**：`ChatView.vue`（abortController 闲置）、`api/chat.js`（chatStream 返回 controller）
**我的回答**：abort() 从未调用；"很可能"signal 没传给 fetch；需补停止按钮 UI。
**点评**：核心事实正确，但"signal 没传"是推测且错误（chat.js 第 7 行 signal: controller.signal 传了）。教训：**能读到的结论不要猜**。
**已修复** ✅：handleStop + streamingIndex 追踪 + isStreaming 条件按钮

### 题 3：动态 import 坏味道 — 85 分
**代码来源**：`api/chat.js`（updateFeedback 内 import('./request')）
**我的回答**：每次调用产生模块加载/异步微任务开销；破坏静态分析；修法=顶部静态导入。
**点评**：识别正确。补充两个观察：①函数放错文件（消息反馈 API 应归 message/session 类文件）②动态 import 的正当场景是代码分割/循环依赖应急，10 行模块不沾边。
**已修复** ✅：顶部静态导入

### 题 4：正则健壮性 — 88 分
**代码来源**：`ChatMessage.vue`（extractSql 与 renderedText 两处正则）
**我的回答**：i 修饰符忽略大小写 + \s* 允许空格。
**点评**：方向全对。漏了关键点：同文件 renderedText 的删除正则也有同款问题——只改提取不改删除会"提取到了但没删掉"内容重复。同类逻辑必须同步修改。
**已修复** ✅：SQL_BLOCK_PATTERN 共享常量（/```\s*sql\s*([\s\S]*?)```/gi），提取与删除共用

### 作业模式总结
今日最大收获：**能读到的结论不要猜**（题2 的推测错误）。

---

## Day 7 作业（会话管理代码改进 4 题，总分 86.5）

> 新规则：Day 8 起作业减至 2 道。

### 题 1：会话切换竞态 — 88 分
**代码来源**：`ChatView.vue`（handleSelectSession）、`HistorySessions.vue`（handleOpenHistory）
**我的回答**：请求版本号，只有最新请求允许更新消息列表，配合禁用按钮/加载状态。
**点评**：令牌模式方向正确。补代码：sessionRequestSeq 自增 + 返回校验。金句："防抖/禁用按钮降低概率，令牌消除竞态。"
**已修复** ✅

### 题 2：浮框数据同步 — 80 分
**代码来源**：`HistorySessions.vue`（sessionList 本地快照）
**我的回答**：watch sessionStore.sessions 同步本地列表。
**点评**：方案有效但非最简。陷阱：全屏遮罩挡住侧边栏，出题前提不成立（先验问题再动手）。更简修法：直接消费响应式 store，删除本地快照副本——双数据源问题源于设计而非同步。
**已修复** ✅

### 题 3：删除当前会话缺陷 — 88 分
**代码来源**：`HistorySessions.vue`（handleDelete）、`ChatService.chatStream`（无会话校验）
**我的回答**：三后果全中（残留/可发/后端异常），修法=判断当前会话+清空+关浮框。
**点评**：后端行为比预想更糟——message 表无外键，消息静默写入已删除会话。双层修复：前端重置状态，后端校验会话存在性。金句："前端管体验，后端管数据完整性。"
**已修复** ✅（前后端双层）

### 题 4：scrollToBottom 性能 — 90 分
**代码来源**：`ChatView.vue`（scrollToBottom 每 chunk 调用）
**我的回答**：Layout Thrashing，三后果准确；改进=条件滚动（用户上滑时暂停自动滚动）。
**点评**：术语+后果满分，条件滚动是聊天产品标准做法。补第二武器：rAF 节流合并到每帧一次。金句："性能问题不是'算得快'，是'算得少'和'尊重意图'。"
**已修复** ✅（rAF + 滚动锁双武器）

### 作业模式总结（四天观察）
从"超前设计"（Day 3+4 题2 MQ）到"先验问题再动手"（今天题 2 差一点识破陷阱）——进步明显。

---

## Day 8 作业（知识库页面 2 题）

### 题 1：表单校验漏洞 — 90 分 ⭐
**代码来源**：`KnowledgeBase.vue`（saveTerm/saveCaliber 无校验、无条件 ElMessage.success）
**我的回答**：el-form 无 :rules 无 validate；空字符串提交触发 DB NOT NULL 异常；前端无条件显示"保存成功"产生假象；修法不知道。
**点评**："保存成功假象"洞察是关键分——后端把异常包成 HTTP 200+code 500 时前端照样报喜。双层防线：①el-form rules（体验层，省请求+即时提示）②响应码检查 res.code!==200（真实性层，前端校验可被 curl 绕过）。
**已修复** ✅：rules + validate + 响应码检查（术语/口径双表单）

### 题 2：表单残留 bug — 讲解题
**代码来源**：`KnowledgeBase.vue`（@closed 只重置 editingTerm 不重置 termForm）
**我的回答**：不知道（诚实）。
**讲解**：编辑A→取消→新增，表单残留A的数据，保存会创建重复条目。根源=@closed 只重置编辑标记、没重置表单数据——状态清理不彻底（Day 5 题 1 的孪生兄弟）。修法="进入时初始化 + 离开时清理"双向兜底。
**已修复** ✅：openCreateTerm/openCreateCaliber 显式重置 + @closed 统一清理

### 主题总结
两题同主题：**表单状态生命周期管理**——进入显式初始化（防残留），提交显式校验（防脏数据），离开显式清理（防串台）。"不依赖默认值"。

---

## Day 9（前后端联调 + 全面回归）✅

### 回归测试结果：22/22 API 全部通过 ✅

覆盖范围：
- 会话 CRUD（创建/列表/改名/模糊搜索/删除）5 项
- 问答 SSE 流式（5018 字符）+ 消息列表 + 点赞反馈 3 项
- 知识库术语完整生命周期（新增→生效→查询→修改→失效→删除）6 项
- 非法 status 参数拦截（HTTP 400）✅
- 已删除会话发问被拒 ✅
- 浏览器实测：主界面 + 完整问答链路（真实表名 dwd_user_behavior_log）✅

### 👤 我的浏览器测试清单（完成后打勾）

**会话管理**
- [ ] 新建会话 → 确认左侧列表出现"新会话"
- [ ] 切换不同历史会话 → 消息区正确切换
- [ ] 重命名会话 → 列表立即更新
- [ ] 删除会话 → 列表消失
- [ ] 打开"历史对话"浮框 → 搜索关键词 → 打开某条历史会话

**问答**
- [ ] 输入问题 → 流式逐字输出（不卡顿）
- [ ] 生成过程中点"停止" → 流式中断
- [ ] 点"复制" → 剪贴板有 SQL
- [ ] 展开"💭 思考过程" → 能看到检索到的知识库条目
- [ ] 连续追问第二个问题 → 会话名不被覆盖

**知识库**
- [ ] 术语库/口径库 Tab 切换
- [ ] 新增术语（空名称 → 应被拦截并提示）
- [ ] 编辑 → 取消 → 新增 → 表单应为空
- [ ] 生效/失效 → 状态立即刷新
- [ ] 生效条目的"删除"按钮应为灰色禁用

**发现问题记录**（如有）：

```
（写在这里）
```

### 今日学到的
1. 回归测试的层次：API 层（22 项脚本化断言）→ 浏览器层（真实用户路径）→ 人工清单（你正在做）
2. 测试脚本的断言要覆盖"负向路径"：非法参数、已删除资源、空表单——正向 20 项通过不代表系统健壮

### ⭐ 浏览器测试实战成果（我发现的 4 个 UI 问题，全部修复）
| # | 我发现的 | 根因 | 修复 |
|---|---------|------|------|
| 1 | 重命名/删除操作不便 | hover 才显示的更多菜单难发现 | 右键菜单（contextmenu） |
| 2 | 思考过程展开图标找不到 | el-collapse 默认箭头不可见 | 自定义蓝色箭头按钮+旋转动画 |
| 3 | 点赞/踩图标纯白 | 图标颜色与浅灰背景融合 | 带边框文字按钮"有用/没用" |
| 4 | SQL 表名列名黑色看不清 | highlight.js 用了浅色主题(github.css)，SQL 块却是深色背景 | 换 github-dark.css 深色主题 |

**教训**：脚本回归测"能跑通"，人工测试测"好用不好用"——这两个问题脚本测不出来。UI 可发现性（affordance）和对比度是自动化盲区。

---

## Day 10（上下文管理功能实测）✅

### 实测结果：全流程打通（无代码变更，验证日）

| 功能 | 实测 |
|------|------|
| 上下文选择器打开 | ✅ 表/代码双 Tab |
| 表搜索 + 添加 | ✅ "5G套网用户"入标签 |
| 代码搜索 + 添加 | ✅ "5G套餐退订用户数"入标签 |
| 多上下文并存 | ✅ 两个标签同时显示 |
| 上下文注入 AI | ✅ AI 注释直接引用术语业务定义 |

**自动化测试的边界**：测试中"代码 tab 搜索无响应"最终查明是 agent-browser 的 fill 不触发 Vue input 事件、点击命中了隐藏 tab 的按钮——**应用无 bug，是测试工具不兼容**。改用 Vue 兼容的原生事件模拟后一切正常。又一个"先验工具再验应用"的例子。

### 学到的
上下文 = 把表结构/口径 SQL 以 JSON 形式随问题发给 AI，AI 在生成时参考。与 RAG 的区别：RAG 是"系统自动检索"，上下文是"用户手动指定"——手动指定优先级更高、更可控。

---

## Day 10 作业（上下文管理 2 题）

### 题 1：已添加条目置灰（需求未实现）— 讲解题
**代码来源**：`ContextSelector.vue`（无已添加状态）、`ChatView.vue`（handleAddContext 静默去重）
**我的回答**：不知道。
**讲解**：需求原文"条目上的添加按钮变为灰色"。子组件要知道哪些已添加 → props 向下传 selectedIds，条目按 isAdded 显示"已添加"标签+置灰，不再 emit select。组件通信优先级：props 向下、事件向上，状态单一来源。
**已修复** ✅：selectedIds prop + 置灰样式 + 已添加标签

### 题 2：上下文 JSON 直塞 Prompt — 95 分 ⭐
**代码来源**：`ChatView.vue`（context: {items}）、`nl_to_sql.py`（json.dumps 直塞）
**我的回答**：AI 看到原始 JSON；问题=token 浪费（键名/括号噪音）+信息噪音（被迫解析 JSON）+20条膨胀；方案=前端预处理为文本。
**点评**：分析满分。架构 nuance：格式化应放 Python 侧（Prompt 构建是 AI 引擎职责，API 边界保持结构化）。金句："前端不懂 Prompt，Prompt 不懂 API。"
**已修复** ✅：Python 侧 format_context（术语一行/口径带 SQL）

### 主题总结
**分层原则**：数据在边界处保持结构化（可扩展），在消费处格式化（可定制）。前端、API、Prompt 各司其职。

---

## Day 11（多轮对话记忆）✅

### 核心成果：多轮对话记忆打通

**架构决策**：Java 传历史（无状态）而非 LangChain 进程内 Memory。

| 方案 | 状态 | 重启 | 多实例 | 结论 |
|------|------|------|--------|------|
| LangChain ConversationBufferMemory | 进程内 | 丢失 | 不共享 | 1.x 已废弃 |
| **Java 从 MySQL 读历史传入** | 无状态 | 不丢 | 可共享 | ✅ 采用 |

**实现**：
- Java ChatService：从 MySQL 读最近 6 条消息（3 轮），构建 [{role, content}] 传 Python
- Python format_history：格式化为"用户：xxx / AI：xxx"注入 Prompt，每条截断 500 字防膨胀
- Prompt 增加修改语义提示："如果用户问题是对历史回答的修改，请基于最近的 SQL 修改"

**验证（对比实验）**：
```
第一轮: 统计当日5G套网到达数
  → SELECT COUNT(DISTINCT user_id) FROM dwd_5g_user_info WHERE p_day...

第二轮(追问): 加一个地市分组维度
  → 保留原表+原过滤, 添加 city AS city_name + GROUP BY city ✅
  → 防幻觉规则仍生效(假设标注) ✅
```

### 学到的
1. **Memory 的本质**：不是魔法，是"把历史消息塞进下一次请求的 Prompt"——我们选择让 Java（数据拥有者）负责收集历史，比框架内置 Memory 更契合三层架构
2. **历史截断策略**：最多 6 条 + 每条 500 字，防止 token 膨胀（和题 2 的上下文格式化一个道理）
3. 面试可以主动讲"为什么不用 LangChain Memory"——选型有理由比会用框架更值钱

---

## 简历素材积累

### 项目总述（面试 30 秒版）
> 我独立开发了一个智能 SQL 助手（Smart SQL），基于 LangChain + DeepSeek 实现自然语言转 SQL，集成 RAG 检索增强生成，将业务知识库向量化后注入大模型上下文，大幅提升生成 SQL 的业务准确性。前后端采用 Vue 3 + Spring Boot + Python 三层架构。

### 你的个人贡献点（持续更新）
- [x] 负责数据库 5 张表的结构设计（含 ER 关系梳理）
- [x] 负责 DeepSeek API 的申请与配置
- [x] 设计并优化 NL→SQL 的 Prompt 模板（5 条改进意见全部落地：安全约束/防幻觉/冲突处理/注释规范/性能要求）
- [x] 参与 RAG 检索效果测试并见证调优（标题加权、知识注入格式）
- [x] 代码 review 发现 3 个真实 bug 并完成修复（凭据硬编码、会话名误覆盖、SSE JSON 落库）
- [ ] 端到端系统测试（Day 9）
- [ ] 项目 README 撰写（Day 14）

### 技术关键词（面试前复习清单）
- [x] LangChain: Chain / PromptTemplate / RunnableSequence
- [x] RAG: 向量化 / 语义检索 / Top-K 召回 / 文档构建
- [x] 防幻觉: 占位符策略 / 假设标注 / 待确认列表
- [ ] SSE: 服务端推送 / 流式渲染
- [ ] Spring Boot: 三层架构 / MyBatis-Plus
- [ ] Vue 3: Composition API / Pinia

# 踩坑记录（Pitfalls & Solutions）

> 项目开发过程中每天遇到的问题与解决方案记录。
> 每次遇到新问题，先在这里记录，再修复。
> 面试时可讲：这些坑体现了真实项目的工程经验。

---

## Day 1（环境搭建 + 数据库 + Git 初始化）

### 坑 1：MySQL 不在 PATH 中
- **现象**：`mysql --version` 提示命令不存在
- **原因**：MySQL 8.0.46 安装在 `D:\Program Files\MySQL\MySQL Server 8.0\bin\`，但未加入系统 PATH
- **解决**：使用完整路径或临时 `export PATH="$PATH:/d/Program Files/MySQL/MySQL Server 8.0/bin"`
- **教训**：Windows 下 MySQL Installer 默认不配置 PATH，查服务定位安装路径：`sc qc MySQL80` → BINARY_PATH_NAME

### 坑 2：MySQL root 密码丢失
- **现象**：`Access denied for user 'root'@'localhost'`
- **排查路径**：常见密码尝试 → 查 my.ini（无 skip-grant-tables）→ 查 Windows 凭据管理器（无）→ 查 Workbench connections.xml（密码加密存储在 vault，无法明文读取）
- **解决**：用户回忆起当初安装时设置的密码
- **备选方案**（未使用）：`--skip-grant-tables` 免密启动后 `ALTER USER` 重置密码（需要管理员权限）
- **教训**：重要密码应有密码管理器；Workbench 的 connections.xml 只存主机/用户，密码在系统加密 vault 中

### 坑 3：DDL 中文默认值报错
- **现象**：`ERROR 1067: Invalid default value for 'session_name'`（`DEFAULT '新会话'`）
- **原因**：通过管道 `cat schema.sql | mysql` 传输时，中文被 shell 以 GBK 编码破坏
- **解决**：
  1. 默认值改为英文 `'New Session'`（业务层展示中文）
  2. 执行时指定字符集：`mysql --default-character-set=utf8mb4 < schema.sql`
- **教训**：Windows 终端默认 GBK 编码，UTF-8 中文 SQL 文件必须显式指定字符集；建表默认值尽量用 ASCII

### 坑 4：`.env` 被 git 忽略
- **现象**：`git add smart-sql-ai/.env` 提示被 .gitignore 忽略
- **原因**：`.gitignore` 中配置了 `.env` 规则（保护 API Key 不被提交）
- **解决**：这是**正确的行为**。只提交 `.env.example` 模板，真实 Key 仅存在本地
- **教训**：API Key 泄露 = 财产损失。任何含密钥的文件都不该进版本库

### 坑 5：JDK 25 与 Spring Boot 2.7 不兼容
- **现象**：环境检查发现本机 JDK 25（2026 LTS），而原计划 Spring Boot 2.7 只支持 JDK 8-17
- **解决**：升级到 Spring Boot 3.3.5 + `mybatis-plus-spring-boot3-starter`，`<java.version>25</java.version>`
- **教训**：选技术栈前先查环境实际版本；Spring Boot 3.x 需要 spring-boot3 版本的 starter

---

## Day 2（Python AI 引擎跑通 NL→SQL）

### 坑 6：Python 3.14 太新，包版本不兼容
- **现象**：`pydantic==2.10.4` 报 `No matching distribution found`
- **原因**：本机 Python 3.14.3（2026 年最新），部分包的旧版本没有 cp314 的 wheel
- **解决**：requirements.txt 从精确版本 `==` 改为最低版本 `>=`，让 pip 选择支持 3.14 的最新版
- **教训**：新版本 Python 装旧版本包时必踩；用 `>=` 约束 + 安装后 `pip freeze` 固化

### 坑 7：pip 直连 pypi.org 超时
- **现象**：`ReadTimeoutError`，重试 5 次全失败
- **原因**：国内网络直连 PyPI 不稳定
- **解决**：`pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple`
- **教训**：国内开发环境必备镜像：清华源（pip）、hf-mirror（HuggingFace）、npmmirror（npm）

### 坑 8：HuggingFace 模型下载超时
- **现象**：Embedding 模型（470MB）下载到一半被中断，之后连接 `huggingface.co` 持续超时
- **解决**：设置 `HF_ENDPOINT=https://hf-mirror.com`，HuggingFace 支持断点续传
- **关键细节**：`huggingface_hub` 在**首次导入时**缓存 ENDPOINT 值，环境变量必须在 import 之前设置。最终把 `os.environ.setdefault("HF_ENDPOINT", ...)` 放在 `main.py` 最顶部（所有 import 之前）
- **教训**：库的全局状态在 import 时固化——环境变量要尽早设置

### 坑 9：ChromaDB 尝试下载自带的 ONNX 模型
- **现象**：首次查询报 `ReadTimeout`，日志显示在下载 chroma 自带的 ONNX MiniLM 模型
- **原因**：创建 collection 时**没有传 `embedding_function`**，ChromaDB 默认使用自己的 ONNX 模型（国内无法下载）
- **解决**：传入自定义适配器 `embedding_function=LangChainEmbeddingAdapter()`，使用已下载好的 sentence-transformers 模型
- **教训**：第三方库的"默认行为"可能依赖外部资源——初始化时显式指定关键依赖

### 坑 10：ChromaDB 1.5.x 接口变化（连环坑）
- **现象**：三个连续的 AttributeError
  1. `'LangChainEmbeddingAdapter' object has no attribute 'name'`
  2. `'LangChainEmbeddingAdapter' object has no attribute 'embed_query'`
- **原因**：ChromaDB 1.5.x 的 EmbeddingFunction 协议要求：
  - `name()` — 标识函数名
  - `embed_query(input)` — 查询向量化
  - `embed_documents(input)` — 文档向量化
  而旧版只需要 `__call__(input)`
- **解决**：适配器同时实现 4 个方法，兼容新旧版本
- **教训**：升级第三方库大版本前查 breaking changes；适配器模式是隔离版本差异的好手段

### 坑 11：ChromaDB collection 配置冲突
- **现象**：`ValueError: Embedding function conflict: new: paraphrase-... vs persisted: default`
- **原因**：旧 collection 创建时使用了默认 embedding 配置（已持久化），新代码传入不同 embedding 函数导致冲突
- **解决**：删除 `chroma_db/` 目录重建（注意：需先停止服务释放 sqlite 文件锁）
- **教训**：向量库的 embedding 配置是持久化的，**换模型必须重建库或迁移**；Windows 下 sqlite 文件被进程锁定时无法删除

### 坑 12：curl 发送中文 JSON 乱码
- **现象**：`curl -d '{"question":"统计当日..."}'` 报 `There was an error parsing the body`
- **原因**：Windows curl 默认按 GBK 编码处理命令行参数
- **解决**：测试脚本统一用 Python `urllib.request`，`json.dumps(ensure_ascii=False).encode('utf-8')` 显式控制编码
- **教训**：Windows 下中文 HTTP 测试首选 Python 脚本，别用 curl

### 坑 13：DeepSeek API 间歇性连接重置
- **现象**：`httpx.ConnectError: [WinError 10054] 远程主机强制关闭了一个现有的连接`
- **排查**：同代码重试后成功（2.1s 返回），确认是网络抖动而非代码问题
- **解决**：客户端加重试；关键链路不因单次失败报错
- **教训**：网络问题要先用最小复现（裸 SDK 调用）区分"代码问题"和"环境问题"

---

## Day 3（RAG 知识检索全链路）

### 坑 14：ChromaDB 多进程并发写入冲突
- **现象**：同步脚本写入向量库后，运行中的服务器报 `InternalError: Error creating hnsw segment reader: Nothing found on disk`
- **原因**：服务器进程持有 ChromaDB 的内存状态，同步脚本在另一个进程修改了磁盘数据，导致服务器内存中的 HNSW 索引与磁盘不一致
- **解决**：同步脚本运行后**重启 AI 引擎服务器**，让它从磁盘重新加载向量库状态
- **教训**：ChromaDB 的 PersistentClient 不支持"两个进程同时写 + 一个进程读"的零刷新模式；生产环境应通过 HTTP API（`/ai/knowledge/sync`）同步，而非直接操作磁盘文件

### 坑 15：RAG 检索到了知识，但 AI 没用表名
- **现象**：向量检索命中"当日5G套网到达数"口径（含 SQL 和真实表名），但 AI 生成的 SQL 仍用占位符 `<表名待确认>`
- **排查**：打印注入 Prompt 的知识上下文，发现只有 `description` 字段（截断 200 字符），**SQL 逻辑所在的 `content` 字段被丢弃了**
- **原因**：`k.get('description', k.get('content', ''))[:200]` — description 存在时 content 永不使用，且 200 字符截断把 SQL 切掉
- **解决**：按类别分别渲染知识条目：口径（caliber）注入完整文档（含 SQL），术语（term）注入名称+描述
- **教训**：RAG 效果的瓶颈往往不在检索，而在"检索结果如何注入 Prompt"——检索到 ≠ 模型看到了 ≠ 模型用上了。三层都要验证

### 坑 16：长文档稀释标题语义（Embedding 排名问题）
- **现象**：含 SQL 的口径条目"当日5G套网到达数"与查询"统计当日5G套网到达数"的相似度仅 0.42，排名第 4；不含 SQL 的术语反而排第 1
- **原因**：Embedding 模型对整个文档做平均池化，SQL 代码部分（与查询无关的 token）稀释了标题部分的语义权重
- **解决**：构建检索文档时**标题重复一次**（`f"{name}\n{name}"`），相似度从 0.42 提升到 0.53，排名升至第 2
- **教训**：RAG 的文档构建（chunking/加权）直接影响检索质量；标题加权是最简单的检索优化手段之一

---

## 通用经验总结

| 类别 | 经验 |
|------|------|
| 国内网络 | pip→清华源、HuggingFace→hf-mirror、npm→npmmirror |
| Windows 编码 | 终端默认 GBK；涉及中文一律显式 UTF-8；SQL 文件用 `--default-character-set` |
| 版本兼容 | 环境版本（Python 3.14/JDK 25）先查再定依赖版本；大版本升级查 breaking changes |
| 敏感信息 | .env 永不提交；.env.example 提供模板 |
| 排障方法 | 先最小复现定位问题层（环境/代码/网络），再看库文档和 breaking changes |
| 服务管理 | 改代码后必须重启服务；Windows 文件锁（sqlite）需先停服务再删文件 |

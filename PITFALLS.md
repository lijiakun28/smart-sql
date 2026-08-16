# 踩坑记录（Pitfalls & Solutions）

> 项目开发过程中遇到的问题与解决方案记录，每个问题包含现象、原因、解决、教训四部分。

---

## 阶段 1（环境搭建 + 数据库 + Git 初始化）

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

## 阶段 2（Python AI 引擎跑通 NL→SQL）

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

## 阶段 3（RAG 知识检索全链路）

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

## 阶段 4（Java 中间层编译启动 + 会话 API）

### 坑 17：JDK 25 下 Lombok 注解不生效（连环坑）
- **现象**：`mvn compile` 报"找不到符号 getBusinessDesc()/getTermName()..."——Lombok 的 getter 全部缺失
- **排查过程**（升级了三次才解决）：
  1. Spring Boot 3.3.5 自带 Lombok 1.18.34 不支持 JDK 25 → 显式升级 1.18.38，**仍然失败**
  2. 继续升级 1.18.46（Maven Central 最新），**仍然失败**
  3. 检查依赖树确认 1.18.46 已在 classpath → 才意识到是**注解处理根本没运行**
- **根因**：JDK 23+ 默认禁用隐式注解处理（JEP），javac 不再自动运行 classpath 上的注解处理器
- **解决**：maven-compiler-plugin 显式声明 `<annotationProcessorPaths>` 指向 Lombok
- **教训**：JDK 大版本升级有三个坑位：① 库版本兼容 ② 构建工具插件兼容 ③ **语言/工具链行为变更**。第三类最难查，因为报错信息往往误导（"找不到符号"像是依赖问题，实际是注解处理被禁用）

### 坑 18：Spring Boot 3.3.5 的 Maven 插件不认识 JDK 25
- **现象**：编译通过但 `mvn spring-boot:run` 报 `Unsupported class file major version 69`（69 = JDK 25）
- **原因**：spring-boot-maven-plugin 3.3.5 太旧，无法解析 JDK 25 的 class 文件
- **解决**：Spring Boot 整体升级到 3.5.16
- **教训**：编译通过 ≠ 运行通过。构建插件自己也需要支持目标 JDK

### 坑 19：JDBC URL 字符集名错误
- **现象**：所有接口返回 500，日志显示 `Caused by: java.io.UnsupportedEncodingException: utf8mb4`
- **原因**：`characterEncoding=utf8mb4` 是 **MySQL 的字符集名**，而 Connector/J 需要 **Java 的字符集名**（`UTF-8`）
- **解决**：改为 `characterEncoding=UTF-8`（Connector/J 会自动映射到 utf8mb4 连接字符集）
- **教训**：JDBC URL 参数是给"Java 驱动"用的，不是给 MySQL 服务器用的；MySQL 官方文档里 utf8mb4 与 UTF-8 的映射关系要搞清楚

### 坑 20：TaskStop 杀不死 Spring Boot 子进程
- **现象**：停止后台任务后重启报端口 8080 被占用，`netstat` 显示旧 PID 仍 LISTENING
- **原因**：后台 bash 任务被终止，但它 fork 出的 Java 子进程（PID 37456）存活
- **解决**：`netstat -ano | grep 8080` 找到 PID → `taskkill //F //PID <pid>`
- **教训**：Windows 下 mvn spring-boot:run 的进程树不会随父 shell 终止；改端口/重启前先查 `netstat -ano`

### 坑 21：中文查询参数未 URL 编码
- **现象**：`urllib.request` 报 `UnicodeEncodeError: 'ascii' codec can't encode`
- **原因**：HTTP 请求行只能是 ASCII，中文参数必须先百分号编码
- **解决**：`urllib.parse.quote(str(v))` 对每个参数值编码
- **教训**：手写 HTTP 测试客户端时，URL 编码/解码是必修课（浏览器和 axios 会自动处理，urllib 不会）

---

## 阶段 5（知识库 API 联调 + 自动同步）

### 坑 22：全局 logic-delete 配置与业务状态字段冲突
- **现象**：知识库列表 API 返回 0 条，但 MySQL 里明明有数据；新建的知识也查不到
- **原因**：`application.yml` 全局配置了 `logic-delete-field: status`（为会话表设计），MyBatis-Plus 给**所有**表查询自动加 `WHERE status=1`。而知识库表的 status 是业务状态字符串（'new'/'active'/'inactive'），永远不等于 1
- **解决**：移除全局 logic-delete 配置；会话表的逻辑删除由 `SessionService.delete()` 手动 UPDATE status=0 实现
- **教训**：**同一个字段名在不同表中语义不同**时，不能套全局配置。字段语义是表级的，不是应用级的；全局配置只适合所有表统一语义的字段（如 create_time）

### 坑 23：MyBatis-Plus 3.5.9+ 分页插件拆分
- **现象**：`selectPage` 返回 total=0、不分页；补写分页插件时 `PaginationInnerInterceptor` 类找不到
- **原因**：MyBatis-Plus 3.5.9 把分页插件拆到独立模块 `mybatis-plus-jsqlparser`（因 JSqlParser 的 CVE 修复与 License 争议）
- **解决**：`pom.xml` 添加 `mybatis-plus-jsqlparser` 依赖 + 注册 `MybatisPlusInterceptor` Bean
- **教训**：① 分页必须显式注册插件（不注册静默退化，不报错——最阴险的失败模式）② 3.5.9 是 MyBatis-Plus 的重大拆分版本，升级前查 release notes

### 坑 24：Maven Central 连接不稳定
- **现象**：`mvn compile` 报 "Failed to read artifact descriptor"，curl Maven Central 返回 000
- **解决**：`~/.m2/settings.xml` 配置阿里云镜像（mirrorOf=central）
- **教训**：国内开发环境三件套齐全了：pip→清华、HF→hf-mirror、Maven→阿里云。**新环境第一天就该配齐所有镜像**

### 坑 25：@Value 字段注入 vs 构造器执行顺序
- **现象**：问答接口报 `Connection refused: getsockopt: /[::1]:80`——WebClient 请求打到了 localhost 的 80 端口
- **原因**：Spring 的 `@Value` 字段注入发生在**构造器执行之后**。在构造器里读 `@Value` 字段拿到的是 null，`WebClient.builder().baseUrl(null)` 没有 baseUrl，后续相对路径请求解析到默认 localhost:80
- **解决**：改为**构造器参数注入**：`public AiProxyService(ObjectMapper om, @Value("${...}") String url)`
- **教训**：构造器里用注入字段 = 经典陷阱。规则：**初始化逻辑依赖配置时，配置必须走构造器参数注入**。报错指向的端口（80）往往就是线索——"为什么是 80？因为 baseUrl 是 null"

### 坑 26：校验异常被通用异常处理器吞掉
- **现象**：加了 @Pattern 校验后，非法参数返回 HTTP 200 + code 500（消息正确但状态码错误）
- **原因**：GlobalExceptionHandler 的 `Exception.class` 处理器捕获了 ConstraintViolationException，包装成 Result.fail(500)
- **解决**：新增专门的 ConstraintViolationException 处理器，返回 `ResponseEntity.badRequest()`（HTTP 400 + code 400）
- **教训**：通用 Exception 处理器会"吞掉"所有特定异常——每引入一种新异常类型（校验、限流、认证），都要检查是否需要专门的处理器。**响应码语义与异常类型要一一对应**

### 坑 27：SSE 流式验证方法错误（误报 bug）
- **现象**：验证"AI 是否使用新知识"时检查原始 SSE 字符串是否包含表名，结果误判"异步同步失败"
- **原因**：DeepSeek 流式返回按**单字符**分块（"统"、"计"、"当"...每个 chunk 一个 JSON 信封），表名 `dwd_async_def` 在原始流中永远不连续出现
- **解决**：检查**落库后的完整消息内容**（Java 侧已解析拼接纯文本），而不是原始 SSE 流
- **教训**：验证流式接口时，断言对象应该是"最终消费形态"（落库数据），而不是中间形态（分块流）。**先确认验证方法正确，再怀疑代码有 bug**

---

## 阶段 6 补充（真实浏览器驱动测试，agent-browser + Edge）

### 坑 28：SCSS 编译器缺失导致整页不渲染
- **现象**：页面报 `Preprocessor dependency "sass-embedded" not found`，界面空白无按钮
- **原因**：组件用了 `<style lang="scss">` 但 package.json 从未安装 SCSS 编译器（阶段 1 建骨架时漏装，且之前的验证只用 curl 检查了 JS 编译，没检查 CSS 编译路径）
- **解决**：`npm install -D sass-embedded`
- **教训**：**curl 验证页面 ≠ 浏览器验证页面**。Vite 按需编译，curl 一个 URL 只触发该模块编译；浏览器加载整棵树才会暴露全部依赖缺口。项目 skill 应记录"启动前必须装 sass"

### 坑 29：前端与后端 context 类型不匹配（ClassCastException）
- **现象**：浏览器发问后 Java 抛 `ArrayList cannot be cast to Map`（ChatController line 60）
- **原因**：前端 `context: chatStore.contextList`（数组），Java 强转 `(Map<String, Object>)`。之前的 Python 测试脚本都传 `{}`（对象），掩盖了真实前端的请求形状
- **解决**：Java 端改 `Object context` 透传；前端规范化为 `{ items: [...] }`
- **教训**：**测试脚本的请求形状要忠实于真实客户端**——用 Python 验证 Java 接口时，请求体必须模拟浏览器的真实格式，否则"假阳性"通过

### 坑 30：Spring SseEmitter 与前端 SSE 解析器的格式差异
- **现象**：后端正常落库、thinking 也不渲染——流式数据被前端静默丢弃
- **原因**：Spring `SseEmitter.event().data()` 输出 `data:{json}`（冒号后**无空格**）；前端解析检查 `startsWith('data: ')`（**有空格**）。永不匹配，无报错
- **解决**：`line.startsWith('data:')` + `slice(5).trim()` 兼容两种格式
- **教训**：SSE 格式的"冒号后空格"是可选细节，不同实现不同写法（Python 手写 `data: `有空格，Spring 无空格）。**跨语言协议对接处，先看两边实际字节**

### 坑 31：正则 g 标志的 match() 陷阱（修复引入的次生 bug）
- **现象**：SQL 代码块在浏览器中永不渲染，但 thinking 正常
- **原因**：修复提取/删除共用的正则时把提取/删除共用带 `g` 的正则。`str.match(/re/g)` 只返回完整匹配数组、**不含捕获组**，`match[1]` 是 undefined → extractSql 永远失败
- **解决**：提取用无 `g` 正则（取捕获组），删除用带 `g` 正则（replace 全部），两个正则分开
- **教训**：① `match` 带 g 无捕获组是 JS 经典陷阱 ② **修复的修复**——每次修复都可能引入次生 bug，浏览器实测是发现它们的唯一可靠手段

---

## 阶段 8-12（浏览器实测与进阶功能阶段）

### 坑 32：状态变更 API 调用后不刷新列表（阶段 8）
- **现象**：知识库"生效"按钮点击后 PUT 返回 200，但表格状态停留旧值——用户误以为操作失败
- **原因**：模板直接绑定 API 函数 `@click="changeTermStatus(...)"`，调用成功但无人触发列表重载
- **解决**：包装函数 handleTermStatusChange：await API → ElMessage → loadTerms()
- **教训**：**写操作成功 ≠ UI 已同步**。所有变更类操作都要走"调 API → 提示 → 刷新列表"三件套

### 坑 33：UI 可发现性与对比度问题四连（阶段 9，用户浏览器测试发现）
- **现象**：① 重命名/删除操作藏在 hover 才显示的菜单 ② 思考过程展开箭头不可见 ③ 点赞/踩图标纯白与背景融合 ④ SQL 黑字在深色背景上看不清
- **根因**：① 交互入口可发现性差 ② el-collapse 默认箭头样式问题 ③ 图标颜色继承错误 ④ highlight.js 用了浅色主题 github.css，SQL 块却是深色背景——**主题与容器不匹配**
- **解决**：右键菜单 / 自定义箭头按钮 / 带边框文字按钮 / 换 github-dark.css
- **教训**：**脚本回归测"能跑通"，人工测试测"好用不好用"**。可发现性（affordance）与对比度是自动化测试盲区。让真实用户用一天，比写一百个断言值钱

### 坑 34：agent-browser 的 fill 不触发 Vue 事件（阶段 10，测试工具坑）
- **现象**：自动化 fill 输入框后点搜索，Vue 的 v-model 数据没有更新——请求参数为空
- **原因**：agent-browser 的 fill 直接设 DOM value，未触发 Vue 依赖的 input 事件
- **解决**：用原生 setter + dispatchEvent(new Event('input', {bubbles:true}))
- **教训**：**先验工具再验应用**——自动化工具的交互方式与真实用户不同，遇到"疑似应用 bug"先确认是应用还是工具的问题

### 坑 35：巧合性通过比显性失败更危险（阶段 12，本项目最深刻的坑）
- **现象**：多轮对话测试通过（追问后 AI 正确修改上一轮 SQL），但流式端点**根本没把 history 传给链**——链收到 None，Prompt 里历史是"无"
- **真相**：测试通过是 RAG 检索碰巧兜底——追问"加一个地市分组"时 RAG 恰好检索到同一条口径，AI 基于 RAG 知识完成了修改，与历史注入无关
- **发现**：做知识引用功能时顺手检查 invoke 参数才发现
- **教训**：**验证功能时要排除"其他路径碰巧工作"的可能**。隔离性验证：设计一个只有被测功能能答对的用例（如问 RAG 检索不到、只能靠历史回答的问题）

### 坑 36：best-effort 同步的垃圾数据残留（阶段 12）
- **现象**：3 条已删除的知识条目仍留在向量库（baseUrl bug 时代的异步删除任务失败所致）
- **原因**：删除同步是 best-effort（失败仅告警），历史失败无人重试，垃圾永久残留
- **解决**：对账机制——sync_knowledge.py --full 清空向量库后从 MySQL 重建；VectorStoreManager.wipe()
- **教训**：**best-effort 系统必须配对账（reconciliation）**。以权威数据源定期重建派生数据，是分布式缓存的通用兜底模式

### 坑 37：重新回答的重复落库 + 审计约束
- **现象**：重新回答会把同一条用户问题重复落库（前端 addUserMessage + 后端 chatStream 都保存），历史膨胀成重复问答对
- **修复思路的坑**：想"删除旧问答对"——但需求要求日志保留 3 个月，删除违反审计
- **解决**：isRegenerate 标志贯通前端→Java，跳过重复保存用户消息；历史变为一问多答（与 ChatGPT 语义一致）
- **教训**：**修复方案要考虑审计/日志约束**——"删掉重来"经常违反数据保留要求；"追加替代"更安全

### 坑 38：中文输入法 Enter 键误触发（阶段 14 用户反馈）
- **现象**：手动敲字（中文）后按 Enter 发送无反应，但点发送按钮能发
- **原因**：`@keydown.enter.exact` 在中文输入法**组词确认**（选候选词）时也触发——此时 `e.isComposing=true`，组词文本还没提交进 v-model，`handleSend` 读到空字符串静默 return
- **解决**：handleEnterKey 中检查 `e.isComposing`，组词中的 Enter 不触发发送
- **教训**：**中文应用的前端事件处理必须考虑 IME 状态**——Enter/Tab/上下键等都会在组词时触发。`isComposing` 是过滤输入法中间态的标准手段。测试时用真实中文输入法敲一遍，自动化工具模拟不了 IME

### 坑 39：IDEA 直接打开 index.html 白屏（阶段 14 用户反馈）
- **现象**：从 IDEA 打开 index.html，Edge 访问 localhost:63342 空白页面；localhost:5173 内容完整
- **原因**：63342 是 IDEA 内置**静态服务器**，只返回原始 HTML——不运行 Vite 的模块打包/SCSS 编译/API 代理。`<script type="module" src="/src/main.js">` 在静态服务器下无法解析
- **解决**：Vue/Vite 项目必须通过 `npm run dev` 启动的 5173 端口访问，永远不要直接打开 index.html
- **教训**：**SPA 的"页面"是构建产物不是源文件**——index.html 只是入口壳，内容靠 dev server 动态编译。此坑与坑 28（SCSS 缺失）同族：把前端当静态站点理解

### 坑 40：Vue 模板事件对象污染函数参数（用户反馈，修复引入的次生 bug）
- **现象**：输入框打字后点击"发送"按钮无反应；回车发送正常
- **原因**：修复给 `handleSend` 增加了 `questionOverride` 参数，但模板 `@click="handleSend"` 会自动把 **MouseEvent** 作为第一个参数传入——`(questionOverride || inputText.value).trim()` 抛 `TypeError: .trim is not a function`。回车键走 `handleEnterKey`（不传参）所以正常，点击按钮必现
- **解决**：双保险——模板显式 `@click="handleSend()"` 不传事件 + 函数内 `typeof questionOverride === 'string'` 防御判断
- **教训**：① **Vue 模板 `@click="fn"` 会传事件对象**，给函数加参数时必须同步改模板绑定 ② 修复引入的次生 bug（第二次了，见坑 31）——修复后必须回归原功能路径 ③ 排查"点击没反应"用 `window.addEventListener('error'/'unhandledrejection')` 捕获静默异常最快

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

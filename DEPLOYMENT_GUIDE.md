# 智能SQL：从零安装与使用指南

本指南面向首次获取项目的 Windows 用户，说明如何从下载代码开始，完成数据库初始化、模型配置、三项服务启动，以及全部核心功能验证。

## 1. 前置条件

请先安装并确认下列工具可在终端中使用：

| 工具 | 建议版本 | 用途 |
| --- | --- | --- |
| Git | 2.40+ | 下载项目 |
| MySQL | 8.0+ | 保存会话和知识库数据 |
| JDK | 25 | 运行 Spring Boot 服务 |
| Maven | 3.9+ | 构建和启动 Java 服务 |
| Python | 3.11–3.14 | 运行 AI 服务 |
| Node.js | 20+ | 运行 Vue 前端 |

还需要准备一个可用的 DeepSeek API Key。该 Key 只保存在本机 `.env` 文件中，绝不能提交到 Git 仓库。

在 PowerShell 依次检查：

```powershell
git --version
mysql --version
java -version
mvn -version
python --version
node --version
npm --version
```

若 `npm` 无法识别，请重新安装 Node.js 的 LTS 版本，并在安装程序中勾选“Add to PATH”，然后重新打开终端。

## 2. 下载项目

```powershell
cd D:\
git clone https://github.com/lijiakun28/smart-sql.git 智能SQL
cd D:\智能SQL
```

后续命令均以 `D:\智能SQL` 为项目根目录。若使用其他路径，请将命令中的路径替换为实际位置。

## 3. 初始化 MySQL 数据库

1. 确认 MySQL 服务正在运行。
2. 打开 PowerShell，进入 MySQL 客户端：

```powershell
mysql -u root -p --default-character-set=utf8mb4
```

输入 MySQL `root` 用户密码后，在出现 `mysql>` 提示符时执行：

```sql
SOURCE D:/智能SQL/smart-sql-server/src/main/resources/db/schema.sql;
```

该命令会创建 `smart_sql` 数据库以及所需表结构。使用正斜杠可以避免 Windows 路径转义问题。

可用以下命令确认：

```powershell
mysql -u root -p -e "USE smart_sql; SHOW TABLES;"
```

预期能看到会话、消息、业务术语和统计口径相关的数据表。

## 4. 配置 AI 服务

### 4.1 创建本地配置文件

```powershell
cd D:\智能SQL\smart-sql-ai
Copy-Item .env.example .env
notepad .env
```

在 `.env` 中至少填写下列三项：

```dotenv
LLM_API_KEY=你的_DeepSeek_API_Key
MYSQL_PASSWORD=你的_MySQL_root_密码
INTERNAL_API_TOKEN=随机生成的内部调用令牌
```

`INTERNAL_API_TOKEN` 用于 Java 中间层调用 AI 引擎时的鉴权（Java 侧读同名环境变量，两侧必须完全一致）。生成方式：

```powershell
python -c "import secrets; print(secrets.token_urlsafe(32))"
```

其余默认项通常无需调整。`.env` 已被 Git 忽略，不应移动或重命名为会被提交的配置文件。

### 4.2 安装 Python 依赖

```powershell
cd D:\智能SQL\smart-sql-ai
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
```

首次使用会下载向量模型 `paraphrase-multilingual-MiniLM-L12-v2`，体积约 470 MB，耗时取决于网络状况。

### 4.3 同步初始知识库

保持虚拟环境已激活后执行：

```powershell
python scripts\sync_knowledge.py --full
```

该步骤从 MySQL 读取业务术语和统计口径，将它们写入本地 `chroma_db` 向量库。终端应显示同步完成的条目数量。

## 5. 启动 Java 服务

Java 服务需要通过当前终端的环境变量读取两项配置：MySQL 密码，以及与 AI 引擎约定的内部调用令牌。

```powershell
cd D:\智能SQL\smart-sql-server
$env:MYSQL_PASSWORD = '你的_MySQL_root_密码'
$env:INTERNAL_API_TOKEN = '与 smart-sql-ai\.env 中完全相同的令牌'
mvn spring-boot:run
```

启动成功后，观察终端中 Spring Boot 已在 8080 端口启动的日志。

> 每次新开 Java 终端都要重新设置这两个变量。不要将真实密码写入 `application.yml`。
>
> ⚠️ `INTERNAL_API_TOKEN` 未设置、或与 AI 引擎侧不一致时，**问答功能会整体不可用** —— AI 引擎对未携带正确令牌的 `/ai/*` 请求一律拒绝，这是刻意的 fail-closed 设计（安全配置缺失必须立刻可见，不能悄悄退化成无防护）。

## 6. 启动 AI 服务

在第二个 PowerShell 窗口中执行：

```powershell
cd D:\智能SQL\smart-sql-ai
.\.venv\Scripts\Activate.ps1
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

访问 http://localhost:8000/docs 可打开 FastAPI 自动生成的接口说明页面。

## 7. 启动前端

在第三个 PowerShell 窗口中执行：

```powershell
cd D:\智能SQL\smart-sql-frontend
npm install
npm run dev
```

终端会显示 Vite 提供的地址。默认打开：

```text
http://localhost:5173
```

不要直接用 IDEA 或浏览器打开 `smart-sql-frontend/index.html`。那会使用静态服务器（例如 `localhost:63342`），不会启用 Vite 的模块转换和 `/api` 代理，因此页面可能是空白或功能异常。

## 8. 推荐启动顺序

每次使用项目时按下面顺序启动，且保持三个终端窗口不要关闭：

1. MySQL 服务。
2. Python AI 服务（8000）。
3. Java 服务（8080）。
4. Vue 前端（5173）。

项目根目录的 `start-all.bat` 可帮助同时打开三个服务窗口。它会自动从 `smart-sql-ai\.env` 读取 `MYSQL_PASSWORD` 与 `INTERNAL_API_TOKEN` 并注入 Java 进程，**脚本自身不含任何密码**（该文件含本机绝对路径，已被 `.gitignore` 排除，不入库）。

## 9. 核心功能验证

打开 http://localhost:5173 后，按顺序验证：

### 9.1 生成 SQL

1. 新建一个会话。
2. 输入“统计当日 5G 套网到达数”。
3. 点击“发送”。
4. 等待流式输出结束。

预期结果：回答中包含只读 SQL，并能看到与 `dwd_5g_user_info` 相关的查询，而不应出现 `INSERT`、`UPDATE` 或 `DELETE`。

### 9.2 多轮追问

在同一会话输入“按省份分组”。预期生成的 SQL 会基于上一轮问题补充分组逻辑，而不是忽略此前语境。

### 9.3 会话管理

在左侧会话列表中：

- 右键一个会话，验证可以重命名或删除。
- 切换不同会话，验证消息列表随会话变化。
- 在回答区域使用“重新生成”，验证不会额外写入重复的用户问题。

### 9.4 知识库管理

进入知识库页面：

1. 新增一条业务术语或统计口径，填写完整名称和内容。
2. 保存后启用该条目。
3. 回到聊天页面，在上下文选择器中选中它，向模型提问。
4. 验证回答能使用该条目提供的表、字段或口径信息。

知识库条目修改后，后端会异步同步向量库；也可在 AI 服务目录执行 `python scripts\sync_knowledge.py --full` 进行完整重建。

## 10. 常见问题

### `localhost:5173` 拒绝连接

前端服务没有启动或已退出。进入 `smart-sql-frontend` 后运行：

```powershell
npm run dev
```

若系统中 `npm` 无法识别，而项目的 `node_modules` 已存在，可使用 Node.js 的绝对路径启动 Vite：

```powershell
& 'D:\node.exe' .\node_modules\vite\bin\vite.js --host 127.0.0.1
```

建议仍然修复 Node.js 的 PATH 配置，以便正常使用 `npm`。

### 页面能打开，但发送后没有回答

依次检查：

1. AI 服务的终端是否仍在运行，http://localhost:8000/docs 是否可访问。
2. Java 服务是否已启动并监听 8080。
3. `.env` 中 `LLM_API_KEY` 是否有效。
4. Java 终端是否已设置正确的 `MYSQL_PASSWORD`。
5. **Java 终端与 `.env` 中的 `INTERNAL_API_TOKEN` 是否完全一致**（见下方专项排查）。

### 问答整体不可用 / AI 引擎返回 401 或 503

这是内部令牌未配置或两端不一致的典型症状。AI 引擎对所有 `/ai/*` 业务接口强制校验请求头 `X-Internal-Token`：

- **401 未授权** —— 令牌不匹配：逐字符核对 `smart-sql-ai\.env` 里的 `INTERNAL_API_TOKEN` 与 Java 终端里的 `$env:INTERNAL_API_TOKEN`（注意首尾空格、不要带引号）。
- **503 服务未配置内部令牌** —— AI 引擎侧完全没读到该变量：确认 `smart-sql-ai\.env` 里存在这一行且非空。

用下面的命令可绕过 Java 直接验证 AI 引擎侧的鉴权状态（`/ai/health` 不需要令牌）：

```powershell
curl.exe -i http://localhost:8000/ai/health
```

若该请求返回 200，说明服务本身正常，问题在令牌上。

### 知识库同步失败或模型不知道新增口径

确认 MySQL 服务可访问，检查 `.env` 中 `MYSQL_PASSWORD`，然后重新执行：

```powershell
cd D:\智能SQL\smart-sql-ai
.\.venv\Scripts\Activate.ps1
python scripts\sync_knowledge.py --full
```

### Vite 报 `sass-embedded not found`

在前端目录执行：

```powershell
npm install
npm install -D sass-embedded
```

### 端口已被占用

查看端口占用进程：

```powershell
netstat -ano | Select-String ':5173|:8080|:8000'
```

关闭对应的旧服务窗口后重新启动，或根据需要更改端口配置。

## 11. 停止服务

在三个运行服务的终端中分别按 `Ctrl + C`。停止后，5173、8080、8000 地址将无法访问，属于正常现象。

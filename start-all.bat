@echo off
chcp 65001 >nul
echo ============================================
echo   智能SQL - 一键启动三个服务
echo   每个服务在独立窗口中运行
echo ============================================
echo.

:: 1. Python AI 引擎 (8000)
start "智能SQL-AI引擎" cmd /k "cd /d D:\智能SQL\smart-sql-ai && .venv\Scripts\activate && python -m uvicorn app.main:app --host 0.0.0.0 --port 8000"
echo [1/3] AI引擎已启动 (端口8000, 窗口标题: 智能SQL-AI引擎)

:: 等待AI引擎先起来
timeout /t 5 /nobreak >nul

:: 2. Java 中间层 (8080)
:: 密码通过环境变量注入（不写入代码仓库）
start "智能SQL-Java服务" cmd /k "cd /d D:\智能SQL\smart-sql-server && set MYSQL_PASSWORD=YOUR_MYSQL_PASSWORD && mvn spring-boot:run"
echo [2/3] Java服务已启动 (端口8080, 窗口标题: 智能SQL-Java服务)
echo     注意: 请先把脚本中的 YOUR_MYSQL_PASSWORD 替换为实际密码

:: 3. Vue 前端 (5173)
start "智能SQL-前端" cmd /k "cd /d D:\智能SQL\smart-sql-frontend && npm run dev"
echo [3/3] 前端已启动 (端口5173, 窗口标题: 智能SQL-前端)

echo.
echo ============================================
echo   启动完成! 等待约30秒后访问:
echo   http://localhost:5173
echo.
echo   注意:
echo   - 三个黑色窗口保持打开, 关闭即停止对应服务
echo   - 停止服务: 直接关闭对应窗口
echo ============================================
pause

@echo off
chcp 65001 >nul
setlocal

rem ============================================================
rem Brix Campus Market 后端启动（本地开发态，非部署产物）
rem 1) 若根目录存在 .env.bat 则先加载（可覆盖 DB_USERNAME 等）
rem 2) PATH 中没有 mvn 时，自动到 %USERPROFILE%\.m2\wrapper\dists 找
rem ============================================================

cd /d "%~dp0backend"

if exist "%~dp0.env.bat" call "%~dp0.env.bat"

rem 沙箱 / 部分终端会注入 SERVER_PORT，这里显式固定为 8080（Vite 代理指向 8080）
if "%SERVER_PORT%"=="" set "SERVER_PORT=8080"

set "MAVEN_CMD=mvn"
where mvn >nul 2>nul || (
  rem 兼容 wrapper 任意版本：递归搜索 dists 下所有 mvn.cmd，取最后一个
  for /f "delims=" %%f in ('dir /b /s "%USERPROFILE%\.m2\wrapper\dists\mvn.cmd" 2^>nul') do set "MAVEN_CMD=%%f"
)

echo 使用 Maven: %MAVEN_CMD%
echo 后端端口: %SERVER_PORT%  数据库账号: %DB_USERNAME%/%DB_PASSWORD%
echo.

call "%MAVEN_CMD%" -q spring-boot:run

echo.
echo 后端已退出。
pause

@echo off
setlocal

rem ============================================================
rem Brix Campus Market DB initializer (local dev only)
rem Steps: create db -> create app user -> import schema -> import seed data -> verify
rem Root password stays in this process only, never written to any file.
rem NOTE: this file is pure ASCII on purpose. Batch files with UTF-8 Chinese
rem       text can get line-split randomly on some codepages (cmd parsing bug).
rem ============================================================

rem Auto-detect MySQL install dir (8.x / 9.x, highest version wins)
set "MYSQL="
for /d %%D in ("C:\Program Files\MySQL\MySQL Server *") do (
  if exist "%%~D\bin\mysql.exe" set "MYSQL=%%~D\bin\mysql.exe"
)

if not defined MYSQL (
  echo [ERROR] mysql.exe not found under C:\Program Files\MySQL\
  echo Install MySQL first, or edit this script.
  pause
  exit /b 1
)
echo Using MySQL: %MYSQL%

rem Guard: the two SQL files live NEXT TO this script (in the db folder)
if not exist "%~dp0schema.sql" (
  echo [ERROR] schema.sql not found next to this script: %~dp0
  pause
  exit /b 1
)
if not exist "%~dp0data.sql" (
  echo [ERROR] data.sql not found next to this script: %~dp0
  pause
  exit /b 1
)

echo ============================================================
echo  Brix Campus Market DB init
echo  target db : brix_campus ^(utf8mb4^)    app user: brix / brix123
echo ============================================================
set /p ROOT_PWD=Enter MySQL root password: 

echo.
echo [1/5] create database and app user ...
"%MYSQL%" -u root -p%ROOT_PWD% --default-character-set=utf8mb4 -e "CREATE DATABASE IF NOT EXISTS brix_campus DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER IF NOT EXISTS 'brix'@'localhost' IDENTIFIED BY 'brix123'; GRANT ALL PRIVILEGES ON brix_campus.* TO 'brix'@'localhost'; FLUSH PRIVILEGES;"
if errorlevel 1 goto :fail

echo [2/5] import schema ...
"%MYSQL%" -u root -p%ROOT_PWD% --default-character-set=utf8mb4 brix_campus < "%~dp0schema.sql"
if errorlevel 1 goto :fail

echo [3/5] import seed data ...
"%MYSQL%" -u root -p%ROOT_PWD% --default-character-set=utf8mb4 brix_campus < "%~dp0data.sql"
if errorlevel 1 goto :fail

echo [4/5] verify row counts ...
"%MYSQL%" -u root -p%ROOT_PWD% --default-character-set=utf8mb4 brix_campus -e "SELECT (SELECT COUNT(*) FROM t_user) AS users, (SELECT COUNT(*) FROM t_category) AS categories, (SELECT COUNT(*) FROM t_item) AS items, (SELECT COUNT(*) FROM t_order) AS orders, (SELECT COUNT(*) FROM t_review) AS reviews;"
if errorlevel 1 goto :fail

echo [5/5] done. App user brix / brix123 is ready.
echo Backend reads DB_USERNAME / DB_PASSWORD env vars, defaults brix / brix123.
echo.
echo Demo accounts:
echo   student01  / user123456     regular user (main demo account)
echo   seller01~05 / user123456    5 sellers
echo   reviewer01 / reviewer123456 reviewer
echo   operator01 / operator123456 operator
echo.
echo Admin entry: http://localhost:5173/#/admin/login
pause
exit /b 0

:fail
echo.
echo [FAILED] previous step failed. Check root password / MySQL service / paths above.
pause
exit /b 1

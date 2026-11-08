@echo off
setlocal EnableExtensions
cd /d "%~dp0"

where node >nul 2>&1
if errorlevel 1 (
  echo [ERROR] node not found in PATH. Install Node.js 20.19+ first.
  exit /b 1
)

node scripts\app.mjs stop
exit /b %errorlevel%

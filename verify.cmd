@echo off
setlocal
cd /d "%~dp0"
node scripts\verify.mjs %*
exit /b %ERRORLEVEL%

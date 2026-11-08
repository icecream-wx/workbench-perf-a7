@echo off
setlocal EnableExtensions
cd /d "%~dp0"

where mvn >nul 2>&1
if errorlevel 1 (
  echo [ERROR] mvn not found in PATH.
  exit /b 1
)
where npm >nul 2>&1
if errorlevel 1 (
  echo [ERROR] npm not found in PATH. Install Node.js 20.19+ first.
  exit /b 1
)

echo [INFO] backend: mvn -q -DskipTests package
pushd backend
call mvn -q -DskipTests package
if errorlevel 1 (
  echo [ERROR] backend build failed
  popd
  exit /b 1
)
popd

echo [INFO] frontend dependencies
pushd frontend
if not exist node_modules (
  if exist package-lock.json (
    echo [INFO] npm ci
    call npm ci
  ) else (
    echo [INFO] npm install
    call npm install
  )
  if errorlevel 1 (
    echo [ERROR] frontend dependency install failed
    popd
    exit /b 1
  )
)
echo [INFO] frontend: npm run build
call npm run build
if errorlevel 1 (
  echo [ERROR] frontend build failed
  popd
  exit /b 1
)
popd

echo [INFO] artifacts:
for %%F in (backend\target\*.jar) do echo   %%F
if exist frontend\dist\index.html echo   frontend\dist\index.html
exit /b 0

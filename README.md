# perf-a7

Vue 3 + Spring Boot 应用骨架。

## 前置

- Java 17
- Maven 3.9+
- Node.js >= 20.19、npm 10+
- 可选：Docker（MySQL 8.x）

## 快速开始

Windows：

```bat
build.cmd
start.cmd local
```

Linux/macOS：

```bash
./build.sh
./start.sh local
```

打开 `http://127.0.0.1:5173/`，后端健康检查为 `http://127.0.0.1:8080/actuator/health`。停止：

```bat
stop.cmd
```

```bash
./stop.sh
```

## 验证

构建并启动后，统一运行：

```bat
verify.cmd
```

```bash
./verify.sh
```

它会只 start/stop 一次，执行 `tools/api-smoke.mjs`（health + OpenAPI + 前端首页）和 `tools/ui-smoke.mjs`（Chrome/Edge + CDP 检查 SPA 挂载与 console）。业务专项检查放到 `tools/`，复用 `tools/cdp.mjs`，不要每次新建临时 Playwright 工程。

- 迭代期后端：`mvn -q -DskipTests compile` 或 `mvn -Dtest=<TestClass> test`
- 提交前：`mvn -q test`、`npm run typecheck && npm run build` 各一次
- 只有新增依赖时才修改 `package.json`/lock；不要重复 `npm install`

## 构建

- 后端：`mvn -q -DskipTests package`
- 前端：首次 `npm ci`（有 lock）或 `npm install`，然后 `npm run build`
- 根目录 `build.cmd`／`build.sh` 会依次完成，并打印 jar 与 `dist/` 路径。

## 运行模式

| 模式 | 后端 profile | 前端 | 外部依赖 |
| --- | --- | --- | --- |
| `local`（默认） | `local` | `npm run dev` | 无，H2 文件库 |
| `dev` | `dev` | `npm run dev` | Nacos |
| `prod` | `prod,mysql` | `npm run preview` | Nacos + MySQL 8.x |

```bat
start.cmd local
start.cmd dev
start.cmd prod
```

```bash
./start.sh local
./start.sh dev
./start.sh prod
```

`start` 只启动已经构建好的前端依赖/产物；首次请先执行 `build`。重复执行 `start` 是幂等的；端口被其他进程占用会明确失败，不会杀无关进程。

## Nacos

```bat
set NACOS_ADDR=127.0.0.1:8848
set NACOS_NAMESPACE=
set NACOS_GROUP=DEFAULT_GROUP
set NACOS_USERNAME=
set NACOS_PASSWORD=
```

```bash
export NACOS_ADDR=127.0.0.1:8848
export NACOS_NAMESPACE=
export NACOS_GROUP=DEFAULT_GROUP
```

`dev`/`prod` 缺少 `NACOS_ADDR` 时启动脚本会明确报错。

## MySQL 8.x

```bat
set DB_HOST=127.0.0.1
set DB_PORT=3306
set DB_NAME=perf-a7
set DB_USER=root
set DB_PASSWORD=
```

可选本地 MySQL：

```bash
docker compose --profile mysql up -d
```

## 日志与 PID

- 日志：`logs/backend.log`、`logs/backend.err.log`、`logs/frontend.log`、`logs/frontend.err.log`
- PID／状态：`run/backend.pid`、`run/frontend.pid`、`run/processes.json`
- 停止脚本会等待 15 秒再强制结束本次启动的进程；不会按进程名批量杀。

## 常见问题

- **端口被占用**：先 `stop` 或修改 `BACKEND_PORT`／`FRONTEND_PORT`；不要把其他服务杀掉。
- **前端启动失败**：确认已执行 `build`（或 `npm install`），`frontend/node_modules` 存在。
- **后端启动失败**：查看 `logs/backend.log`／`backend.err.log`；`local` 不应依赖 Nacos。
- **MySQL 连接失败**：确认目标为 MySQL 8.x、数据库已创建、`DB_*` 正确；`application-mysql.yml` 已带 `allowPublicKeyRetrieval=true`。

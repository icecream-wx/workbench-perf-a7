# perf-a7 应用工作空间

应用名：perf-a7
包名：`com.example.perfa7`
后端端口：`8080`
前端端口：`5173`

本目录是一个完整应用：后端在 `backend/`，前端在 `frontend/`，启停与构建脚本在根目录。业务代码由 Agent 在骨架上增量编写；不要改骨架目录、版本和脚本约定。

## 工程事实

| 项目 | 目录 | 栈与版本 | 构建 | 测试 | 运行 |
| --- | --- | --- | --- | --- | --- |
| 后端 | `backend/` | Spring Boot 3.5.16、Spring Cloud 2025.0.3、Spring Cloud Alibaba 2025.0.0.0、Java 17 | `mvn -q -DskipTests package` | `mvn test` | `mvn spring-boot:run -Dspring-boot.run.profiles=local` |
| 前端 | `frontend/` | Vue 3.5.45、Vite 7.3.7、TypeScript 5.9.3、Element Plus 2.14.5 | `npm run build` | `npm run typecheck` | `npm run dev` |

## 启停与构建

- 构建：`build.cmd`（Windows）／`build.sh`（Linux/macOS）
- 本地启动（H2 文件库，无 Nacos）：`start.cmd local`／`start.sh local`
- 开发环境（Nacos）：`start.cmd dev`／`start.sh dev`
- 生产环境（Nacos + MySQL 8.x）：`start.cmd prod`／`start.sh prod`
- 停止：`stop.cmd`／`stop.sh`
- 默认访问：前端 `http://127.0.0.1:5173/`，后端健康 `http://127.0.0.1:8080/actuator/health`

`start` 默认使用 `local`；重复执行已启动的实例会提示 already running 并返回 0。PID 在 `run/`，日志在 `logs/`。

## Profiles

- `local`：H2 文件库，Nacos 关闭，无外部服务依赖。
- `dev`：Nacos config/discovery 开启；需要 `NACOS_ADDR`。
- `prod`：Nacos + MySQL 8.x；需要 `NACOS_ADDR`、`DB_HOST` 等。
- `mysql`：数据源覆盖，可与 `local`／`dev`／`prod` 组合，例如 `--spring.profiles.active=local,mysql`。

## Nacos 与数据库环境变量

- Nacos：`NACOS_ADDR`（如 `127.0.0.1:8848`）、`NACOS_NAMESPACE`、`NACOS_GROUP`、`NACOS_USERNAME`、`NACOS_PASSWORD`。
- Nacos discovery scope：`compile`。
- MySQL 8.x：`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD`。
- 密码/令牌不要写入仓库；本地可用环境变量或未跟踪的 `.env.local`。

## 目录约定

- `backend/src/main/java/com.example.perfa7/controller|service|repository|domain|exception`：业务分层。
- `backend/src/main/resources/db/migration`：Flyway 迁移，业务表从 `V2__*.sql` 开始。
- `frontend/src/api`：所有业务接口统一走这里，不散落 `fetch`。
- `frontend/src/views`、`components`、`stores`、`composables`：页面、组件、状态、组合式逻辑。
- 不修改 `pom.xml`／`vite.config.ts`／启停脚本的核心结构；业务需要时先说明。

## 验证与命令策略（性能）

- 统一验证入口：`node scripts/verify.mjs`（只 start/stop 一次，跑 `tools/api-smoke.mjs` + `tools/ui-smoke.mjs`）。
- **完整 `scripts/verify.mjs` 只允许在提交前运行一次**；迭代期只跑 `mvn -q -DskipTests compile`、定向测试或前端 typecheck。失败修复后最多再跑一次完整验证；不要每次改动/推送后重复完整验证。
- 迭代期用 `mvn -q -DskipTests compile` 或定向 `mvn -Dtest=<TestClass> test`；**提交前全量 `mvn -q test` 只跑一次**。
- 前端 `npm run typecheck && npm run build` 提交前一次；**禁止 `npm install`**，只有新增依赖才改 lock；`node_modules` 已存在时直接使用。
- 不要读取 `node_modules` 或第三方依赖源码来猜 API；优先看类型/编译错误与官方文档。
- 远端同步：开始时 fetch/merge 一次、提交前一次；不要每次检查都 fetch/merge。
- 页面/接口专项检查放到 `tools/`，复用 `tools/cdp.mjs` 和 `scripts/verify.mjs`；**不要每次新建临时 Playwright 工程**。
- 标准 CRUD 优先复用 `samples/crud/`：`node scripts/use-crud-sample.mjs --entity <Entity> --plural <plural> --code <codeField> --date <dateField> --label <中文名> --table <table>`，再按批次规格改字段/校验/页面；不要从零重写样板。
- 需求整理只读 `PROJECT_BRIEF.md`；不要用 glob/read 扫描整个仓库。
- **交付证据 `.opencode/delivery/<批次ID>.md` 必须与代码一起提交并推送**；最后一次 `git status` 只允许 `.opencode/batch/` 未跟踪。提交/推送后只做 `git status`/`git ls-remote` 校验，不再跑完整验证。

## 批次

- 当前批次规则文件：`.opencode/batch/current.md`。
- 详细规格：`.opencode/batch/<批次ID>/specs/<需求点ID>.md`。
- 交付证据：`.opencode/delivery/<批次ID>.md`。
- 批次与模板是否入 Git 由工作台策略决定。

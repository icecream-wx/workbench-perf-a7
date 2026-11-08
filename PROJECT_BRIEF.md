# PROJECT_BRIEF：perf-a7

> 需求整理 Agent 只需要读这一份文件；不要用 glob/read 扫描整个仓库。开发 Agent 以批次规则和本文件为准，按需读取具体业务文件。

## 应用

- app-id：`perf-a7`
- 应用名：perf-a7
- Java 包名：`com.example.perfa7`
- 后端端口：8080
- 前端端口：5173
- 本地数据库：H2 文件库（Nacos 关闭）

## 技术栈

- 后端：Spring Boot 3.5.16、Spring Cloud 2025.0.3、Spring Cloud Alibaba 2025.0.0.0、Java 17
- 前端：Vue 3.5.45、Vite 7.3.7、TypeScript 5.9.3、Element Plus 2.14.5
- Node：>=20.19

## 目录

```text
backend/
  src/main/java/com.example.perfa7/        业务包（controller/service/repository/domain/exception/config）
  src/main/resources/
    application*.yml               local/dev/prod/mysql profiles
    db/migration/                  Flyway，业务表从 V2__*.sql 开始
  src/test/java/com.example.perfa7/       测试
frontend/
  src/api/                         HTTP 接口封装
  src/views/                       页面
  src/components/                  组件
  src/router/                      路由
scripts/                           app.mjs 启停/状态
tools/                             api-smoke.mjs / ui-smoke.mjs / cdp.mjs
```

## 命令

- 构建：`build.cmd`（Windows）／`build.sh`
- 启动：`start.cmd local`／`start.sh local`
- 停止：`stop.cmd`／`stop.sh`
- 验证：`node scripts/verify.mjs`（只启动/停止一次，跑 API + UI smoke）；**提交前只跑一次完整验证**，迭代期用定向测试/typecheck。
- 后端迭代：`cd backend && mvn -q -DskipTests compile`
- 后端定向测试：`cd backend && mvn -Dtest=<TestClass> test`
- 后端提交前全量：`cd backend && mvn -q test`（只跑一次）
- 前端：`cd frontend && npm run typecheck && npm run build`（提交前一次）
- 禁止 `npm install`；`node_modules` 已存在时直接使用；不要读 `node_modules` 源码。
- 标准 CRUD：先运行 `node scripts/use-crud-sample.mjs ...` 复用 `samples/crud`，再改业务字段/校验/页面；保留基础测试并补充业务用例。
- 健康检查：`http://127.0.0.1:8080/actuator/health`
- OpenAPI：`http://127.0.0.1:8080/v3/api-docs`
- 前端首页：`http://127.0.0.1:5173/`

## 约定

- 不要修改 `pom.xml`／`vite.config.ts`／启停脚本核心结构；业务需要时先说明。
- 不要重复 `npm install`；需要新增依赖时才改 `package.json` 并更新 lock。
- 不要新建临时 Playwright 工程；页面检查放到 `tools/`，复用 `tools/cdp.mjs`。
- 批次、模板是否入 Git 由工作台策略决定；交付证据写 `.opencode/delivery/<批次ID>.md`。

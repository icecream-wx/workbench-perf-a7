# CRUD 参考模块

这是一份**通用 CRUD 参考**（Member 实体），不是业务答案。用于减少样板结构：

- 后端：domain/repository/service/controller/Request/Response/PageResponse、Flyway 迁移、基础测试；
- 前端：api 封装、列表页（搜索/分页/新增/编辑/校验）；
- `tools/crud-api.mjs`、`tools/crud-ui.mjs`：接口与页面烟测。

复制到目标实体并改名：

```bat
node scripts/use-crud-sample.mjs --entity Employee --plural employees ^
  --code employeeNo --date hireDate --label 员工 ^
  --code-label 工号 --date-label 入职日期 --table employee
```

```bash
node scripts/use-crud-sample.mjs --entity Employee --plural employees \
  --code employeeNo --date hireDate --label 员工 \
  --code-label 工号 --date-label 入职日期 --table employee
```

之后必须：

1. 按批次规格改字段、校验、错误语义、页面交互；
2. 保留/补充测试（总数不少于基线 11 个；参考模块自带 15 个基础用例）；
3. 执行一次 `node scripts/verify.mjs`；
4. 提交代码 + `.opencode/delivery/<批次ID>.md` 并推送。

不要直接保留示例字段/示例文案；不要把 `samples/` 当作业务代码运行。

# MuYunDatabase 路线图

## 范围边界

1. 聚焦单表高频能力：DDL、CRUD、条件查询、分页、事务一致性。
2. 复杂查询和多表联查优先走 `@MuYunRepository` 注解 SQL，必要时使用原生 SQL。
3. 不提供关系映射 ORM（`1:N/N:N`、级联、延迟加载）。

## 当前稳定能力

1. `core/jdbi/starter` 分层能力与自动装配。
2. `@MuYunRepository + EntityDao` 统一 DAO 入口（含 CRUD / query / pageQuery / count / upsert / ensureTable）。
3. `Criteria` 动态条件（含 `EXISTS/NOT_EXISTS/IN_SUBQUERY/NOT_IN_SUBQUERY`）。
4. 方言级原子 upsert（MySQL/PostgreSQL）。
5. `MigrationOptions`（`APPLY`、`DRY_RUN`、`DRY_RUN_STRICT`）。
6. Spring 声明式事务：`EntityDao` 方法与注解 SQL 方法同边界回滚。
7. 表结构拉齐策略：全局 `repository-schema-mode` + 仓库级 `alignTable` 覆盖。

## 近期优先级

### P0

1. 双库回归矩阵：同一组用例覆盖 MySQL/PostgreSQL，确保 `insert/update/delete/find/query/pageQuery/count/upsert` 行为一致。
2. 事务回滚矩阵：`@Transactional` 下 `EntityDao` 方法与注解 SQL 方法统一回滚。
3. 拉齐策略矩阵：覆盖全局 `repository-schema-mode` 与仓库 `alignTable` 组合优先级。
4. 契约门禁：`README/QUICKSTART/API_CONTRACT/MIGRATION` 与代码行为保持一致。

### P1

目标：开发体验。验收标准是“新成员 30 分钟可完成一个合格 DAO + Service”。

1. 注解 SQL 报错可读：参数名、方法签名、SQL 片段、建议修复动作清晰可见。
2. 示例可照抄：提供 3 组标准样板（开箱 CRUD、事务回滚、特例 SQL）。
3. 文档一致性：示例代码与仓库 sample 保持可编译一致。

### P2

目标：线上可维护性。验收标准是“异常可定位、性能可度量”。

1. 可观测性：慢 SQL、失败分类、关键上下文日志可直接用于排障。
2. 性能基线：形成 CRUD + 分页的长期基线（吞吐、延迟、并发语义）。

## 发布门禁

1. `core/jdbi/starter/test` 全部通过。
2. 双库回归（MySQL/PostgreSQL）核心场景通过。
3. 文档同步更新：`README.md`、`docs/API_CONTRACT.md`、`docs/QUICKSTART.md`、`docs/REFACTOR_GUIDE.md`。

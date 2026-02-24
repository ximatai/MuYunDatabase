# 测试架构

## 目标

统一测试分层与隔离规则，确保以下三类能力可持续回归：

1. 双库一致性（MySQL/PostgreSQL）。
2. 事务一致性（`@Transactional` 回滚边界）。
3. 表结构拉齐策略优先级（全局 + 仓库覆盖）。

---

## 1. 分层测试基类

1. `MuYunDatabaseBaseTest`
- 共享 Jdbi/Testcontainers/bootstrap 工具能力。
- 提供 `protected` 级复用方法，不直接暴露 `@Test`。

2. `MuYunDatabaseCoreOpsTestBase`
- 覆盖核心数据库操作与基础行为。

3. `MuYunDatabaseDdlTestBase`
- 覆盖表结构变更与元数据行为。

4. `MuYunDatabaseOrmTestBase`
- 覆盖实体映射、criteria、分页、迁移相关行为。

5. `MuYunDatabaseTxAndPluginTestBase`
- 覆盖事务执行器、插件、SQL 组合场景。

6. Starter 仓库层测试（`muyun-database-spring-boot-starter`）
- 覆盖 `@MuYunRepository + EntityDao` 主路径（扫描、代理分发、事务、拉齐策略）。

具体数据库测试类（`MuYunDatabaseMySQLTest`、`MuYunDatabasePostgresTest`）只负责数据库特定配置与实体绑定。

---

## 2. P0 回归矩阵映射

| P0 目标 | 最低覆盖要求 |
| --- | --- |
| 双库一致性 | `MuYunDatabaseMySQLTest` 与 `MuYunDatabasePostgresTest` 对同一组核心用例同时通过（CRUD/query/pageQuery/count/upsert） |
| 事务回滚矩阵 | starter 层覆盖 `EntityDao` 方法 + 注解 SQL 方法同事务回滚 |
| 拉齐策略优先级 | starter 层覆盖 `repository-schema-mode` 与 `alignTable=DEFAULT/ENABLED/DISABLED` 组合 |
| 契约门禁 | 与 `README/QUICKSTART/API_CONTRACT/MIGRATION` 示例口径一致 |

---

## 3. 隔离规则

1. 禁止依赖其他测试遗留的可变 schema 副作用。
2. 若测试会修改表结构（字段类型/长度/索引），必须使用专用表名，或在测试内重建需要的结构。
3. SQL 相关集成测试必须使用独立表，禁止复用共享核心表。
4. 测试数据必须与当前表契约兼容。
5. 共享基线表必须在 `@BeforeEach` 重建。
6. 继承相关 DDL 测试必须使用独立表名，禁止跨测试复用。
7. 自定义 schema 的 DDL 测试应使用用例专属 schema 名。
8. SQL 注解测试不应硬编码共享表名，应使用占位参数传入测试作用域表名。

---

## 4. 自动化守卫

源码级守卫测试：

- `../muyun-database-test/src/test/java/net/ximatai/muyun/database/TestingArchitectureGuardTest.java`

守卫目标：

1. 阻止重新引入历史共享 schema/table 硬编码。
2. 阻止 SQL 注解测试硬编码共享测试表。
3. 维持测试命名与隔离约束。

此外，集成测试包含原子 upsert 并发回归场景：

1. 常规竞争（`testAtomicUpsertConcurrent`）。
2. 高竞争（`testAtomicUpsertConcurrentHighContention`）。

---

## 5. 可选稳定性检查

```bash
./gradlew :muyun-database-test:test \
  --tests net.ximatai.muyun.database.MuYunDatabaseMySQLTest \
  --tests net.ximatai.muyun.database.MuYunDatabasePostgresTest \
  -Djunit.jupiter.testmethod.order.default='org.junit.jupiter.api.MethodOrderer$Random' \
  -Djunit.jupiter.execution.order.random.seed=12345
```

# 事务内结构发现与迁移

适用版本：3.26.23-SNAPSHOT（开发版，尚未发布正式版本）。

## 问题与修复

3.26.22 的 `JdbiMetaDataLoader.getDBInfo()` 使用实例共享缓存。事务 A 创建尚未提交的表后，连接 B 可以把不包含该表的快照写入缓存，导致 A 下次迁移重复建表。即使 A 在协调前 reset，B 也可能在 reset 与规划读取之间重新填充缓存。

Jdbi 实现现在为每次 `SchemaManager.ensureTable` 创建独立的元数据加载器，并将整个调用及表、列、索引、主键、唯一约束、外键的延迟读取绑定到本次 Handle。规划器只使用该快照。该快照不会读取或写入共享缓存，也不会被其他连接的 reset 或重新发现替换。执行成功或失败后仍保留原有共享缓存失效行为。dry-run、strict 校验、索引及约束治理继续使用原有规划逻辑。

## Spring 接入

现有 `MuYunSchemaManager`、`SchemaManager`、`TableBuilder` 和 ORM 的 ensureTable 入口自动使用新路径，调用方不必复制底层查询，也不必为迁移正确性额外调用 reset。

```java
@Transactional
public void publishModel(TableWrapper table) {
    schemaManager.ensureTable(table);
    // 本事务内即使再次协调，也能看到自身尚未提交的 DDL。
    schemaManager.ensureTable(table);
}
```

`schemaManager` 可为注入的 Spring `MuYunSchemaManager`。须使用同一 DataSource 及其事务管理器，并保持 `muyun.database.transaction-aware-data-source=true`（默认值）。关闭事务感知数据源、跨线程执行或使用另一个数据源不会自动加入原事务。

## 显式 Jdbi 事务

```java
transactionRunner.inTransactionVoid(tx -> {
    SchemaManager schema = new SchemaManager(tx.getOperations());
    schema.ensureTable(table);
    schema.ensureTable(table);
});
```

使用 `TxContext.getOperations()`，发现和执行均绑定 `TxContext` 的 Handle。事务提交、回滚和 Handle 的生命周期由调用方的事务入口管理，发现接口本身不提交、不回滚，也不关闭显式传入的 Handle。

## 独立发现 API

```java
boolean hasTable = operations.withFreshDBInfo(info -> {
    var schema = info.getSchema("public");
    return schema != null && schema.containsTable("business_model");
});

// 已持有 Handle 时，也可通过 Jdbi 加载器显式绑定。
List<String> columnNames = loader.withFreshDBInfo(handle, info ->
    List.copyOf(info.getSchema("public")
        .getTable("business_model").getColumnMap().keySet()));
```

`DBInfo`、`DBSchema`、`DBTable` 及其延迟元数据只在回调中使用，不得返回或保存到回调外；返回布尔值、字符串或自行复制的描述值即可。没有外层事务时，Jdbi 为本次调用保持一个 Handle，但不会自动创建事务。该 API 遵循数据库自身的隔离级别，不承诺多次查询构成比该级别更强的原子数据库快照。

第三方 `IMetaDataLoader` 的默认 `withFreshDBInfo` 仅保持旧行为，未提供隔离和新鲜度保证；第三方实现需覆盖该方法。原有 `getDBInfo()` 仍是共享缓存 API，不能代替事务结构发现；直接调用它仍可能缓存事务内可见结构，外部 DDL、提交或回滚后的缓存更新仍由其原有失效契约管理。

本次修复不负责将独立事务中的并发 DDL 串行化。如果两个事务在各自真实视图中都未发现一张表，仍应使用项目现有迁移协调器或调用方的锁策略协调建表。

## 回归验证

`TransactionalSchemaDiscoveryIntegrationTest` 使用真实 PostgreSQL 17，覆盖：

- Spring 事务内重复协调；连接 B 在两次协调之间刷新共享缓存。
- A reset 后、规划发现前，等待 B 在线程内完成刷新后再继续 A；无需压力测试或 sleep 猜测交错。
- 主动回滚后物理表不存在，新事务可重新创建，提交后其他连接可发现。
- 延迟读取列、索引、主键、唯一约束和外键时仍能看到未提交 DDL，且共享快照不被写入事务结构。
- 显式 `JdbiTransactionRunner` Handle、配置默认 schema、dry-run、strict 拒绝和迁移执行失败后的恢复。

```bash
./gradlew :muyun-database-spring-boot-starter:test \
  --tests '*TransactionalSchemaDiscoveryIntegrationTest'
./gradlew test -Pmuyun.postgres.it.required=true
```

需要 Docker。临时将 `SchemaManager` 的发现入口恢复为 `operations.getDBInfo()`，两个交错用例均会报 PostgreSQL `relation already exists`，用于确认用例确实能捕获原缺陷。

2026-10-18 本地验证：完整 `test -Pmuyun.postgres.it.required=true` 通过，445 项测试，0 失败、0 错误、0 跳过，包含上述 7 项回归、PostgreSQL／MySQL 和 Quarkus PostgreSQL JVM 测试。未执行 Quarkus native 测试。

## 本地接入版本

六个发布模块的 `3.26.23-SNAPSHOT` 已通过 `./gradlew publishToMavenLocal` 安装到本机 Maven 本地仓库。MuYunSpring 可在本机启用 `mavenLocal()` 后使用：

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("net.ximatai.muyun.database:muyun-database-spring-boot-starter:3.26.23-SNAPSHOT")
}
```

显式声明的其他 MuYunDatabase 模块也应统一到该版本。该快照尚未上传远端仓库；其他机器需从本分支构建并安装。正式版本号建议使用 3.26.23，不覆盖已发布的 3.26.22；正式发布及远端获取方式待发布流程完成后提供。

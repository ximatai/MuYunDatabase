# 索引迁移契约升级（未发布）

本次收口 PR #12 的审查发现，并修正它暴露的存量设计问题。项目尚未上线，本次直接调整契约，不保留语义混杂的旧入口或隐式归属猜测。本文说明破坏性变化及调用方修改方式；不授权或自动执行数据库重建、数据删除、去重或回填。

## 1. 破坏性变化

| 原行为／入口 | 新契约 | 调用方动作 |
| --- | --- | --- |
| `dropIndex(Index)` 同时携带名称和无实际用途的定义字段 | 删除该重载，使用 `dropIndexByName(String)` | 按数据库真实物理名称删除，不再构造占位 `Index` |
| `getDroppedIndexes()` 返回 `List<Index>` | 返回 `List<IndexDrop>`；名称与列集合选择器互斥，列集合快照不可变 | 直接操作模型的代码改用 `IndexDrop.byName/byColumns` |
| 删除和目标声明混用，可能重复 DROP 或只删不建 | 同名 add/drop、按列删除与目标同列集合、解析后指向同一物理对象的冲突均在 DDL 前拒绝 | 同名替换只声明新定义；不同名替换按旧名称删除 |
| 自动认领 PostgreSQL 的历史截断名称 | 仅匹配当前稳定生成的名称或显式名称 | 要保留旧索引就显式 `.named(真实旧名称)`；要替换就显式按旧名称删除 |
| 普通／唯一自动名称互相作为替换候选 | 两个名称代表独立索引，不再隐式删除另一个 | 要解除旧唯一性保障，必须明确删除对应旧名称 |
| 已有表新增唯一索引被标为安全增量，strict 可以执行 | `DATA_VALIDATION_REQUIRED`，strict 和 dry-run-strict 拒绝 | 先普通 dry-run、校验／治理数据，再显式 execute |
| PostgreSQL 部分索引比较忽略名称或列名大小写 | 所有索引路径遵循 PG 精确比较、MySQL 忽略大小写 | PG 使用真实大小写；MySQL 大小写不同仍能匹配 |
| 同名索引可重复声明 | 按方言身份规则拒绝重复名称 | 合并为唯一的目标定义，不按声明先后选择覆盖 |
| MySQL 的目标唯一约束与删除选择器分属不同检查路径，可能删除仍声明的唯一性 | `UniqueConstraint` 也作为同一物理索引目标保护；同名 `Index`／`UniqueConstraint` 重复声明拒绝 | 保留约束时移除冲突删除；删除旧约束时不再将其声明为目标 |
| 部分 `addIndex` 重载在组装时精确查列，其他重载延迟校验 | 所有重载统一组装模型，在规划阶段按方言校验列存在性 | 允许先声明索引再声明列；未知列的异常统一为规划阶段 `INVALID_MAPPING` |
| 先删列再删／替换其索引，可能对已被数据库隐式清理的索引再次 DROP | 索引删除／替换先于列删除，目标索引键列引用待删除列时拒绝 | 只声明最终保留列上的索引，列与索引操作不互相矛盾 |

已有数据库不会因此被自动清理。移除旧名称猜测后，ensure 可能新增当前名称的索引并保留旧索引；这不是重建数据库，也不会自动解除旧唯一性限制。先检查 dry-run 和 metadata，决定保留还是显式替换。

接入模块应修改调用并重新编译，不要混用依赖旧重载或旧删除模型的编译产物。

## 2. 修改示例

### 精确删除

```java
// 旧：table.dropIndex(new Index("code", true).named("legacy_unique_code"));
table.dropIndexByName("legacy_unique_code");
```

`dropIndex(List.of("code"))` 仍表示按无序列集合删除，但只允许命中一个普通或唯一索引。唯一性、方向和 predicate 不参与选择；多个匹配时拒绝，未匹配时 no-op。空白名称直接拒绝，不回退为按列删除。多个删除选择器命中同一个对象，只生成一次 DROP。

### 同名替换：只声明最终定义

```java
TableWrapper table = TableWrapper.withName("contract")
    .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
    .addIndex(new Index("code", false).named("lookup_code"));
```

如果数据库中的 `lookup_code` 是唯一索引，规划器生成一次 DROP、一次 CREATE；如果已经是相同普通索引，则 no-op。不要同时 `dropIndexByName("lookup_code")`，也不要留下重复的旧目标定义。

### 不同名替换：显式指定旧物理名称

```java
TableWrapper table = TableWrapper.withName("contract")
    .addColumn(Column.of("code").setType(ColumnType.VARCHAR).setLength(64))
    .dropIndexByName("legacy_unique_code")
    .addIndex(new Index("code", false).named("lookup_code"));
```

该模型可重复 ensure：旧对象不存在时删除是 no-op，新对象存在且定义相同时也 no-op。不要改成按列删除，否则它在下一次调用时可能选择新对象；平台会提前拒绝这类矛盾声明。

### 已有表新增唯一索引

```java
table.addIndex(new Index("code", true).named("unique_code"));
MigrationResult preview = manager.ensureTable(table, MigrationOptions.dryRun());
// CREATE_INDEX 风险为 DATA_VALIDATION_REQUIRED；由调用方审核计划并校验／治理数据。
// strict / dryRunStrict 会拒绝，不会执行前面的其他 DDL。
manager.ensureTable(table, MigrationOptions.execute());
```

`execute()` 不是“数据已通过校验”的承诺。存在重复值时数据库仍会拒绝；平台不自动去重或改业务数据。新建空表的唯一索引仍是安全增量，已有相同唯一索引仍是 strict 可接受的 no-op。

## 3. 风险与边界

- PostgreSQL 自动名称仍按 63 UTF-8 字节稳定缩短；显式超长名称仍拒绝。索引列顺序影响定义，按列删除则忽略顺序。
- PostgreSQL constraint-owned backing index 不属于独立索引删除范围；MySQL 的 `UNIQUE` 约束与唯一索引共用物理对象，删除其索引会解除对应唯一性。MySQL 的目标唯一约束已纳入删除冲突保护；未再声明的旧约束仍可显式按索引名删除。两库的约束删除不完全同义。
- 规划失败和 strict 拒绝发生在执行前；普通 execute 的 DDL 原子性仍由外部事务和数据库决定。尤其 MySQL DDL 可能自动提交，同名替换失败不能保证恢复旧索引。
- 本次不引入迁移历史系统，不重写整个 Planner，不扩展表达式／INCLUDE 索引模型，也不顺带重做所有主外键标识符规则。
- predicate 是 SQL 文本，本次不解析其中的任意列依赖；混合删列时由调用方检查 predicate，数据库仍负责校验其合法性。
- 后续治理：制定跨方言的约束／索引所有权契约，并在有真实需求时扩展索引模型；不通过重新添加名称猜测解决。

## 4. 验收范围

核心测试覆盖：PG 精确名称／列名、MySQL 大小写无关匹配、历史名称不认领、普通与唯一自动名称共存、删除歧义与去重、缺失／已有对象的冲突拒绝、同名替换、重复目标名称，以及新表／已有表唯一索引风险和 strict 行为。

各 `addIndex` 重载同时验证模型组装顺序、MySQL 列名大小写及未知列的执行前拒绝。

MySQL 额外覆盖唯一约束与索引两个 metadata 视图：命名／按列／解析后冲突在所有模式下拒绝，新表／已有表均保护；不同名称的约束替换及未再声明的旧约束删除仍可执行。

真实双库测试覆盖：计划与执行一致、重复 ensure 幂等、显式历史名称替换、PG 大小写碰撞不解除唯一性、同名替换只执行一次，以及重复数据下新增唯一索引失败且数据不被修改。

2026-10-04 本地验证：`./gradlew test -Pmuyun.postgres.it.required=true` 通过，438 项测试，0 失败、0 错误、0 跳过，包含双库与 Quarkus PostgreSQL JVM 矩阵。本次未运行 native 发布门禁，也未执行发布。

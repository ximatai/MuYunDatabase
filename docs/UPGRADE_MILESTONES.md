# MuYunDatabase 升级里程碑

本文档记录基于架构审视讨论确认的升级方向与跟踪计划。

## 设计原则

1. 工具化、去业务化：框架只做工具该做的事——让数据访问正确、高效、类型安全、可观测。
2. 业务语义（逻辑删除、审计填充、乐观锁、生命周期回调）由业务层自行实现，框架不替业务做设计决策。
3. 能力开放、语义中立：框架可提供无业务假设的扩展点（如拦截器），但不内置业务行为。
4. 聚焦单表高频能力，不提供关系映射 ORM。

## 不做的事（明确边界）

| 不做 | 原因 |
|------|------|
| 逻辑删除 | 业务语义，框架不应替业务定义"删除" |
| 审计字段自动填充 | 用户身份获取是业务上下文，框架无法假设来源 |
| 乐观锁 | 冲突策略是业务决策，框架不应替业务选择 |
| 生命周期回调 | 业务逻辑的侧门，与 Service 层职责重叠 |
| 关系映射 ORM | 已在 API 契约中明确排除，坚持单表聚焦 |

## 与已有文档的关系

- 本文档的条目与 [`docs/MIGRATION_FEEDBACK_BACKLOG.md`](MIGRATION_FEEDBACK_BACKLOG.md) 存在重叠时，以本文档为准；backlog 中对应条目在里程碑完成后标记为"已完成"并回引本文档。
- 本文档的条目实施后，需同步更新 [`docs/API_CONTRACT.md`](API_CONTRACT.md)、[`docs/ROADMAP.md`](ROADMAP.md) 中的相关声明。

---

## 里程碑 M1：修正确性缺陷

目标：消除当前实现中影响结果正确性或数据一致性的已知问题。

### M1.1 `count()` 走专用计数 SQL

- 问题：仓库代理层 `count(criteria)` 通过 `pageQuery(...).getTotal()` 间接实现，执行了不必要的 `SELECT *` + 数据映射。
- 目标：代理分发直接走 `SELECT COUNT(*)` 路径，避免额外分页查询链路。
- 影响面：`muyun-database-core`（SimpleEntityManager 新增 count）、`muyun-database-spring-boot-starter`（代理分发逻辑）。
- 验收：
  1. `count` 不触发 `SELECT *`，不产生实体映射开销。
  2. MySQL / PostgreSQL 返回一致。
  3. 对外方法签名与语义保持兼容。
- 关联：backlog #1

### M1.2 `existsById` 走高效存在性判定

- 问题：仓库代理层 `existsById` 通过 `findById != null` 推导存在性，执行了不必要的实体映射。
- 目标：新增专用存在性 SQL 路径（如 `SELECT 1 ... LIMIT 1`），不依赖实体映射。
- 影响面：`muyun-database-core`（SimpleEntityManager 新增 exists）、`muyun-database-spring-boot-starter`（代理分发逻辑）。
- 验收：
  1. `existsById` 不触发实体映射。
  2. 返回语义与当前一致。
  3. MySQL / PostgreSQL 一致。
- 关联：backlog #2

### M1.3 `upsert` 默认走方言级原子操作

- 问题：`IDatabaseOperations.upsertItem` 默认实现为 SELECT + INSERT/UPDATE，存在竞态窗口，并发下可能重复插入。
- 目标：默认使用方言级原子 upsert；仅在方言不支持时降级为非原子模式，并显式警告。
- 影响面：`muyun-database-core`（默认实现逻辑）、`muyun-database-jdbi`（确认方言覆盖）、`muyun-database-spring-boot-starter`（代理分发逻辑）。
- 验收：
  1. 默认路径为原子 upsert，无竞态窗口。
  2. 降级路径在日志中输出 WARN 级别告警。
  3. MySQL / PostgreSQL 行为一致。
  4. 双库并发 upsert 回归测试通过。

### M1.4 新增 `ColumnType` 解决 SET 逗号限制

- 问题：`Set<String>` 字段当前使用 CSV 语义存入 `text` 列（`ColumnType.SET`），禁止元素包含逗号，这是让业务迁就框架。
- 目标：新增 `ColumnType`（如 `JSON_SET`）使用 JSON 数组存储，消除逗号限制。保留原有 `SET`（CSV 语义）不动，老项目零影响，新项目选用新类型。
- 影响面：`muyun-database-core`（新增 ColumnType 枚举值及读写逻辑）、`muyun-database-jdbi`（数据转换逻辑）。
- 验收：
  1. 新类型下 `Set<String>` 元素可包含逗号，不再拒绝写入。
  2. 原有 `SET`（CSV）行为完全不变，老项目零影响。
  3. MySQL / PostgreSQL 行为一致。
  4. `API_CONTRACT.md` 新增新类型说明，原有 SET 条款不变。

---

## 里程碑 M2：接口与架构治理

目标：让核心接口回归"契约"本质，降低实现者负担，提升类型安全性。

### M2.1 `IDatabaseOperations` 按职责拆分

- 问题：当前接口 30+ 方法，CRUD / SQL 执行 / 批量操作 / upsert / DDL 全部塞在一个接口中，违反接口隔离原则。
- 目标：按职责拆分为小接口（读 / 写 / 批量 / DDL / 原始 SQL），原有接口继承聚合，现有使用方代码零改动。
- 影响面：`muyun-database-core`（接口拆分）、`muyun-database-jdbi`（实现类组织）。
- 验收：
  1. 新实现者可只实现所需子接口。
  2. 现有 `IDatabaseOperations` 使用方代码零改动。
  3. 双库回归测试通过。

### M2.2 接口 default 方法清理

- 问题：`IDatabaseOperations` 中大量 default 方法让接口变成"半实现半接口"，新实现者难以区分哪些必须实现。
- 目标：接口只保留少量必须实现的抽象方法，其余 default 实现移除并提取为工具类，接口回归"契约"本质。**破坏性变更**：直接实现 `IDatabaseOperations` 的自定义类需改用工具类复用逻辑。
- 影响面：`muyun-database-core`（新增工具类、移除 default 方法）、`muyun-database-jdbi`（实现类复用工具类）。
- 验收：
  1. 接口中抽象方法不超过 6 个。
  2. 现有功能行为不变。
  3. 新实现者只需关注少量抽象方法。

### M2.3 Map 风格 API 标记废弃

- 问题：`insertItem(String, String, Map)` 等 Map 入参 API 是类型不安全的，字段名拼错、类型传错只有运行时才知道。
- 目标：Map 风格 API 标记 `@Deprecated`，javadoc 指向 `EntityDao`。不移动包、不删除方法，现有调用方编译不报错（仅 warning）。公开文档和示例统一使用 `EntityDao<T, ID>` 路径。
- 影响面：`muyun-database-core`（API 标记 @Deprecated）、`muyun-database-spring-boot-starter`（文档和示例统一使用 EntityDao）。
- 验收：
  1. 公开文档和示例中不再出现 Map 风格 API。
  2. 现有调用方编译不报错（仅 warning）。
  3. `API_CONTRACT.md` 明确 Map API 为废弃路径。

### M2.4 元数据缓存策略优化

- 问题：`resolveTable` 的"缺表 → 全量刷新缓存 → 重试"模式，在表数量多时性能开销大，且存在并发场景下缓存雪崩风险。
- 目标：改为单表粒度懒加载 + 过期策略，缺表查询不触发全量重载，并发场景下无缓存雪崩。
- 影响面：`muyun-database-core`（缓存结构重构）、`muyun-database-jdbi`（元数据加载逻辑）。
- 验收：
  1. 缺表查询不触发全量元数据重载。
  2. 缓存过期后自动刷新，无需手动 `resetDBInfo`。
  3. 并发场景下无缓存雪崩。
  4. 双库回归测试通过。

---

## 里程碑 M3：工具能力补齐

目标：补齐工具层面的能力短板，不涉及业务语义。

### M3.1 Criteria 类型安全增强

- 问题：`Criteria.eq("name", "foo")` 使用字符串字段名，重构时 IDE 无法追踪，拼写错误运行时才暴露。
- 目标：增加 Lambda 属性引用方式（如 `criteria.eq(UserEntity::getName, "foo")`），编译期即可发现字段名错误。保留字符串方式作为兼容路径，两种方式可混用。
- 影响面：`muyun-database-core`（Criteria 新增 Lambda 重载、实体元信息缓存）。
- 验收：
  1. Lambda 方式编译期可检查字段名。
  2. 字符串方式继续可用，行为不变。
  3. 两种方式可混用。
- 前置：M2.4（元数据缓存结构需先稳定）

### M3.2 批量更新与批量删除

- 问题：当前只有 `batchInsert`，缺少 `batchUpdate` 和 `batchDelete`，数据同步 / ETL 场景是刚需。
- 目标：新增 `batchUpdate` / `batchDelete` 能力，在 `@Transactional` 下与单条操作共享事务边界。
- 影响面：`muyun-database-core`（接口与实现）、`muyun-database-jdbi`（批量执行）、`muyun-database-spring-boot-starter`（可选暴露）。
- 验收：
  1. 批量操作在 MySQL / PostgreSQL 行为一致。
  2. 批量操作在 `@Transactional` 下与单条操作共享事务边界。
  3. 性能优于逐条执行。

### M3.3 可观测性内置

- 问题：当前框架几乎没有内置的慢 SQL 日志、操作耗时统计、异常分类，生产环境排障困难。
- 目标：
  1. 内置 SQL 执行耗时日志，可配置阈值。
  2. 操作失败时输出完整上下文（表名、SQL、参数、异常链）。
  3. 提供指标接入点（可选依赖，不引入硬依赖）。
- 影响面：`muyun-database-jdbi`（执行拦截）、`muyun-database-spring-boot-starter`（配置属性 + 可选装配）。
- 验收：
  1. 慢 SQL 超阈值时输出 WARN 日志，含 SQL、参数、耗时。
  2. 操作失败日志含表名、SQL、绑定参数、异常链。
  3. 指标接入点可选启用，不引入硬依赖。

### M3.4 代理方式优化

- 问题：JDK 动态代理每次方法调用经过 `InvocationHandler`，有反射开销；堆栈中全是 Proxy 调用，排障时难以定位。
- 目标：减少反射开销，提升异常堆栈可读性（至少包含 DAO 接口名和方法名）。现有 DAO 接口零改动。
- 影响面：`muyun-database-spring-boot-starter`（代理工厂与调用处理器）。
- 验收：
  1. 方法分派性能不低于当前实现。
  2. 异常堆栈可读性提升。
  3. 现有 DAO 接口零改动。

---

## 里程碑间依赖关系

```
M1（正确性）→ M2（架构治理）→ M3（能力补齐）
     │                              ↑
     └──── M2.4 元数据缓存 ──────────┘
           （M3.1 Criteria 类型安全依赖缓存结构稳定）
```

- M1 是前置条件，必须先完成，否则在错误基础上做架构重构没有意义。
- M2 和 M3 之间大部分无强依赖，可并行推进，但 M3.1（Criteria 类型安全）依赖 M2.4（元数据缓存）的稳定结构。
- 每个 Milestone 完成后需通过发布门禁。

## 发布门禁

每个里程碑合入前需满足：

1. `core / jdbi / starter / test` 全部通过。
2. 双库回归（MySQL / PostgreSQL）核心场景通过。
3. 文档同步更新：`README.md`、`docs/API_CONTRACT.md`、`docs/QUICKSTART.md`、`docs/REFACTOR_GUIDE.md`。
4. 无新增 `@Deprecated` 的默认启用路径（废弃 API 必须有替代方案）。

## 变更记录

1. 初始版本：基于架构审视讨论，确认 M1 / M2 / M3 三个里程碑及明确边界。
2. 补充 M1.2 `existsById` 高效存在性判定（对齐 backlog #2）；新增与已有文档的关系说明；精简实现细节，聚焦目标与验收。
3. 兼容性决策落地：M1.4 改为新增 ColumnType 不改旧 SET；M2.2 明确为破坏性变更（直接移除 default）；M2.3 改为仅 @Deprecated 不移包。

# 版本变更

本文档按版本记录面向使用者的功能、行为、兼容性和迁移说明。

## 未发布

### 新增

- 暂无。

### 变更

- 暂无。

### 修复

- 暂无。

### 迁移说明

- 暂无。

## 3.26.13

发布日期：2026-06-29

### 新增

- 支持集合字段 Criteria 查询。静态实体 ORM 路径以及 `RuntimeTableGateway + TableMeta` 路径下，`SET` / `JSON_SET` / `ARRAY` 字段可使用 `contains`、`containsAny`、`containsAll`、`isEmpty`、`isNotEmpty`，并提供对应 `or*` 方法。
- 新增运行态表模型元数据 `TableMeta` / `FieldMeta` / `RuntimeFieldMeta`，让 runtime-defined records 可以显式提供字段名、列名、`ColumnType`、`elementColumnType`、字段 Java 类型和集合元素 Java 类型。
- 新增 `ColumnType.ARRAY`，用于 PostgreSQL 原生数组列。实体字段可声明为 `List<T>` / `Set<T>` / Java 数组，并通过 `@Column(type = ColumnType.ARRAY, elementType = ...)` 指定元素类型；当字段泛型可识别时，可自动推断元素类型。
- 集合字段读写统一走字段级 codec，`SET` / `JSON_SET` / `ARRAY` 的集合元素可复用自定义 `DatabaseValueConverter`，例如枚举 code 映射可以同时作用于字段值和查询参数。
- 新增 tag 触发的 Maven Central 发布 workflow。推送 `v<project.version>` tag 后，CI 会校验版本、运行测试并执行发布任务。

### 变更

- `String[]` / `int[]` 的默认类型映射由旧数组枚举收口到 `ColumnType.ARRAY`。
- `ColumnType.VARCHAR_ARRAY` 和 `ColumnType.INT_ARRAY` 标记为遗留入口；新代码建议统一使用 `ColumnType.ARRAY + elementType`。
- PostgreSQL 数组列建表、迁移比对、写入绑定和 Criteria 查询改为使用原生数组语义，不再把 CSV 字符串当作 ARRAY 输入。
- 静态实体和运行态表会在元数据解析阶段更早校验重复列、无效字段元数据和不支持的 `ARRAY` 元素类型，配置错误会提前以 `INVALID_MAPPING` 暴露。

### 修复

- 抽取 Criteria 方言表达式，补齐 PostgreSQL 数组 `contains` / `containsAny` / `containsAll` / 空集合判断的 SQL 编译与数据库回归验证。
- 改进 PostgreSQL array 类型比对，兼容数据库返回的 `_varchar`、`int4[]`、`timestamp without time zone[]` 等类型表示，减少重复迁移误判。

### 迁移说明

- `ColumnType.ARRAY` 第一阶段只支持 PostgreSQL。MySQL 不做 JSON 降级；在 MySQL 上建表、迁移或读写 ARRAY 字段会失败或由底层数据库拒绝。
- ARRAY 写入只接受 `Collection` 或 Java 数组，不再接受 CSV 字符串。历史上把 `"a,b,c"` 当数组写入的代码需要改成 `List.of("a", "b", "c")` 或数组。
- 集合 Criteria 查询需要字段元数据上下文。静态实体 ORM 和 `RuntimeTableGateway + TableMeta` 会自动执行集合字段 codec；旧的单向 `CriteriaColumnResolver`、`raw` 条件和 `SqlSubQuery` 不会自动执行集合字段 codec。
- `containsAny(field, List.of())` 固定为 false 条件；`containsAll(field, List.of())` 固定为 true 条件。调用方如果把空集合视为“不加条件”，需要在业务代码中提前跳过该条件。

## 3.26.11

发布日期：2026-06-25

### 新增

- 新增 `DatabaseValueConverter` 扩展点，用于统一处理实体值、查询值与数据库值之间的转换。默认实现支持枚举、基础数值类型、布尔、`BigDecimal` / `BigInteger`、`Date`、`LocalDate`、`LocalDateTime`、`Instant` 等常见转换。
- Spring Boot starter 与 Quarkus extension 会自动发现并使用应用声明的 `DatabaseValueConverter` bean；没有自定义 bean 时使用默认转换器。
- 新增 `ColumnType.LONGTEXT`。MySQL 下映射为 `LONGTEXT`，PostgreSQL 下映射为 `TEXT`，适合大体积 JSON、导入导出快照和超长描述字段。
- `EntityDao`、`CrudRepository`、`SimpleEntityManager` 和 `RuntimeTableGateway` 新增不分页列表查询入口：`list(Criteria, Sort...)` / `list(Class<T>, Criteria, Sort...)` / `listColumns(...)`。
- `Criteria` 新增 `eqNullable` 和 `orEqNullable`，当值为 `null` 时自动编译为 `IS NULL` 条件。

### 变更

- `list(Criteria, PageRequest, Sort...)` 保留为分页兼容别名，语义等价于 `query(Criteria, PageRequest, Sort...)`；真正的不分页查询使用 `list(Criteria, Sort...)`。
- README 和契约文档收口到更完整的接入说明，明确普通 Java、Spring Boot、Quarkus、迁移存量 DAO/MyBatis-Plus 时的推荐入口。

### 修复

- EntityMapper 的值转换逻辑从分散实现收口到 `DatabaseValueConverter`，减少枚举、时间和数值类型在读写路径上的不一致。
- RuntimeTableGateway 补齐不分页列表查询能力，使运行时 Map 表访问与静态实体 DAO 的查询入口更一致。

### 迁移说明

- 自定义 `DatabaseValueConverter` 必须同时保证 `toDatabaseValue` 和 `fromDatabaseValue` 语义一致，尤其是枚举 code、集合元素和查询参数绑定场景。
- 自定义 `EntityDao` / `SimpleEntityManager` 实现如果没有覆盖新的 `list` 默认方法，调用不分页列表查询会抛出 `UnsupportedOperationException`；框架内置 Spring/Quarkus Repository 代理已支持该入口。
- 原有分页查询代码不需要改动；如果业务只是想“查全部匹配记录并排序”，优先改用新的不分页 `list(Criteria, Sort...)`。

## 3.26.9

发布日期：2026-06-15

### 新增

- 新增 Quarkus extension：`muyun-database-quarkus` runtime 模块和 `muyun-database-quarkus-deployment` deployment 模块。
- Quarkus 应用可通过 `net.ximatai.muyun.database.quarkus.MuYunRepository` 声明 Repository，并直接 CDI 注入使用。
- Quarkus Repository 支持 `EntityDao<T, ID>` 保留方法、Jdbi `@SqlQuery` / `@SqlUpdate` 注解方法，以及 Java interface default method。
- Quarkus 扩展提供默认 CDI bean，包括 `Jdbi`、`JdbiMetaDataLoader`、`IDatabaseOperations`、`EntityMetaResolver`、`SimpleEntityManager`、`MigrationOptions`、`MuYunSchemaManager` 和 `MuYunRepositoryFactory`。
- 新增 Quarkus 启动期表结构拉齐：全局配置 `muyun.database.repository-schema-mode=ENSURE/NONE`，仓库级 `@MuYunRepository(alignTable = ENABLED/DISABLED)` 可覆盖。
- 新增 `samples/quarkus-minimal`，演示 Quarkus 中最小接入、Repository CRUD、Jdbi SQL Object 方法和 `@Transactional` 回滚。
- 新增 Quarkus 发布 gate 脚本 `scripts/quarkus-release-gate.sh`，覆盖 JVM PostgreSQL 矩阵、H2 native smoke、外部 PostgreSQL native smoke 和白名单本地发布验证。

### 变更

- 项目文档从“Spring Boot starter 为主”扩展为普通 Java / Spring Boot / Quarkus 三条接入路径。
- Quarkus 配置前缀与 Spring Boot starter 保持一致，使用 `muyun.database.*`；枚举配置同时支持大写和短横线形式，例如 `DRY_RUN_STRICT` 或 `dry-run-strict`。
- Jdbi common plugin 默认安装；PostgreSQL 插件受 `install-postgres-plugins` 与 PostgreSQL driver class 双重条件保护。

### 修复

- 修正迁移规划中部分列定义比对和 schema migration 行为，补齐 Quarkus 表结构拉齐、事务、CRUD、SQL Object、PostgreSQL 矩阵和 native smoke 的自动化验收。
- 发布前 Quarkus 验证入口收口，避免误用根级 `publishAllPublicationsToMavenRepository` 发布 integration-test 等非发布模块。

### 迁移说明

- Quarkus 项目应依赖 `net.ximatai.muyun.database:muyun-database-quarkus`，不要依赖 Spring Boot starter；runtime 会声明对应 deployment artifact。
- Quarkus 使用独立注解 `net.ximatai.muyun.database.quarkus.MuYunRepository`，不要复用 Spring 包下的 `@MuYunRepository`。
- 启动期是否自动拉齐表结构由 `repository-schema-mode` 和仓库级 `alignTable` 共同决定。只读或注入型仓库建议显式使用 `alignTable = DISABLED`，避免启动时执行不需要的 DDL。
- Native image 已覆盖 H2 与外部 PostgreSQL smoke，但完整 dev mode reload 和完整 native 构建矩阵仍不作为当前承诺。

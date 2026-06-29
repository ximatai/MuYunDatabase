# MuYun-Database

MuYun-Database 是一个基于 `Jdbi` 的轻量数据库工具库，面向单表高频业务提供表结构拉齐、CRUD、动态条件、分页、事务一致性和特例 SQL 扩展能力。项目可单独使用，也可作为 [MuYun](https://github.com/ximatai/MuYun) 的一部分使用。

当前主线支持：

- 纯 Jdbi 场景：直接使用 `IDatabaseOperations` / `SimpleEntityManager`
- Spring Boot：通过 starter 自动装配、Repository 扫描和声明式事务桥接
- Quarkus：通过 extension 提供 CDI bean、Repository synthetic bean、启动期表结构拉齐和 native smoke 验证
- 数据库：以 MySQL / PostgreSQL 行为一致性为目标

## 怎么选

| 场景 | 推荐入口 | 说明 |
| --- | --- | --- |
| 普通 Java / 非 Spring 项目 | `muyun-database-jdbi` | 手动传入 `DataSource/Jdbi`，适合工具、批处理或轻量服务 |
| Spring Boot 项目 | `muyun-database-spring-boot-starter` | 自动装配 Jdbi、Repository、事务桥接和表结构拉齐 |
| Quarkus JVM / native 项目 | `muyun-database-quarkus` | 使用 Quarkus CDI、build time 扫描、native reflection/proxy metadata |
| 迁移存量 DAO/MyBatis-Plus | `@MuYunRepository + EntityDao` | 常规 CRUD 走标准接口，复杂 SQL 留在同一 Repository 的 Jdbi SQL Object 方法 |

## 文档导航

推荐阅读顺序：`README -> QUICKSTART -> API_CONTRACT -> REFACTOR_GUIDE -> QUARKUS -> ROADMAP`

按目标直达：

- 快速上手：[`docs/QUICKSTART.md`](docs/QUICKSTART.md)
- Quarkus 接入：[`docs/QUARKUS.md`](docs/QUARKUS.md)
- 稳定契约与约束：[`docs/API_CONTRACT.md`](docs/API_CONTRACT.md)
- 存量项目迁移：[`docs/REFACTOR_GUIDE.md`](docs/REFACTOR_GUIDE.md)
- 项目路线图：[`docs/ROADMAP.md`](docs/ROADMAP.md)
- 版本变更：[`docs/CHANGELOG.md`](docs/CHANGELOG.md)
- 发布流程：[`docs/RELEASE_PROCESS.md`](docs/RELEASE_PROCESS.md)

附录：

- 测试架构：[`docs/TESTING_ARCHITECTURE.md`](docs/TESTING_ARCHITECTURE.md)
- 性能基线模板：[`docs/PERFORMANCE_BASELINE.md`](docs/PERFORMANCE_BASELINE.md)
- 迁移反馈需求池：[`docs/MIGRATION_FEEDBACK_BACKLOG.md`](docs/MIGRATION_FEEDBACK_BACKLOG.md)
- Spring Boot 最小样板：[`samples/starter-minimal`](samples/starter-minimal)
- Quarkus 最小样板：[`samples/quarkus-minimal`](samples/quarkus-minimal)
- 用法示例测试：[`muyun-database-test/src/test/java/net/ximatai/muyun/database/MuYunDatabaseUsageExamplesTestBase.java`](muyun-database-test/src/test/java/net/ximatai/muyun/database/MuYunDatabaseUsageExamplesTestBase.java)

## 快速接入

### 纯 Jdbi

```groovy
dependencies {
    implementation("net.ximatai.muyun.database:muyun-database-jdbi:3.26.14")
}
```

```java
Jdbi jdbi = Jdbi.create(dataSource);
JdbiMetaDataLoader loader = new JdbiMetaDataLoader(jdbi);
JdbiDatabaseOperations<String> db = new JdbiDatabaseOperations<>(jdbi, loader, String.class, "id");
SimpleEntityManager orm = new DefaultSimpleEntityManager(db);

orm.ensureTable(UserEntity.class);
orm.insert(user);
UserEntity loaded = orm.findById(UserEntity.class, user.id);
```

### Spring Boot

```groovy
dependencies {
    implementation("net.ximatai.muyun.database:muyun-database-spring-boot-starter:3.26.14")
}
```

```java
@Configuration
@EnableMuYunRepositories(basePackageClasses = UserRepository.class)
class DaoConfig {
}

@MuYunRepository
interface UserRepository extends EntityDao<UserEntity, String> {
    @org.jdbi.v3.sqlobject.statement.SqlQuery(
            "select id, v_name as name from public.demo_user where id = :id")
    UserEntity findViaSql(@org.jdbi.v3.sqlobject.customizer.Bind("id") String id);

    @org.jdbi.v3.sqlobject.statement.SqlUpdate(
            "update public.demo_user set v_name = :name where id = :id")
    int rename(@org.jdbi.v3.sqlobject.customizer.Bind("id") String id,
               @org.jdbi.v3.sqlobject.customizer.Bind("name") String name);
}
```

### Quarkus

```groovy
dependencies {
    implementation("net.ximatai.muyun.database:muyun-database-quarkus:3.26.14")
}
```

```java
import net.ximatai.muyun.database.quarkus.MuYunRepository;

@MuYunRepository
interface UserRepository extends EntityDao<UserEntity, String> {
}

@jakarta.enterprise.context.ApplicationScoped
class UserService {
    @jakarta.inject.Inject
    UserRepository userRepository;
}
```

Quarkus 使用独立注解 `net.ximatai.muyun.database.quarkus.MuYunRepository`，不会引入 Spring 依赖。扩展 runtime 会声明对应的 deployment artifact，应用只需要依赖 `muyun-database-quarkus`。

## Repository 主路径

常规业务建议默认使用 `@MuYunRepository + EntityDao`：

- 开箱 CRUD：`insert/update/delete/findById/query/pageQuery/count/upsert`
- 条件能力：`Criteria`、分页、排序、条件更新、条件删除
- 表结构：`ensureTable`、启动期拉齐、增量加列、dry-run
- 事务：Spring `@Transactional` 或 Quarkus `jakarta.transaction.Transactional`
- 特例 SQL：同一个 Repository 内使用 Jdbi `@SqlQuery/@SqlUpdate`

当仓库继承 `EntityDao<T, ID>` 时，框架会自动为实体 `T` 注册 Jdbi BeanMapper。`@SqlQuery` 返回 `T` 或 `List<T>` 时通常无需再写 `@RegisterBeanMapper`；若列名与属性名不一致，请在 SQL 中使用别名对齐，例如 `v_name as name`。

底层 `IDatabaseOperations` 仍可用于 Map + SQL 风格的少数手工控制场景。条件更新/删除会拒绝空 where 或无有效 where 字段，不提供默认整表更新/整表删除捷径。

## 版本与模块

- 当前版本 `3.26.14` 兼容 Java 21 及以上
- `1.26.+` 兼容 Java 8，位于 `jdbi-jdk8` 分支
- 具体发布版本以仓库 release / Maven Central 为准

模块说明：

- `muyun-database-core`：标准接口、注解、表结构、ORM 和 SQL 规则
- `muyun-database-core-json-jackson`：可选 Jackson JSON 数组解析支持
- `muyun-database-jdbi`：基于 Jdbi 的核心实现
- `muyun-database-spring-boot-starter`：Spring Boot 自动装配、Repository 扫描与事务桥接
- `muyun-database-quarkus`：Quarkus runtime 模块，提供 CDI bean、Repository 代理和启动期表结构拉齐
- `muyun-database-quarkus-deployment`：Quarkus deployment 模块，负责 build time 扫描、synthetic bean、native metadata
- `muyun-database-test`：项目内部测试模块，业务项目通常不依赖

## 验证与发布

常规本地验证：

```bash
./gradlew test
```

Quarkus 发布前 gate：

```bash
bash scripts/quarkus-release-gate.sh jvm
./gradlew publishReleaseToLocalRepository
```

完整 Quarkus native / PostgreSQL gate 需要 Docker、GraalVM `native-image` 和外部 PostgreSQL，详见 [`docs/QUARKUS.md`](docs/QUARKUS.md)。

## 设计边界

1. 聚焦单表高频能力：DDL、CRUD、条件查询、分页、事务一致性。
2. 保持分层：`core` 定义规则，`jdbi` 负责执行，Spring Boot starter 与 Quarkus extension 负责各自框架装配。
3. 常规业务默认走 `@MuYunRepository + EntityDao`；复杂 SQL 保留在同一 Repository 的 Jdbi SQL Object 方法中。
4. 不提供关系映射 ORM（`1:N/N:N`、级联、延迟加载）。
5. Quarkus native image 已覆盖 H2 与外部 PostgreSQL smoke；完整 native/dev-mode 矩阵不作为当前承诺。

完整目标与优先级见 [`docs/ROADMAP.md`](docs/ROADMAP.md)。

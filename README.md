# MuYun-Database

[![Maven Central](https://img.shields.io/maven-central/v/net.ximatai.muyun.database/muyun-database-core.svg)](https://search.maven.org/search?q=g:net.ximatai.muyun.database)
[![Java CI with Gradle](https://github.com/ximatai/MuYunDatabase/actions/workflows/gradle.yml/badge.svg)](https://github.com/ximatai/MuYunDatabase/actions/workflows/gradle.yml)
[![Git tag](https://img.shields.io/github/v/tag/ximatai/MuYunDatabase)](https://github.com/ximatai/MuYunDatabase/tags)
[![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)](#版本与模块)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

MuYun-Database 是一个基于 `Jdbi` 的轻量数据库工具库，面向 Java 服务提供单表 Repository、表结构拉齐、动态条件、分页、事务一致性和特例 SQL 扩展能力。

它适合希望少写 DAO 样板代码、又不想放弃 SQL 控制力的项目：常规单表操作走 `EntityDao`，复杂查询继续写 Jdbi SQL Object。

## 为什么用它

- **少写单表 DAO 样板代码**：`@MuYunRepository + EntityDao` 直接获得 `insert/update/delete/findById/query/pageQuery/count/upsert`。
- **CRUD 和手写 SQL 不割裂**：简单操作走标准接口，复杂查询或定制更新继续使用 Jdbi `@SqlQuery/@SqlUpdate`，放在同一个 Repository。
- **表结构可以自动拉齐**：实体或 `TableWrapper` 可驱动建表、增量加列和注释同步；Spring Boot / Quarkus 可在启动期按策略执行。
- **动态条件实用且克制**：`Criteria` 支持查询、分页、排序、count、条件更新和条件删除；空 where 或未知字段默认拒绝。
- **事务边界一致**：`EntityDao` 方法和 Jdbi SQL 注解方法在 Spring `@Transactional` 或 Quarkus `jakarta.transaction.Transactional` 下共用同一事务边界。

## 3 分钟跑通 Spring Boot

### 1. 加依赖

```groovy
dependencies {
    implementation("net.ximatai.muyun.database:muyun-database-spring-boot-starter:3.26.15")
}
```

### 2. 定义实体

```java
import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;

@Table(name = "demo_user")
class UserEntity {
    @Id
    @Column(length = 64)
    public String id;

    @Column(name = "v_name", length = 64)
    public String name;

    @Column(name = "i_age")
    public Integer age;
}
```

### 3. 写 Repository

```java
import net.ximatai.muyun.database.core.orm.EntityDao;
import net.ximatai.muyun.database.spring.boot.sql.annotation.MuYunRepository;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@MuYunRepository
interface UserRepository extends EntityDao<UserEntity, String> {
    @SqlQuery("select id, v_name as name, i_age as age from demo_user where id = :id")
    UserEntity findViaSql(@Bind("id") String id);

    @SqlUpdate("update demo_user set v_name = :name where id = :id")
    int rename(@Bind("id") String id, @Bind("name") String name);
}
```

### 4. 启用扫描并调用

```java
import net.ximatai.muyun.database.spring.boot.sql.annotation.EnableMuYunRepositories;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@EnableMuYunRepositories(basePackageClasses = UserRepository.class)
class DaoConfig {
}

@Service
class UserService {
    private final UserRepository users;

    UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    UserEntity createAndLoad() {
        users.ensureTable();

        UserEntity user = new UserEntity();
        user.id = "u_1";
        user.name = "alice";
        user.age = 18;

        users.insert(user);
        users.rename(user.id, "alice-v2");
        return users.findById(user.id);
    }
}
```

更多 Spring Boot、纯 Jdbi、Quarkus 示例见 [`docs/QUICKSTART.md`](docs/QUICKSTART.md)。

## 核心能力

### 开箱即用

- **统一 DAO 入口**：常规业务默认使用 `@MuYunRepository + EntityDao`，单表 CRUD、条件查询、分页、计数、upsert 都走同一套接口。
- **框架自动装配**：Spring Boot starter 自动装配 Jdbi、Repository、事务桥接和表结构拉齐；Quarkus extension 提供 CDI bean、synthetic Repository、启动期拉齐和 native smoke 验证。
- **安全条件写入**：条件更新/删除会拒绝空 where 或无有效 where 字段，不提供默认整表更新/整表删除捷径。
- **SQL 保持一等能力**：多表联查、复杂聚合和数据库特有语法继续写 Jdbi SQL Object，不强行抽象成通用 ORM API。

### 深度能力

- **迁移治理**：`MigrationOptions.dryRun()` 预览 SQL，`dryRunStrict()` 遇到非增量变更直接拒绝，并返回结构化 `MigrationChange`。
- **双库元数据幂等**：针对 MySQL / PostgreSQL 的类型别名、默认值、表/列注释等元数据差异做归一化，减少重复 DDL 和误判迁移。
- **集合字段查询**：`SET` / `JSON_SET` / PostgreSQL `ARRAY` 字段支持 `contains`、`containsAny`、`containsAll`、`isEmpty`、`isNotEmpty`。
- **字段级转换器**：`DatabaseValueConverter` 可统一处理枚举 code、时间、数值等读写转换，也可作用于集合元素和查询参数。
- **运行态表模型**：`RuntimeTableGateway + TableMeta` 面向配置化表、低代码表单等运行时定义的单表记录，复用 Criteria、分页、字段 codec 和逻辑字段名返回。

## 怎么选

| 场景 | 推荐入口 | 说明 |
| --- | --- | --- |
| Spring Boot 项目 | `muyun-database-spring-boot-starter` | 自动装配 Jdbi、Repository、事务桥接和表结构拉齐 |
| 普通 Java / 非 Spring 项目 | `muyun-database-jdbi` | 手动传入 `DataSource/Jdbi`，适合工具、批处理或轻量服务 |
| Quarkus JVM / native 项目 | `muyun-database-quarkus` | 使用 Quarkus CDI、build time 扫描、native reflection/proxy metadata |
| 迁移存量 DAO/MyBatis-Plus | `@MuYunRepository + EntityDao` | 常规 CRUD 走标准接口，复杂 SQL 留在同一 Repository 的 Jdbi SQL Object 方法 |

## 和常见方案的区别

| 方案 | MuYun-Database 更适合的点 |
| --- | --- |
| 纯 Jdbi | 保留 SQL 控制力，同时补齐 Repository、Criteria、分页、表结构拉齐和框架事务集成 |
| MyBatis / MyBatis-Plus | 减少单表 CRUD 样板；复杂 SQL 仍可显式书写，不强推 XML 或重型插件体系 |
| JPA / Hibernate | 不做关系映射、级联、延迟加载，换取更直接的 SQL 行为和更小的抽象面 |
| 全功能 ORM | 聚焦单表高频业务，适合希望数据库行为清晰可控的服务 |

## 更多接入方式

### 纯 Jdbi

```groovy
dependencies {
    implementation("net.ximatai.muyun.database:muyun-database-jdbi:3.26.15")
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

### Quarkus

```groovy
dependencies {
    implementation("net.ximatai.muyun.database:muyun-database-quarkus:3.26.15")
}
```

```java
import net.ximatai.muyun.database.core.orm.EntityDao;
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

## 使用边界

当仓库继承 `EntityDao<T, ID>` 时，框架会自动为实体 `T` 注册 Jdbi BeanMapper。`@SqlQuery` 返回 `T` 或 `List<T>` 时通常无需再写 `@RegisterBeanMapper`；若列名与属性名不一致，请在 SQL 中使用别名对齐，例如 `v_name as name`。

底层 `IDatabaseOperations` 仍可用于 Map + SQL 风格的少数手工控制场景。复杂查询、多表联查、数据库特有语法建议继续写在 Jdbi SQL Object 方法中。

MuYun-Database 不提供关系映射 ORM 能力，包括 `1:N/N:N`、级联、延迟加载、实体状态跟踪。完整目标与优先级见 [`docs/ROADMAP.md`](docs/ROADMAP.md)。

## 文档导航

推荐阅读：

- 快速上手：[`docs/QUICKSTART.md`](docs/QUICKSTART.md)
- 稳定契约与约束：[`docs/API_CONTRACT.md`](docs/API_CONTRACT.md)
- 存量项目迁移：[`docs/REFACTOR_GUIDE.md`](docs/REFACTOR_GUIDE.md)
- Quarkus 接入：[`docs/QUARKUS.md`](docs/QUARKUS.md)

更多文档：

- 运行态字段元数据迁移：[`docs/RUNTIME_METADATA_MIGRATION.md`](docs/RUNTIME_METADATA_MIGRATION.md)
- 项目路线图：[`docs/ROADMAP.md`](docs/ROADMAP.md)
- 版本变更：[`docs/CHANGELOG.md`](docs/CHANGELOG.md)
- 发布流程：[`docs/RELEASE_PROCESS.md`](docs/RELEASE_PROCESS.md)
- 测试架构：[`docs/TESTING_ARCHITECTURE.md`](docs/TESTING_ARCHITECTURE.md)
- 性能基线模板：[`docs/PERFORMANCE_BASELINE.md`](docs/PERFORMANCE_BASELINE.md)
- 迁移反馈需求池：[`docs/MIGRATION_FEEDBACK_BACKLOG.md`](docs/MIGRATION_FEEDBACK_BACKLOG.md)
- Spring Boot 最小样板：[`samples/starter-minimal`](samples/starter-minimal)
- Quarkus 最小样板：[`samples/quarkus-minimal`](samples/quarkus-minimal)
- 用法示例测试：[`muyun-database-test/src/test/java/net/ximatai/muyun/database/MuYunDatabaseUsageExamplesTestBase.java`](muyun-database-test/src/test/java/net/ximatai/muyun/database/MuYunDatabaseUsageExamplesTestBase.java)

## 版本与模块

- 当前版本 `3.26.15` 兼容 Java 21 及以上
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

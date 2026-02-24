# 快速开始

阅读提示：

- 适合对象：首次接入、需要最小可运行示例的同学
- 建议前置：先浏览 [`../README.md`](../README.md) 了解模块边界
- 若关注稳定语义与约束：继续阅读 [`API_CONTRACT.md`](API_CONTRACT.md)

## 0. 前置条件

1. Java 21+（若使用 `1.26.+` 版本可兼容 Java 8）。
2. 可用的 MySQL 或 PostgreSQL 数据库。
3. 已准备 `DataSource`（纯 Jdbi 场景）或 Spring Boot `spring.datasource.*` 配置（Spring 场景）。

## 1. 场景 A：不耦合 Spring（纯 Jdbi）

目标：快速体验 `db`（底层 Map 风格）与 `orm`（上层实体风格）两套调用方式。

### 1.1 依赖

```groovy
dependencies {
    implementation("net.ximatai.muyun.database:muyun-database-jdbi:3.26.1")
}
```

```xml
<dependency>
  <groupId>net.ximatai.muyun.database</groupId>
  <artifactId>muyun-database-jdbi</artifactId>
  <version>3.26.1</version>
</dependency>
```

### 1.2 命令式建表（`TableBuilder.build(TableWrapper)`）

```java
// 首次建表
TableWrapper userTable = TableWrapper.withName("demo_user")
        .setPrimaryKey(PredefinedColumn.Id.POSTGRES.toColumn()) // MySQL 可改为 PredefinedColumn.Id.MYSQL.toColumn()
        .addColumn(Column.of("v_name").setLength(64))
        .addColumn(Column.of("i_age"));

boolean created = new TableBuilder(db).build(userTable);

// 存量表增字段：再次 build 同名表结构即可增量变更
TableWrapper userTableV2 = TableWrapper.withName("demo_user")
        .addColumn(Column.of("v_email").setLength(128));

boolean changed = new TableBuilder(db).build(userTableV2);
```

> 说明：`build(...)` 会按当前元数据做增量对齐。新表返回 `true`，已有表发生结构变化时也会执行变更。

### 1.3 定义实体（自动建表 + ORM）

```java
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

### 1.4 初始化并执行最小闭环

```java
// Step 1) 初始化核心对象
Jdbi jdbi = Jdbi.create(dataSource).setSqlLogger(new Slf4JSqlLogger());
JdbiMetaDataLoader loader = new JdbiMetaDataLoader(jdbi);
JdbiDatabaseOperations<String> db = new JdbiDatabaseOperations<>(jdbi, loader, String.class, "id");
SimpleEntityManager orm = new DefaultSimpleEntityManager(db);

// Step 2) 自动建表（基于注解）
orm.ensureTable(UserEntity.class);

// Step 3A) 底层能力：Map 风格 CRUD
String rawId = db.insertItem("demo_user", Map.of(
        "id", "u_10",
        "v_name", "dave",
        "i_age", 25
));
Map<String, Object> rawRow = db.getItem("demo_user", rawId);
db.patchUpdateItem("demo_user", rawId, Map.of("v_name", "dave-v2"));

// Step 3B) 上层能力：实体风格 CRUD
UserEntity user = new UserEntity();
user.id = "u_1";
user.name = "alice";
user.age = 18;
orm.insert(user);
UserEntity loaded = orm.findById(UserEntity.class, user.id);

// Step 3C) Criteria 查询 + 排序 + 分页
List<UserEntity> rows = orm.query(
        UserEntity.class,
        Criteria.of().eq("v_name", "alice").gte("i_age", 18),
        PageRequest.of(1, 20),
        Sort.desc("i_age")
);

```

### 1.5 迁移控制（可选）

```java
// 只预览 SQL，不执行
MigrationResult dryRun = orm.ensureTable(UserEntity.class, MigrationOptions.dryRun());

// 严格模式：遇到非增量变更直接拒绝
orm.ensureTable(UserEntity.class, MigrationOptions.dryRunStrict());
```

## 2. 场景 B：耦合 Spring Boot（`@Transactional`）

目标：以 `@MuYunRepository + EntityDao` 作为默认主路径，在 Spring 管理事务下统一处理 `CRUD + 特例 SQL（显式 SQL 注解）+ 表结构拉齐`。

### 2.1 依赖

```groovy
dependencies {
    implementation("net.ximatai.muyun.database:muyun-database-spring-boot-starter:3.26.1")
}
```

### 2.2 配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/testdb
    username: testuser
    password: testpass

muyun:
  database:
    primary-key-name: id
    primary-key-type: STRING
    default-schema: public
    migration-mode: APPLY # APPLY | DRY_RUN | DRY_RUN_STRICT
    repository-schema-mode: ENSURE # NONE | ENSURE
    transaction-aware-data-source: true
```

### 2.3 推荐主路径：`@MuYunRepository` + `EntityDao`（开箱 CRUD + 特例 SQL）

```java
@Configuration
@EnableMuYunRepositories(basePackageClasses = UserRepository.class)
class DaoConfig {
}

@MuYunRepository(alignTable = MuYunRepository.AlignTable.DEFAULT)
interface UserRepository extends EntityDao<UserEntity, String> {
    @org.jdbi.v3.sqlobject.statement.SqlQuery("select id, v_name as name, i_age as age from public.demo_user where id = :id")
    UserEntity findByIdViaSql(@org.jdbi.v3.sqlobject.customizer.Bind("id") String id);

    @org.jdbi.v3.sqlobject.statement.SqlUpdate("update public.demo_user set v_name = :name where id = :id")
    int rename(@org.jdbi.v3.sqlobject.customizer.Bind("id") String id,
               @org.jdbi.v3.sqlobject.customizer.Bind("name") String name);
}

@Service
class UserService {
    private final UserRepository userRepository;

    UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void demo() {
        UserEntity user = new UserEntity();
        user.id = "u_20";
        user.name = "neo";
        user.age = 22;

        userRepository.insert(user);              // 开箱 CRUD（EntityDao）
        userRepository.rename("u_20", "neo_v2"); // 特例 SQL
    }
}
```

说明：当仓库继承 `EntityDao<T, ID>` 时，框架会自动为 `T` 注册 Jdbi BeanMapper，因此 `@SqlQuery` 返回 `T` / `List<T>` 默认无需显式 `@RegisterBeanMapper`。若列名与属性名不一致，请在 SQL 中使用别名对齐（例如 `v_name as name`）。

### 2.4 事务演示（同一 DAO 内混合 CRUD + 特例 SQL）

```java
@Service
class UserService {
    private final UserRepository userRepository;

    UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void createWithRollbackDemo() {
        UserEntity user = new UserEntity();
        user.id = "u_3";
        user.name = "carol";
        user.age = 21;
        userRepository.insert(user);         // CRUD
        userRepository.rename("u_3", "c2"); // 特例 SQL

        // throw new RuntimeException("force rollback");
    }
}
```

### 2.5 表结构拉齐：全局与仓库级覆盖

```java
// 全局：跟随配置 muyun.database.repository-schema-mode
@MuYunRepository(alignTable = MuYunRepository.AlignTable.DEFAULT)
interface UserRepository extends EntityDao<UserEntity, String> {
}

// 局部：强制当前仓库参与拉齐（即使全局是 NONE）
@MuYunRepository(alignTable = MuYunRepository.AlignTable.ENABLED)
interface OrderRepository extends EntityDao<OrderEntity, String> {
}

// 局部：跳过当前仓库拉齐（即使全局是 ENSURE）
@MuYunRepository(alignTable = MuYunRepository.AlignTable.DISABLED)
interface AuditLogRepository extends EntityDao<AuditLogEntity, String> {
}
```

### 2.6 可选低层能力（仅在必要时使用）

`IDatabaseOperations` 与底层 SQL 仍可用，但建议仅用于特殊场景（例如历史 SQL 复用、极端性能调优或跨表手工 SQL），常规业务默认优先 `@MuYunRepository + EntityDao`。

## 3. 进阶示例

### 3.1 分页结果

```java
PageResult<UserEntity> page = userRepository.pageQuery(
        Criteria.of().eq("v_name", "alice"),
        PageRequest.of(1, 20),
        Sort.desc("i_age")
);
```

### 3.2 Criteria 进阶（子查询 / RAW / guard）

```java
Criteria c = Criteria.of()
        .inSubQuery("id", SqlSubQuery.of(
                "select id from demo_user where i_age >= :age",
                Map.of("age", 18)
        ))
        .raw(SqlRawCondition.of("i_age <= :maxAge", Map.of("maxAge", 60)));

List<UserEntity> rows = userRepository.query(c, PageRequest.of(1, 20), Sort.desc("i_age"));

RawSqlGuard denyLike = sql -> {
    if (sql.toLowerCase().contains(" like ")) {
        throw new IllegalArgumentException("LIKE not allowed");
    }
};
```

## 4. 参考

1. 重构指南（DB 直调 / MyBatis-Plus）：[`REFACTOR_GUIDE.md`](REFACTOR_GUIDE.md)
2. Starter 样板：[`../samples/starter-minimal`](../samples/starter-minimal)
3. 用法示例测试：[`../muyun-database-test/src/test/java/net/ximatai/muyun/database/MuYunDatabaseUsageExamplesTestBase.java`](../muyun-database-test/src/test/java/net/ximatai/muyun/database/MuYunDatabaseUsageExamplesTestBase.java)

下一步：

1. 确认稳定契约与失败边界：[`API_CONTRACT.md`](API_CONTRACT.md)
2. 开始存量代码迁移：[`REFACTOR_GUIDE.md`](REFACTOR_GUIDE.md)

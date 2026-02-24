# MuYun-Database

一个方便创建、修改表结构的工具库，核心依赖 `jdbi`，并提供 Spring Boot starter。支持 `MySQL`、`Postgres`。
既是 [MuYun](https://github.com/ximatai/MuYun) 的一部分，也可以单独使用。

## 文档导航

推荐阅读顺序：`README -> QUICKSTART -> API_CONTRACT -> REFACTOR_GUIDE -> ROADMAP`

主文档：

- 快速上手（初始化与最小可运行示例）：[`docs/QUICKSTART.md`](docs/QUICKSTART.md)
- API 稳定契约（冻结）：[`docs/API_CONTRACT.md`](docs/API_CONTRACT.md)
- 重构指南（DB 直调 / MyBatis-Plus 到 Repository）：[`docs/REFACTOR_GUIDE.md`](docs/REFACTOR_GUIDE.md)
- 项目路线图（已做能力 + 未来优先级）：[`docs/ROADMAP.md`](docs/ROADMAP.md)

附录：

- 测试架构（测试分层与隔离规则）：[`docs/TESTING_ARCHITECTURE.md`](docs/TESTING_ARCHITECTURE.md)
- 性能基线模板：[`docs/PERFORMANCE_BASELINE.md`](docs/PERFORMANCE_BASELINE.md)
- 迁移反馈需求池（待评估）：[`docs/MIGRATION_FEEDBACK_BACKLOG.md`](docs/MIGRATION_FEEDBACK_BACKLOG.md)
- starter 最小样板：[`samples/starter-minimal`](samples/starter-minimal)
- 用法示例测试：[`muyun-database-test/src/test/java/net/ximatai/muyun/database/MuYunDatabaseUsageExamplesTestBase.java`](muyun-database-test/src/test/java/net/ximatai/muyun/database/MuYunDatabaseUsageExamplesTestBase.java)

## 5 分钟上手

目标：在 Spring 项目中以一个统一 DAO 接口完成 `CRUD + 特例 SQL + 表结构拉齐`。

```groovy
dependencies {
    implementation("net.ximatai.muyun.database:muyun-database-spring-boot-starter:3.26.1")
}
```

```java
@Configuration
@EnableMuYunRepositories(basePackageClasses = UserRepository.class)
class DaoConfig {
}

// Step 1) 配置全局表结构拉齐策略（可选）
// muyun.database.repository-schema-mode=ENSURE   # NONE | ENSURE

// Step 2) 统一 DAO：开箱 CRUD + 特例 SQL
@MuYunRepository(alignTable = MuYunRepository.AlignTable.DEFAULT)
interface UserRepository extends EntityDao<UserEntity, String> {
    @org.jdbi.v3.sqlobject.statement.SqlUpdate("update public.demo_user set v_name = :name where id = :id")
    int rename(@org.jdbi.v3.sqlobject.customizer.Bind("id") String id,
               @org.jdbi.v3.sqlobject.customizer.Bind("name") String name);
}

// Step 2.1) 如需覆盖全局，可在仓库级单独控制
@MuYunRepository(alignTable = MuYunRepository.AlignTable.DISABLED)
interface AuditLogRepository extends EntityDao<AuditLogEntity, String> {
}

// Step 3) Service 直接注入统一 DAO，并由 @Transactional 统一控制
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
        userRepository.insert(user);              // 开箱 CRUD
        userRepository.rename("u_3", "carol_v2"); // 特例 SQL

        // 抛异常后，以上两步一起回滚
        // throw new RuntimeException("force rollback");
    }
}
```

> 完整初始化、配置项与更多示例见 [`docs/QUICKSTART.md`](docs/QUICKSTART.md)。

## 底层能力（可选）

`IDatabaseOperations` 依然可用于底层直调（Map + SQL），适合极少数需要手工控制的场景；常规业务开发建议默认使用 `@MuYunRepository + EntityDao`。

## 兼容性说明

- 版本 `3.26.1` 兼容 `Java21` 及以上
- 版本 `1.26.+` 兼容 `Java8`
- 具体发布版本以仓库 release / Maven 中央仓库为准

## 包说明

- `muyun-database-core`：纯 `Java`，定义标准接口和核心逻辑
- `muyun-database-jdbi`：基于 `jdbi` 的实现；普通 Java 项目推荐优先依赖此模块
- `muyun-database-spring-boot-starter`：Spring Boot 自动装配与声明式事务桥接；Spring Boot 项目推荐优先依赖此模块
- `muyun-database-jdbi-jdk8`：兼容 `Java8`，仅存在于 `jdbi-jdk8` 分支
- `muyun-database-test`：测试模块，业务项目通常不依赖

## 设计摘要

1. 聚焦单表高频能力：DDL、CRUD、条件查询、分页、事务一致性。
2. 保持分层：`core` 定义规则、`jdbi` 负责执行、`starter` 负责装配。
3. 常规业务默认走 `@MuYunRepository + EntityDao`；仅在必要时回退更底层方式。
4. 默认以 MySQL/Postgres 双库行为一致性为目标。

完整目标与优先级见 [`docs/ROADMAP.md`](docs/ROADMAP.md)。

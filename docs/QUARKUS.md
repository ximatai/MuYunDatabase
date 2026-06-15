# Quarkus 扩展接入说明

当前 Quarkus 支持复用现有 `core + jdbi` 能力，并让 Quarkus 应用通过 CDI 注入 MuYunDatabase 基础 bean 与 Repository 代理。

## 依赖

```kotlin
dependencies {
    implementation("net.ximatai.muyun.database:muyun-database-quarkus:3.26.8")
}
```

扩展 runtime 会声明对应的 deployment artifact：

```properties
deployment-artifact=net.ximatai.muyun.database:muyun-database-quarkus-deployment::jar:3.26.8
```

## 配置项

配置前缀与 Spring Boot starter 保持一致：

```properties
muyun.database.primary-key-name=id
muyun.database.primary-key-type=STRING
muyun.database.default-schema=public
muyun.database.migration-mode=APPLY
muyun.database.repository-schema-mode=ENSURE
muyun.database.install-common-plugins=true
muyun.database.install-postgres-plugins=true
```

枚举值支持大写和短横线形式，例如 `DRY_RUN_STRICT` 或 `dry-run-strict`。

## CDI Bean

扩展默认提供以下 bean，应用可自定义同类型 bean 覆盖默认实现：

- `org.jdbi.v3.core.Jdbi`
- `net.ximatai.muyun.database.jdbi.JdbiMetaDataLoader`
- `net.ximatai.muyun.database.core.IDatabaseOperations`
- `net.ximatai.muyun.database.core.orm.EntityMetaResolver`
- `net.ximatai.muyun.database.core.orm.SimpleEntityManager`
- `net.ximatai.muyun.database.core.orm.MigrationOptions`
- `net.ximatai.muyun.database.quarkus.MuYunSchemaManager`
- `net.ximatai.muyun.database.quarkus.MuYunRepositoryFactory`

如需自定义 Jdbi，可声明 `MuYunJdbiConfigurer` bean：

```java
@ApplicationScoped
class MyJdbiConfigurer implements MuYunJdbiConfigurer {
    @Override
    public void configure(Jdbi jdbi) {
        // register plugins, mappers, arguments...
    }
}
```

## Repository

Quarkus 使用独立注解，避免引入 Spring 依赖：

```java
import net.ximatai.muyun.database.quarkus.MuYunRepository;

@MuYunRepository
public interface UserRepository extends EntityDao<UserEntity, String> {
}
```

构建期会扫描 `@MuYunRepository` 接口并注册为 Arc synthetic bean，应用中可直接注入：

```java
@Inject
UserRepository userRepository;
```

Repository 代理支持：

- `EntityDao<T, ID>` 保留方法
- Jdbi `@SqlQuery`
- Jdbi `@SqlUpdate`
- Java interface default method

当仓库继承 `EntityDao<T, ID>` 时，扩展会在 build time 解析实体类型并为实体注册 native reflection metadata，用于 `EntityMapper` 与 Jdbi `BeanMapper` 的反射访问。

启动期表结构拉齐会复用同一份 build time 解析结果：

- `muyun.database.repository-schema-mode=ENSURE` 时，`@MuYunRepository` 默认会在 Quarkus `StartupEvent` 自动执行实体表结构拉齐
- `repository-schema-mode=NONE` 时，默认不自动拉齐
- 仓库级 `@MuYunRepository(alignTable = ENABLED)` 可强制开启
- 仓库级 `@MuYunRepository(alignTable = DISABLED)` 可关闭注入型或只读型仓库的启动期拉齐

优先级如下：

| `repository-schema-mode` | `alignTable` | 启动期是否拉齐 |
| --- | --- | --- |
| `ENSURE` | `DEFAULT` | 是 |
| `NONE` | `DEFAULT` | 否 |
| 任意 | `ENABLED` | 是 |
| 任意 | `DISABLED` | 否 |

## 事务与表结构

Quarkus 应用可使用 `jakarta.transaction.Transactional` 包住同一个仓库中的 `EntityDao` 方法与 Jdbi SQL Object 方法。扩展依赖 Quarkus Agroal + Narayana JTA，已验证两类写入在同一事务边界内回滚。

表结构拉齐可显式调用：

```java
@Inject
UserRepository userRepository;

userRepository.ensureTable();
```

或使用 `MuYunSchemaManager`：

```java
@Inject
MuYunSchemaManager schemaManager;

schemaManager.ensureTable(UserEntity.class);
```

`migration-mode` 控制 `MuYunSchemaManager` 的执行策略：

- `APPLY`
- `DRY_RUN`
- `DRY_RUN_STRICT`

## 当前边界

已自动化验证：

- Jandex 扫描 `@MuYunRepository` 接口
- 非接口标注 `@MuYunRepository` 会失败
- Repository factory 能创建 JDK 代理并调用 default method
- Quarkus synthetic bean supplier 必须通过 recorder 提供
- 真实 Quarkus 应用中的 CDI 注入、Jdbi、Agroal 数据源和 Repository synthetic bean
- `EntityDao` CRUD、Criteria 查询、Jdbi SQL Object 混合仓库、SQL Object 返回实体自动 BeanMapper
- `@Transactional` 下 `EntityDao` 与 Jdbi SQL Object 同事务回滚
- `MuYunSchemaManager` 创建表、增量加列和幂等拉齐
- `repository-schema-mode=ENSURE` 下 `@MuYunRepository` 启动期自动表结构拉齐
- `repository-schema-mode=NONE` 下 `DEFAULT` 不自动拉齐，`ENABLED` 强制拉齐
- `@MuYunRepository(alignTable = DISABLED)` 仓库级关闭启动期拉齐
- Repository 实体 native reflection metadata 预注册
- PostgreSQL Testcontainers 矩阵覆盖 CRUD、SQL Object、事务回滚和 schema migration

尚未承诺：

- Quarkus dev mode reload
- 完整 native image 构建矩阵；当前先提供 H2 native smoke

## 测试矩阵

默认 Quarkus integration test 会在 Docker 不可用时跳过 PostgreSQL Testcontainers 矩阵，避免影响普通本地开发：

```bash
./gradlew :muyun-database-quarkus-integration-test:test
```

CI 或发布前应强制 PostgreSQL 矩阵必须执行。此模式下如果 Docker 不可用会直接失败，避免“测试通过但 PostgreSQL 未验证”的假阳性：

```bash
./gradlew :muyun-database-quarkus-integration-test:test -Pmuyun.postgres.it.required=true
```

native smoke 会构建 Quarkus native runner，并启动应用验证扩展的 build time 与 runtime init 链路。Quarkus 3.22 下 native 构建不能同时输出 JAR 和 native runner，因此需要关闭 JAR 输出：

```bash
./gradlew :muyun-database-quarkus-integration-test:testNative \
  -Dquarkus.native.enabled=true \
  -Dquarkus.package.jar.enabled=false
```

本机执行需要 GraalVM `native-image` 可用；也可以按 Quarkus 原生镜像工具链要求改用容器构建参数。

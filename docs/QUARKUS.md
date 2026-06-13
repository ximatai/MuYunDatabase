# Quarkus 扩展接入说明（JVM MVP）

当前 Quarkus 支持处于 JVM MVP 阶段，目标是复用现有 `core + jdbi` 能力，并让 Quarkus 应用可以通过 CDI 注入 MuYunDatabase 基础 bean 与 Repository 代理。

## 依赖

```kotlin
dependencies {
    implementation("net.ximatai.muyun.database:muyun-database-quarkus:3.26.8")
}
```

扩展 runtime 会声明对应的 deployment artifact：

```properties
deployment-artifact=net.ximatai.muyun.database:muyun-database-quarkus-deployment:3.26.8
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

## 当前边界

已自动化验证：

- Jandex 扫描 `@MuYunRepository` 接口
- 非接口标注 `@MuYunRepository` 会失败
- Repository factory 能创建 JDK 代理并调用 default method
- Quarkus synthetic bean supplier 必须通过 recorder 提供

尚未承诺：

- native image
- Quarkus dev mode reload
- 真实 Quarkus 应用中的数据库 CRUD/事务 Testcontainers 验收
- `@MuYunRepository.alignTable` 的启动期自动表结构拉齐

上述能力应作为下一轮 Quarkus 集成测试和 native 支持继续推进。

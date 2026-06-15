# Quarkus Minimal Sample

这个样板演示如何在 Quarkus 中最小接入 `muyun-database-quarkus`，并用一个 `@MuYunRepository` 同时完成：

1. 开箱 CRUD（`EntityDao`）
2. 特例 SQL（Jdbi SQL Object 注解）
3. `@Transactional` 下统一回滚

## 1. 运行环境

1. Java 21+
2. 无需外部数据库，默认使用 H2 in-memory

## 2. 本地源码联调

该 sample 是独立 Gradle build，但会在 `settings.gradle.kts` 中直接引用仓库内的 Quarkus 相关源码模块：

```kotlin
include(":muyun-database-core")
include(":muyun-database-jdbi")
include(":muyun-database-quarkus")
include(":muyun-database-quarkus-deployment")
```

因此从仓库内运行时会直接使用当前工作区的 `muyun-database-quarkus` 与 `muyun-database-quarkus-deployment` 源码，而不是 Maven Central 上的已发布版本。

## 3. 启动

```bash
./gradlew -p samples/quarkus-minimal quarkusDev
```

启动后可访问：

```bash
curl http://localhost:8080/demo/repository
curl http://localhost:8080/demo/transaction
```

预期输出：

```text
repository:renamed
transaction:rolled-back
```

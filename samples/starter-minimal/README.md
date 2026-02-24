# Starter Minimal Sample

这个样板演示如何在 Spring Boot 中最小接入 `muyun-database-spring-boot-starter`，并用一个 `@MuYunRepository` 同时完成：

1. 开箱 CRUD（`EntityDao`）
2. 特例 SQL（Jdbi SQL Object 注解）
3. `@Transactional` 下统一回滚

## 1. 运行环境

1. Java 21+
2. PostgreSQL（本地或容器）

## 2. 配置数据库

编辑 `src/main/resources/application.yml`：

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
    migration-mode: APPLY
```

## 3. 启动

```bash
./gradlew -p samples/starter-minimal bootRun
```

启动后会自动执行一次事务示例，流程如下：

1. `DemoUserRepository.insertSqlRow(...)` 执行特例 SQL 插入
2. `DemoUserRepository.insert(...)` 执行实体 CRUD 插入
3. 业务方法抛异常触发事务回滚

最终两张表记录数均为 0。

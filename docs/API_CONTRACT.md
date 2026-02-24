# API 稳定契约（冻结）

## 1. 统一 DAO 契约

1. 统一入口为 `@MuYunRepository` + `EntityDao<T, ID>`。
2. 不支持方法名推断生成 SQL。
3. 仅支持两类方法：
   1. `EntityDao` 约定方法（开箱 CRUD/查询分页/表结构拉齐）。
   2. 显式 SQL 注解方法（`@Select/@Insert/@Update/@Delete`）。

## 2. EntityDao 方法集合（稳定）

```java
boolean ensureTable();
ID insert(T entity);
int updateById(T entity);
int deleteById(ID id);
boolean existsById(ID id);
T findById(ID id);
List<T> query(Criteria criteria, PageRequest pageRequest, Sort... sorts);
List<T> list(Criteria criteria, PageRequest pageRequest, Sort... sorts);
PageResult<T> pageQuery(Criteria criteria, PageRequest pageRequest, Sort... sorts);
PageResult<T> page(Criteria criteria, PageRequest pageRequest, Sort... sorts);
long count(Criteria criteria);
int upsert(T entity);
```

## 3. 启动期校验（Fail Fast）

1. DAO 接口必须标注 `@MuYunRepository`。
2. 非 `EntityDao` 方法必须标注 SQL 注解。
3. 若声明了 `EntityDao` 保留方法名但签名不匹配（参数/返回不一致），启动直接失败。
4. 不允许通过方法重载/重定义覆盖 `EntityDao` 的既有语义。

## 4. 表结构拉齐策略（稳定）

1. 全局配置：`muyun.database.repository-schema-mode=NONE|ENSURE`。
2. 仓库级配置：`@MuYunRepository(alignTable = DEFAULT|ENABLED|DISABLED)`。
3. 优先级：仓库级显式配置优先于全局配置。
4. `DEFAULT` 表示跟随全局策略。

## 5. 事务语义（稳定）

1. `@Transactional` 下，`EntityDao` 约定方法与注解 SQL 方法必须共用同一事务边界。
2. 同一事务内任一步抛异常，全部回滚。

## 6. 边界声明

1. `EntityDao` 聚焦单表高频场景。
2. 复杂查询由注解 SQL 或底层 SQL 承担。
3. 不提供关系映射 ORM（`1:N/N:N`、级联、延迟加载）。

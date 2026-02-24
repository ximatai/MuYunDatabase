# API 稳定契约（冻结）

阅读提示：

- 适合对象：需要确认“能做/不能做”边界的研发与评审
- 建议前置：先跑通 [`QUICKSTART.md`](QUICKSTART.md) 再看本契约
- 若目标是迁移落地：继续阅读 [`REFACTOR_GUIDE.md`](REFACTOR_GUIDE.md)

## 1. 统一 DAO 契约

1. 统一入口为 `@MuYunRepository` + `EntityDao<T, ID>`。
2. 不支持方法名推断生成 SQL。
3. 仅支持两类方法：
   1. `EntityDao` 约定方法（开箱 CRUD/查询分页/表结构拉齐）。
   2. 显式 SQL 注解方法（Jdbi SQL Object 注解）。
4. 当仓库继承 `EntityDao<T, ID>` 时，框架自动为实体 `T` 注册 Jdbi BeanMapper，`@SqlQuery` 返回 `T` / `List<T>` 无需显式 `@RegisterBeanMapper`。
5. 上述自动映射仅作用于 `EntityDao` 混合仓库；纯 Jdbi DAO（未继承 `EntityDao`）遵循 Jdbi 默认映射规则。

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
2. 非 `EntityDao` 方法必须标注 Jdbi SQL Object 注解（`@SqlQuery/@SqlUpdate`）。
3. 若声明了 `EntityDao` 保留方法名但签名不匹配（参数/返回不一致），启动直接失败。
4. 不允许通过方法重载/重定义覆盖 `EntityDao` 的既有语义。

## 4. 表结构拉齐策略（稳定）

1. 全局配置：`muyun.database.repository-schema-mode=NONE|ENSURE`。
2. 仓库级配置：`@MuYunRepository(alignTable = DEFAULT|ENABLED|DISABLED)`。
3. 优先级：仓库级显式配置优先于全局配置。
4. `DEFAULT` 表示跟随全局策略。

## 5. 事务语义（稳定）

1. `@Transactional` 下，`EntityDao` 约定方法与 Jdbi SQL 注解方法必须共用同一事务边界。
2. 同一事务内任一步抛异常，全部回滚。

## 6. 边界声明

1. `EntityDao` 聚焦单表高频场景。
2. 复杂查询由 Jdbi SQL 注解方法或底层 SQL 承担。
3. 不提供关系映射 ORM（`1:N/N:N`、级联、延迟加载）。

下一步：若你在做历史项目改造，请按 [`REFACTOR_GUIDE.md`](REFACTOR_GUIDE.md) 的“推荐重构路径”执行。

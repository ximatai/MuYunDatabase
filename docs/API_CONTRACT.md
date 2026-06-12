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
int updateByIdAndCondition(T entity, Map<String, Object> conditions);
int deleteById(ID id);
int deleteByIdAndCondition(ID id, Map<String, Object> conditions);
boolean existsById(ID id);
T findById(ID id);
List<T> query(Criteria criteria, PageRequest pageRequest, Sort... sorts);
List<T> list(Criteria criteria, PageRequest pageRequest, Sort... sorts);
PageResult<T> pageQuery(Criteria criteria, PageRequest pageRequest, Sort... sorts);
PageResult<T> page(Criteria criteria, PageRequest pageRequest, Sort... sorts);
long count(Criteria criteria);
int upsert(T entity);
```

## 3. 条件写契约（稳定）

1. `updateByIdAndCondition(T entity, Map<String, Object> conditions)` 以实体主键和附加条件共同定位记录。
2. `deleteByIdAndCondition(ID id, Map<String, Object> conditions)` 以主键和附加条件共同定位记录。
3. `conditions` 的 key 使用实体字段名，框架按实体映射解析为物理列名；实体元数据已映射的物理列名也可使用。
4. 条件字段不存在或字段名不安全时，直接抛出 ORM 映射/条件异常，不拼接 SQL。
5. 条件未命中时返回 `0`，不会抛出乐观锁或业务冲突异常；业务层可按影响行数解释冲突语义。
6. 条件写仍是单表能力，不承载租户、软删、生命周期、权限等业务语义。

## 4. 启动期校验（Fail Fast）

1. DAO 接口必须标注 `@MuYunRepository`。
2. 非 `EntityDao` 方法必须标注 Jdbi SQL Object 注解（`@SqlQuery/@SqlUpdate`）。
3. 若声明了 `EntityDao` 保留方法名但签名不匹配（参数/返回不一致），启动直接失败。
4. 不允许通过方法重载/重定义覆盖 `EntityDao` 的既有语义。

## 5. 表结构拉齐策略（稳定）

1. 全局配置：`muyun.database.repository-schema-mode=NONE|ENSURE`。
2. 仓库级配置：`@MuYunRepository(alignTable = DEFAULT|ENABLED|DISABLED)`。
3. 优先级：仓库级显式配置优先于全局配置。
4. `DEFAULT` 表示跟随全局策略。

## 6. 事务语义（稳定）

1. `@Transactional` 下，`EntityDao` 约定方法与 Jdbi SQL 注解方法必须共用同一事务边界。
2. 同一事务内任一步抛异常，全部回滚。

## 7. 边界声明

1. `EntityDao` 聚焦单表高频场景。
2. 复杂查询由 Jdbi SQL 注解方法或底层 SQL 承担。
3. 不提供关系映射 ORM（`1:N/N:N`、级联、延迟加载）。
4. `Set<String>` 字段默认推断为 `ColumnType.SET`，使用 CSV 语义存入 `text` 列。
5. `ColumnType.SET` 写入时不允许元素包含英文逗号 `,`（否则拒绝写入），以避免 CSV 不可逆解析。
6. `ColumnType.JSON_SET` 使用 JSON 字符串数组语义存入 `text` 列，适用于元素可能包含逗号的字符串集合。
7. `ColumnType.JSON_SET` 必须通过 `@Column(type = ColumnType.JSON_SET)` 显式声明；默认 `Set<String>` 推断结果仍为 `ColumnType.SET`。
8. `ColumnType.JSON_SET` 的元素按字符串处理：写入时忽略 `null` 元素、按集合语义去重、保留首次出现顺序；空集合写入为 `[]`，字段值为 `null` 时写入为 `null`。
9. `ColumnType.JSON_SET` 读取非法 JSON 数组或写入非法 JSON 数组字符串时直接拒绝，不做静默降级。

下一步：若你在做历史项目改造，请按 [`REFACTOR_GUIDE.md`](REFACTOR_GUIDE.md) 的“推荐重构路径”执行。

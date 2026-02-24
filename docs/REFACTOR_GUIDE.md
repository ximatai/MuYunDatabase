# 重构指南（DB 直调 / MyBatis-Plus -> MuYun Repository）

## 目标

将历史 `IDatabaseOperations` 直调代码与 MyBatis-Plus 仓储调用，统一重构为 `@MuYunRepository + EntityDao`。

适用边界：

1. 单表高频 CRUD / 条件查询 / 分页。
2. 特例 SQL 使用同一仓库接口中的注解 SQL。
3. 多表复杂 SQL 不强行抽象为 `EntityDao`。

---

## 1. 推荐重构路径

1. 先定义 `@MuYunRepository` 接口并继承 `EntityDao<T, ID>`。
2. 将 service 对 `mapper/db` 的依赖替换为 `entityDao`。
3. 替换单表 CRUD、查询、分页、count、exists。
4. 将特例 SQL 迁到同一仓库接口的注解方法。
5. 配置并验证表结构拉齐策略（全局 + 仓库覆盖）。

---

## 2. DB 直调对照表

| 旧接口（IDatabaseOperations） | 新接口（EntityDao） |
| --- | --- |
| `new TableBuilder(db).build(Entity.class)` | `entityDao.ensureTable()` |
| `db.insertItem("table", map)` | `entityDao.insert(entity)` |
| `db.getItem("table", id)` | `entityDao.findById(id)` |
| `db.updateItem("table", map)` | `entityDao.updateById(entity)` |
| `db.upsertItem("table", map)` | `entityDao.upsert(entity)` |
| `db.deleteItem("table", id)` | `entityDao.deleteById(id)` |
| `db.getItem("table", id) != null` | `entityDao.existsById(id)` |
| 手工分页查询 SQL | `entityDao.query(...) / entityDao.pageQuery(...)` |
| 手工 `count(*)` SQL | `entityDao.count(criteria)` |
| `db.query(sql, params)`（复杂场景） | 同一仓库内注解 SQL 方法 |

---

## 3. MyBatis-Plus 映射

### 3.1 仓储替换模板

```java
class UserService {
    private final UserDao userDao;
}

@MuYunRepository(alignTable = MuYunRepository.AlignTable.DEFAULT)
interface UserDao extends EntityDao<UserEntity, String> {
}
```

### 3.2 基础 CRUD 映射

| MyBatis-Plus | Muyun Repository |
| --- | --- |
| `baseMapper.insert(entity)` | `entityDao.insert(entity)` |
| `baseMapper.updateById(entity)` | `entityDao.updateById(entity)` |
| `baseMapper.selectById(id)` | `entityDao.findById(id)` |
| `baseMapper.deleteById(id)` | `entityDao.deleteById(id)` |
| `baseMapper.insertOrUpdate(entity)`（业务封装） | `entityDao.upsert(entity)` |
| `selectCount(wrapper)` | `entityDao.count(criteria)` |
| `selectById(id) != null` | `entityDao.existsById(id)` |

### 3.3 QueryWrapper -> Criteria

| QueryWrapper | Criteria |
| --- | --- |
| `eq("v_name", name)` | `Criteria.of().eq("v_name", name)` |
| `ne("status", 0)` | `.ne("status", 0)` |
| `gt("age", 18)` | `.gt("age", 18)` |
| `like("name", "ali%")` | `.like("name", "ali%")` |
| `in("id", ids)` | `.in("id", ids)` |
| `between("age", 18, 30)` | `.between("age", 18, 30)` |

### 3.4 分页与排序

| MyBatis-Plus | Muyun Repository |
| --- | --- |
| `Page<T> page = new Page<>(pageNum, pageSize)` | `PageRequest.of(pageNum, pageSize)` |
| `selectPage(page, wrapper)` | `entityDao.pageQuery(criteria, pageRequest, sorts...)` |
| `IPage.getRecords()` | `PageResult.getRecords()` |
| `IPage.getTotal()` | `PageResult.getTotal()` |
| `orderByAsc("i_age")` | `Sort.asc("i_age")` |
| `orderByDesc("create_time")` | `Sort.desc("create_time")` |

说明：`entityDao.page(...)` 是 `entityDao.pageQuery(...)` 的别名。

---

## 4. 表结构拉齐策略

1. 全局配置：`muyun.database.repository-schema-mode=NONE|ENSURE`。
2. 仓库覆盖：`@MuYunRepository(alignTable = DEFAULT|ENABLED|DISABLED)`。
3. 优先级：仓库显式配置优先于全局，`DEFAULT` 跟随全局。

---

## 5. 回归清单

1. MySQL/PostgreSQL 均跑通 `ensureTable + CRUD + query/pageQuery/count`。
2. `@Transactional` 中 `EntityDao` 与注解 SQL 同边界回滚。
3. `repository-schema-mode` 与 `alignTable` 组合行为符合预期。

# Runtime Table 元数据迁移指南

阅读提示：

- 适合对象：使用运行时定义表、运行时字段配置、Map 记录或动态模型的项目
- 建议前置：先阅读 [`API_CONTRACT.md`](API_CONTRACT.md) 的 Criteria 与边界声明
- 若还未跑通最小示例：先看 [`QUICKSTART.md`](QUICKSTART.md) 的“运行态单表记录”

## 目标

将旧的 `RuntimeTableGateway + CriteriaColumnResolver` 单向列名解析路径，迁移到 `RuntimeTableGateway + TableMeta`。

迁移后，运行态记录可以和静态实体一样使用：

1. `SET` / `JSON_SET` / PostgreSQL `ARRAY` 集合 Criteria。
2. 字段级 `DatabaseValueConverter`。
3. 集合元素 codec。
4. 查询结果逻辑字段名返回。

## 1. 什么时候必须迁移

继续使用旧 `CriteriaColumnResolver` 的条件：

1. 只需要把字段名解析成物理列名。
2. Criteria 只使用普通比较、范围、like、in 等不依赖字段类型的操作。
3. 查询结果保留物理列名 Map 可以接受。

必须切到 `TableMeta` 的条件：

1. 运行态字段需要 `contains` / `containsAny` / `containsAll` / `isEmpty` / `isNotEmpty`。
2. 运行态字段是 `SET`、`JSON_SET` 或 PostgreSQL `ARRAY`。
3. 集合元素或字段值需要自定义 `DatabaseValueConverter`。
4. 查询返回需要逻辑字段名，而不是物理列名。

## 2. 迁移前后对照

迁移前：

```java
CriteriaColumnResolver resolver = fieldOrColumn -> switch (fieldOrColumn) {
    case "name" -> "v_name";
    case "tags" -> "csv_tags";
    default -> null;
};

RuntimeTableGateway gateway = new RuntimeTableGateway(
        db,
        db.getDefaultSchemaName(),
        "runtime_user",
        resolver
);
```

迁移后：

```java
TableMeta tableMeta = TableMeta.builder(db.getDefaultSchemaName(), "runtime_user")
        .id("id", "id", ColumnType.VARCHAR, String.class)
        .field("name", "v_name", ColumnType.VARCHAR, String.class)
        .csvSet("tags", "csv_tags", Set.class, String.class)
        .jsonSet("statuses", "json_statuses", Set.class, Status.class)
        .build();

RuntimeTableGateway gateway = RuntimeTableGateway.of(db, tableMeta, statusConverter);
```

说明：

1. `csvSet(...)` 对应 `ColumnType.SET`，底层是 CSV 兼容路径。
2. `jsonSet(...)` 对应 `ColumnType.JSON_SET`，底层是 JSON 字符串数组。
3. 不使用自定义转换器时，可调用 `RuntimeTableGateway.of(db, tableMeta)`。
4. PostgreSQL 原生数组字段可额外使用 `.array("labels", "labels", ColumnType.VARCHAR, List.class, String.class)` 声明。

## 3. 集合字段声明矩阵

| 字段类型 | TableMeta 声明 | 适用数据库 | 典型用途 | 主要限制 |
| --- | --- | --- | --- | --- |
| CSV SET | `.csvSet("tags", "csv_tags", Set.class, String.class)` | MySQL / PostgreSQL | 兼容旧 CSV 集合字段 | 元素不能包含英文逗号 |
| JSON_SET | `.jsonSet("statuses", "json_statuses", Set.class, Status.class)` | MySQL / PostgreSQL | 跨库集合查询、元素可能包含逗号 | 底层按 JSON 字符串数组保存 |
| ARRAY | `.array("labels", "labels", ColumnType.VARCHAR, List.class, String.class)` | PostgreSQL | 原生数组列 | MySQL 不降级为 JSON |

## 4. 查询写法

```java
Criteria enabled = Criteria.of()
        .contains("statuses", Status.ENABLED);

Criteria anyTag = Criteria.of()
        .containsAny("tags", List.of("admin", "operator"));

Criteria allLabels = Criteria.of()
        .containsAll("labels", List.of("red", "blue"));

Criteria emptyTags = Criteria.of()
        .isEmpty("tags");
```

语义约定：

1. `containsAny(field, List.of())` 固定为 false 条件。
2. `containsAll(field, List.of())` 固定为 true 条件。
3. 如果业务把空集合视为“不加条件”，调用方应在组装 Criteria 前跳过该条件。
4. `raw` 和 `SqlSubQuery` 没有字段上下文，不自动执行集合元素 codec。

## 5. 返回 Map 的字段名变化

使用 `TableMeta` 后：

```java
List<Map<String, Object>> rows = gateway.query(criteria, PageRequest.of(1, 20));
Object statuses = rows.getFirst().get("statuses");
```

返回逻辑字段名：`name`、`tags`、`statuses`。

如果使用 `pageQuery`，记录列表从 `PageResult.getRecords()` 读取，记录 Map 的 key 规则相同。

需要物理列名时：

```java
List<Map<String, Object>> rows = gateway.queryColumns(criteria, PageRequest.of(1, 20));
Object rawStatuses = rows.getFirst().get("json_statuses");
```

返回物理列名：`v_name`、`csv_tags`、`json_statuses`。

迁移时需要检查调用方是否直接读取 Map key。若调用方依赖物理列名，改用 `queryColumns` / `listColumns` / `pageQueryColumns`。

## 6. 回归清单

1. 字段名和列名映射：逻辑字段、物理列名都能被正确解析。
2. 写入路径：`insert` / `patchWhere` 能正确编码 `SET` / `JSON_SET` / `ARRAY`。
3. 查询路径：`contains` / `containsAny` / `containsAll` / `isEmpty` / `isNotEmpty` 覆盖关键集合字段。
4. 读回路径：`query/list/pageQuery` 返回逻辑字段，并按字段类型解码。
5. 物理列路径：依赖物理列名的调用改用 `queryColumns/listColumns/pageQueryColumns`。
6. 转换器：自定义 `DatabaseValueConverter` 同时覆盖写入值、查询参数和读回值。
7. 跨库：MySQL/PostgreSQL 分别验证 `SET` / `JSON_SET`；PostgreSQL 单独验证 `ARRAY`。

下一步：迁移完成后，按 [`TESTING_ARCHITECTURE.md`](TESTING_ARCHITECTURE.md) 补齐项目自己的数据库回归用例。

# 性能基线

用于发布前固定记录 `muyun-database` 的性能基线，重点覆盖单表高频操作与并发 upsert 场景。

## 1. 测试环境模板

1. 日期：
2. 版本（git commit/tag）：
3. 数据库（MySQL/PostgreSQL）：
4. 机器规格（CPU/内存）：
5. JVM 参数：

## 2. 场景与指标

### 2.1 单表 CRUD 与查询（EntityDao）

#### MySQL

| 场景 | 并发 | 吞吐 (ops/s) | P50 (ms) | P95 (ms) | P99 (ms) |
| --- | --- | ---: | ---: | ---: | ---: |
| insert | 1 |  |  |  |  |
| update | 1 |  |  |  |  |
| query | 1 |  |  |  |  |
| pageQuery | 1 |  |  |  |  |
| count | 1 |  |  |  |  |

#### PostgreSQL

| 场景 | 并发 | 吞吐 (ops/s) | P50 (ms) | P95 (ms) | P99 (ms) |
| --- | --- | ---: | ---: | ---: | ---: |
| insert | 1 |  |  |  |  |
| update | 1 |  |  |  |  |
| query | 1 |  |  |  |  |
| pageQuery | 1 |  |  |  |  |
| count | 1 |  |  |  |  |

### 2.2 并发 atomic upsert

#### MySQL

| 场景 | 并发 | 总请求 | 错误数 | 吞吐 (ops/s) | P95 (ms) | P99 (ms) |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| same-pk contention | 12 |  |  |  |  |  |
| high contention | 24 |  |  |  |  |  |

#### PostgreSQL

| 场景 | 并发 | 总请求 | 错误数 | 吞吐 (ops/s) | P95 (ms) | P99 (ms) |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| same-pk contention | 12 |  |  |  |  |  |
| high contention | 24 |  |  |  |  |  |

## 3. 语义校验

1. same-pk 并发写后，目标主键仅保留单行。
2. 无数据库唯一约束异常泄漏到业务层（或在预期范围内有清晰处理）。
3. 回归执行期间无连接泄漏。

## 4. 发布门槛建议

1. 与上个稳定版本相比：
   - 吞吐下降不超过 10%
   - P95/P99 回归不超过 15%
2. 并发语义校验必须全部通过。

## 5. 结果归档

1. 报告文件命名：`perf-baseline-YYYYMMDD.md`
2. 存放路径：`docs/perf-history/`

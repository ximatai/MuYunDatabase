# MuYun-Database

一个方便创建、修改表结构的工具库，依赖单纯，目前仅依赖 `jdbi` 。同时支持 `MySQL`、`Postgres`。
可以方便的结合在 `Spring` 、`Quarkus` 等生态的项目里。
既是 [MuYun](https://github.com/ximatai/MuYun) 的一部分，也可以单独使用。

### 最新版本及兼容性

* 版本 `2.26.+` 兼容 `Java21` 及以上
* 版本 `1.26.+` 兼容 `Java8`

### 包说明

* `muyun-database-core` 纯 `Java` 项目，不依赖任何框架，主要用于定义标准接口和业务逻辑
* `muyun-database-jdbi` 基于 `jdbi` 具体实现，通常项目依赖这个包即可
* `muyun-database-jdbi-jdk8` 基于 `jdbi` 具体实现，兼容 `Java8`，仅存在于 `jdbi-jdk8` 分支
* `muyun-database-test` 准本用来跑单元测试的包，实际项目不需要依赖

## 快速开始

1. 添加依赖
    * gradle
         ```groovy
       dependencies {
           implementation("net.ximatai.muyun.database:muyun-database-jdbi:2.26.+") // 兼容 Java21
           implementation("net.ximatai.muyun.database:muyun-database-jdbi-jdk8:1.26.+") // 兼容 Java8
       }
         ```
    * maven
       ```xml
        <!-- Java 21 兼容版本  -->
        <dependency>
           <groupId>net.ximatai.muyun.database</groupId>
           <artifactId>muyun-database-jdbi</artifactId>
           <version>2.26.+</version>
        </dependency>
        <!-- Java 8 兼容版本  -->
        <dependency>
           <groupId>net.ximatai.muyun.database</groupId>
            <artifactId>muyun-database-jdbi-jdk8</artifactId>
            <version>1.26.+</version>
        </dependency>
       ```

2. 标准用法
    * 准备工作
      ```java
      // 创建数据源 (Spring 项目正常不需要自己创建，这段主要是演示原始用法)
      HikariConfig config = new HikariConfig();
      config.setJdbcUrl(getContainer().getJdbcUrl());
      config.setUsername(getContainer().getUsername());
      config.setPassword(getContainer().getPassword());
      config.setDriverClassName(getContainer().getDriverClassName());
      DataSource dataSource = new HikariDataSource(config);

      // 创建 Jdbi 对象，当然也可以用其他参数的方法创建 Jdbi ，不过通常来说推荐基于数据源 DataSource 的方法 
      Jdbi jdbi = Jdbi.create(dataSource).setSqlLogger(new Slf4JSqlLogger());

      // 本项目需要的两个对象
      JdbiMetaDataLoader loader = new JdbiMetaDataLoader(jdbi);
      JdbiDatabaseOperations db = new JdbiDatabaseOperations(jdbi, loader);
      ```
    * 创建表结构（方法1：命令式）
      ```java
      // 获取数据库信息
      DBInfo info = loader.getDBInfo();

      TableWrapper basic = TableWrapper.withName("basic")
                  .setPrimaryKey(getPrimaryKey())
                  .setComment("测试表")
                  .addColumn(Column.of("v_name").setLength(20).setIndexed().setComment("名称").setDefaultValue("test"))
                  .addColumn(Column.of("i_age").setComment("年龄"))
                  .addColumn(Column.of("n_price").setPrecision(10).setScale(2))
                  .addColumn("b_flag")
                  .addColumn("d_date")
                  .addColumn(Column.of("t_create").setDefaultValueAny("CURRENT_TIMESTAMP"));

      new TableBuilder(db).build(basic);

      DBTable table = info.getDefaultSchema().getTable("basic");
      assertNotNull(table);
      ```
    * 创建表结构（方法2：声明式）
      ```java
      @Table(name = "test_entity")
      @CompositeIndex(columns = {"name", "age"}, unique = true)
      @CompositeIndex(columns = {"name", "flag"})
      public class TestEntityBase {
      
          @Column(length = 20, comment = "名称", defaultVal = @Default(varchar = "test_name"))
          public String name;
      
          @Indexed(unique = true)
          @Column(comment = "学号")
          public int code;
      
          @Column(comment = "年龄", defaultVal = @Default(number = 12))
          public int age;
      
          @Column(precision = 10, scale = 2, defaultVal = @Default(decimal = 1.23))
          public double price;
      
          @Column(precision = 10, scale = 2, defaultVal = @Default(decimal = 1.23))
          public float price2;
      
          @Indexed
          @Column(defaultVal = @Default(trueVal = true))
          public boolean flag;
      
          @Column(name = "create_time", defaultVal = @Default(express = "CURRENT_TIMESTAMP"))
          public Date creatTime;
      
      }


      new TableBuilder(db).build(basic);

      DBTable table = info.getDefaultSchema().getTable("basic");
      assertNotNull(table);
      ```
    * 插入与查询数据
      ```java
       Map body = Map.of("v_name", "test_name",
                   "i_age", 5,
                   "b_flag", true,
                   "n_price", 10.2,
                   "d_date", "2024-01-01"
       );

       String id = db.insertItem("basic", body);
       assertNotNull(id);

       Map<String, Object> item = db.getItem("basic", id);

       assertNotNull(item);
       assertEquals("test_name", item.get("v_name"));
       assertEquals(5, item.get("i_age"));
       assertEquals(true, item.get("b_flag"));
       assertEquals(0, BigDecimal.valueOf(10.2).compareTo((BigDecimal) item.get("n_price")));
       assertEquals(LocalDate.of(2024, 1, 1), ((Date) item.get("d_date")).toLocalDate());  
      ```
    * 整合进 `Spring`
      ```java
      import net.ximatai.muyun.database.core.IDatabaseOperations;
      import net.ximatai.muyun.database.jdbi.JdbiDatabaseOperations;
      import net.ximatai.muyun.database.jdbi.JdbiMetaDataLoader;
      import org.jdbi.v3.core.Jdbi;
      import org.jdbi.v3.core.statement.Slf4JSqlLogger;
      import org.springframework.beans.factory.annotation.Autowired;
      import org.springframework.context.annotation.Bean;
      import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
      import org.springframework.stereotype.Service;
      
      import javax.sql.DataSource;

      @Service
      public class DBProducer {
      
          @Autowired
          DataSource dataSource;
      
          @Bean
          public IDatabaseOperations createDB() {
              TransactionAwareDataSourceProxy proxy = new TransactionAwareDataSourceProxy(dataSource);
      
              Jdbi jdbi = Jdbi.create(proxy)
                      .setSqlLogger(new Slf4JSqlLogger());
      
              JdbiMetaDataLoader loader = new JdbiMetaDataLoader(jdbi);
              JdbiDatabaseOperations db = new JdbiDatabaseOperations(jdbi, loader);
      
              return db;
          }
      
      }
      ```
    * 更多用法可以查看单元测试用例 [MuYunDatabaseBaseTest](https://github.com/ximatai/MuYunDatabase/blob/master/muyun-database-test/src/test/java/net/ximatai/muyun/database/MuYunDatabaseBaseTest.java)

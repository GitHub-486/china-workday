# china-workday Starter 重构设计

## 1. 目标

让外部项目以 Maven 依赖方式引用 china-workday 时，只需配置自身 DataSource，即可自动装配 `WorkdayJudgeService` Bean，无需手动写 Repository 实现或关心数据库方言差异。

## 2. 范围

- **纳入**：Repository 层多方言适配、AutoConfiguration 注解、Bean 自动装配、Schema 初始化
- **不纳入**：CrawlService/Controller/Scheduler/Admin 管理等组件的拆分（这些保留在现有应用中，引用方按需启用）
- **不纳入**：Maven 模块拆分（保持单模块，通过 Spring Boot AutoConfiguration 机制实现开箱即用，降低发布复杂度）

## 3. 架构总览

```
引用方项目
  │
  ├── pom.xml: 添加 china-workday 依赖
  ├── application.yml: 配置 datasource（MySQL/H2/PG 均可）
  └── 代码中 @Autowired WorkdayJudgeService
         │
         ▼
china-workday.jar
  ├── ChinaWorkdayAutoConfiguration
  │     └── HolidayRepositoryFactory
  │           ├── 检测 DataSource URL 前缀
  │           ├── → PostgreSQL → HolidayPostgresRepository
  │           ├── → H2          → HolidayH2Repository
  │           └── → MySQL       → HolidayMysqlRepository
  ├── WorkdayJudgeService     （核心判断逻辑，不变）
  ├── HolidayService           （查询聚合，不变）
  ├── HolidayRecord / DayType  （领域模型，不变）
  └── schema-{db}.sql          （按方言选加载）
```

## 4. Repository 方言适配

### 4.1 接口不变

`HolidayRepository` 和 `CrawlUrlRepository` 两个接口保持原样。引用方只用 `HolidayRepository`（通常不需要 `CrawlUrlRepository`）。

### 4.2 实现类

| 实现类 | 适用数据库 | 关键差异 |
|--------|-----------|---------|
| `HolidayPostgresRepository` | PostgreSQL | `BIGSERIAL` 主键，`SELECT COUNT(*) ... WHERE` + UPDATE/INSERT upsert（同现有 `HolidayJdbcRepository`） |
| `HolidayH2Repository` | H2 | `IDENTITY` 替代 `BIGSERIAL`，其他 SQL 同 PG（H2 PG 兼容模式） |
| `HolidayMysqlRepository` | MySQL | `AUTO_INCREMENT`，upsert 用 `INSERT ... ON DUPLICATE KEY UPDATE` |

同样对应三个 `CrawlUrl*Repository` 实现（H2/MySQL 用 `INSERT IGNORE` 而非 try-catch 幂等）。

### 4.3 工厂类

```java
@Component
public class HolidayRepositoryFactory {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public HolidayRepositoryFactory(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public HolidayRepository createHolidayRepository() {
        String url = getJdbcUrl();
        if (url.contains(":postgresql:")) return new HolidayPostgresRepository(jdbcTemplate);
        if (url.contains(":h2:"))         return new HolidayH2Repository(jdbcTemplate);
        if (url.contains(":mysql:"))      return new HolidayMysqlRepository(jdbcTemplate);
        throw new IllegalStateException("不支持的数据库类型，当前仅支持 PostgreSQL / H2 / MySQL。JDBC URL: " + url);
    }

    public CrawlUrlRepository createCrawlUrlRepository() {
        String url = getJdbcUrl();
        if (url.contains(":postgresql:")) return new CrawlUrlPostgresRepository(jdbcTemplate);
        if (url.contains(":h2:"))         return new CrawlUrlH2Repository(jdbcTemplate);
        if (url.contains(":mysql:"))      return new CrawlUrlMysqlRepository(jdbcTemplate);
        throw new IllegalStateException("不支持的数据库类型，当前仅支持 PostgreSQL / H2 / MySQL。JDBC URL: " + url);
    }

    private String getJdbcUrl() {
        try (var conn = dataSource.getConnection()) {
            return conn.getMetaData().getURL();
        } catch (SQLException e) {
            throw new RuntimeException("无法获取 DataSource JDBC URL", e);
        }
    }
}
```

## 5. AutoConfiguration

### 5.1 @EnableChinaWorkday 注解

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(ChinaWorkdayAutoConfiguration.class)
public @interface EnableChinaWorkday {
}
```

### 5.2 ChinaWorkdayAutoConfiguration

```java
@Configuration
@ConditionalOnClass(HolidayRepository.class)
@EnableConfigurationProperties({AppProperties.class, CrawlProperties.class})
public class ChinaWorkdayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HolidayRepository.class)
    public HolidayRepository holidayRepository(DataSource dataSource) {
        return new HolidayRepositoryFactory(dataSource).createHolidayRepository();
    }

    @Bean
    @ConditionalOnMissingBean(CrawlUrlRepository.class)
    @ConditionalOnProperty(prefix = "china-workday.crawl", name = "enabled", havingValue = "true", matchIfMissing = false)
    public CrawlUrlRepository crawlUrlRepository(DataSource dataSource) {
        return new HolidayRepositoryFactory(dataSource).createCrawlUrlRepository();
    }

    @Bean
    @ConditionalOnMissingBean(WorkdayJudgeService.class)
    public WorkdayJudgeService workdayJudgeService(HolidayRepository repository) {
        return new WorkdayJudgeService(repository);
    }

    @Bean
    @ConditionalOnMissingBean(HolidayService.class)
    public HolidayService holidayService(HolidayRepository repository, WorkdayJudgeService workdayJudgeService) {
        return new HolidayService(repository, workdayJudgeService);
    }

    @Bean
    @ConditionalOnMissingBean(JsonExportService.class)
    public JsonExportService jsonExportService(HolidayRepository repository) {
        return new JsonExportService(repository);
    }
}
```

### 5.3 META-INF/spring.factories（Spring Boot 自动发现）

```properties
# Auto Configuration
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.ict.workday.config.ChinaWorkdayAutoConfiguration
```

## 6. Schema 初始化

### 6.1 文件规划

| 文件 | 用途 |
|------|------|
| `schema.sql`（现有） | PostgreSQL 专用，BIGSERIAL 语法 |
| `schema-h2.sql` | H2 兼容，IDENTITY 替代 BIGSERIAL，其他同 PG |
| `schema-mysql.sql` | MySQL 兼容，AUTO_INCREMENT，ENGINE=InnoDB |

### 6.2 自动初始化策略

在 `ChinaWorkdayAutoConfiguration` 中增加 `DataSourceInitializer` Bean，启动时自动执行建表 DDL：

```java
@Bean
public DataSourceInitializer chinaWorkdaySchemaInitializer(DataSource dataSource) {
    DataSourceInitializer initializer = new DataSourceInitializer();
    initializer.setDataSource(dataSource);
    initializer.setEnabled(true);
    
    String url = HolidayRepositoryFactory.getJdbcUrl(dataSource);
    Resource script;
    if (url.contains(":postgresql:")) {
        script = new ClassPathResource("schema-postgres.sql");
    } else if (url.contains(":h2:")) {
        script = new ClassPathResource("schema-h2.sql");
    } else if (url.contains(":mysql:")) {
        script = new ClassPathResource("schema-mysql.sql");
    } else {
        return initializer; // 不初始化
    }
    
    var creation = new DatabasePopulatorUtils();
    ResourceDatabasePopulator populator = new ResourceDatabasePopulator(script);
    populator.setSeparator(";");
    populator.setContinueOnError(false);
    initializer.setDatabasePopulator(populator);
    
    return initializer;
}
```

通过 `@ConditionalOnProperty(prefix = "china-workday", name = "schema-init", havingValue = "true", matchIfMissing = true)` 控制开关，引用方可设置 `china-workday.schema-init=false` 关闭自动建表（例如使用 Flyway 时）。

## 7. 引用方使用方式

### Maven 依赖

```xml
<dependency>
    <groupId>io.github.github-486</groupId>
    <artifactId>china-workday</artifactId>
    <version>1.0.2</version>
</dependency>
```

### application.yml（引用方）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
    username: root
    password: secret

# 关闭自动建表（如使用 Flyway 时）
# china-workday:
#   schema-init: false
```

### Java 代码

```java
@SpringBootApplication
@EnableChinaWorkday
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

@Component
public class MyService {
    @Autowired
    private WorkdayJudgeService workdayJudgeService;

    public void check() {
        boolean isWorkday = workdayJudgeService.isWorkday(LocalDate.of(2026, 1, 1));
        System.out.println(isWorkday ? "上班" : "放假");
    }
}
```

## 8. 现有应用不受影响

- `@SpringBootApplication` 会扫描到 `ChinaWorkdayAutoConfiguration`（在 `com.ict.workday.config` 包下），自动装配
- 现有 `HolidayJdbcRepository` 有 `@Repository` 注解，会被优先注入，`@ConditionalOnMissingBean` 不会覆盖
- 所有现有测试保持不变
- Controller / Scheduler / CrawlService 等不受影响

## 9. 安全约束自检

| 规则 | 检查结果 |
|------|---------|
| SEC-01 默认拒绝 | ✅ 保持，现有 Token 拦截器不变 |
| SEC-02 最小返回 | ✅ ApiResponse 不变 |
| SEC-03 所有权校验 | ✅ 不涉及 |
| SEC-04 最小权限 | ✅ DB 连接使用引用方配置的凭据 |
| SEC-05 输入不信任 | ✅ 保持 |
| SEC-06 参数化查询 | ✅ 所有 Repository 实现使用参数化查询 |
| SEC-08 SSRF | ✅ 不涉及本次改动 |

## 10. 测试策略

| 测试 | 方式 |
|------|------|
| HolidayRepositoryFactory | Mock DataSource，验证 URL 前缀分发正确 |
| HolidayPostgresRepository | 已有 Coverage（当前 HolidayJdbcRepository 测试） |
| HolidayH2Repository | 已有覆盖（测试用的就是 H2 PG 兼容模式） |
| HolidayMysqlRepository | 单元测试 Mock JdbcTemplate |
| AutoConfiguration | `@SpringBootTest` 验证 Bean 自动装配 |

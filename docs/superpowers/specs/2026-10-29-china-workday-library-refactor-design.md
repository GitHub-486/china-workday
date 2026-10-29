# china-workday 库化重构设计

## 背景

china-workday 原设计为独立 Spring Boot 服务（可执行 jar），被其他项目通过 Maven 依赖引入时存在以下问题：

1. 自动配置通过 `AutoConfiguration.imports` 注册，引用方无法控制是否启用
2. Flyway 迁移文件路径冲突——PG 和 H2 两个 V1 都在 default `db/migration/` 下，Flyway 扫描到两个 V1 报错
3. `flyway-core` 是 `compile` scope，被传递到引用方
4. 配置项部分无默认值，引用方不配置可能启动失败

## 目标

- 引用方显式启用 china-workday（`@EnableChinaWorkday`）
- Flyway 与引用方完全隔离（独立迁移表）
- 引用方不感知 flyway-core 依赖
- 所有配置项提供合理默认值

## 改动清单

### 1. 去掉自动配置注册

- 删除 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `EnableChinaWorkday.java` 已存在（`@Import(ChinaWorkdayAutoConfiguration.class)`），引用方使用 `@EnableChinaWorkday` 启用

### 2. Flyway 隔离

所有配置文件增加 `spring.flyway.table: china_workday_flyway_schema`：

- `src/main/resources/application.yml`
- `src/main/resources/application-docker.yml`
- `src/test/resources/application.yml`

Flyway 迁移路径不变（`db/migration/pg/` 和 `db/migration/h2/`），但使用独立的迁移历史表。

### 3. flyway-core scope 改为 runtime

`pom.xml` 中 `flyway-core` 的 scope 从 `compile`（默认）改为 `runtime`，不再传递到引用方。

### 4. flyway-database-postgresql 和 flyway-database-h2 移除

依赖 `flyway-core` 本身支持 PostgreSQL 和 H2，不需要额外方言包（之前已验证通过）。

### 5. 配置项提供默认值

确保所有配置项有合理的默认启动值。

#### 当前默认值情况

| 配置项 | 当前 | 修改后 |
|--------|------|--------|
| `china-workday.app.api-token` | `wk`（已有默认） | 不变 |
| `china-workday.crawl.enabled` | `true`（已有默认） | 不变 |
| `china-workday.crawl.on-startup` | `true`（已有默认） | 不变 |
| `china-workday.crawl.search-url` | 环境变量 `${...}`（有默认值） | 不变 |
| `china-workday.crawl.cron` | `0 0 8 * * *`（已有默认） | 不变 |

已有默认值，不需要额外修改。

### 6. 测试调整

- 两个 Flyway 迁移文件目录同时存在（pg + h2），测试 `application.yml` 指向 `db/migration/h2`
- 测试使用 `@EnableChinaWorkday` 注解启用自动配置（已在 `HolidayControllerTest` 中根据情况添加，或通过 `@SpringBootTest` 扫描）

## 安全审查

- SEC-01（认证）：无影响。ApiTokenInterceptor 保留，引用方需自行配置 `api-token`
- SEC-04（输出过滤）：无影响。`@EnableChinaWorkday` 不改变任何输出行为
- SEC-05（凭据）：`api-token` 已有环境变量注入默认值，未硬编码

# china-workday 节假日/工作日公共服务 设计文档

- 日期：2026-10-22
- 状态：待用户审阅
- 模块坐标：`ict:china-workday:1.0.0`，包名 `ict.china.workday`

## 1. 背景与目标

提供一个**可复用、可独立部署**的中国节假日/工作日判断公共服务：

1. 从**国务院政策文件库**抓取「部分节假日安排的通知」，解析放假与调休日期。
2. 节假日数据入库（PostgreSQL），并支持**按年份导出 JSON**（供引用方放入自身 `resources` 离线使用）。
3. 暴露 HTTP 接口：**传入年月日判断是否上班日**。
4. 可被其它项目作为 **Maven 依赖**引用，也可独立部署（Docker）。
5. 发布到**阿里云效 Packages** Maven 公共仓库，代码托管在 **Gitee**。

## 2. 命名与坐标

| 项 | 值 |
|---|---|
| groupId | `ict` |
| artifactId | `china-workday` |
| version | `1.0.0` |
| 包名 | `ict.china.workday` |
| 发布仓库 | 阿里云效 Packages（`distributionManagement`，待填认证） |
| 代码仓库 | Gitee |

## 3. 技术栈（对齐 rushi-aci）

- Spring Boot 3.2.5 / Java 17
- spring-boot-starter-web + log4j2
- spring-boot-starter-jdbc + PostgreSQL 驱动
- jsoup（HTML 抓取解析）
- jackson-databind（JSON 序列化/反序列化）
- H2（**测试**用，本地快速验证，不用于生产）

## 4. 架构

```
china-workday/
├── pom.xml
├── deploy/
│   ├── Dockerfile
│   ├── docker-compose.yml          # holiday-postgres + china-workday
│   └── db/
│       ├── schema.sql              # 由 application.yml 的 sql.init 执行
│       └── init-user-db.sh         # 建库建账号（复用 rushi-aci 模式）
└── src/main/
    ├── java/ict/china/workday/
    │   ├── ChinaWorkdayApplication.java   # @SpringBootApplication @ConfigurationPropertiesScan @EnableScheduling
    │   ├── config/
    │   │   ├── AppProperties.java         # api-token、数据源无关配置
    │   │   └── CrawlProperties.java       # 抓取开关、列表页 URL、解析规则、定时 cron
    │   ├── client/
    │   │   └── GovCrawlClient.java        # 抓取列表页 + 详情页，jsoup 解析（含 SSRF/超时/重定向防护）
    │   ├── domain/
    │   │   ├── DayType.java               # HOLIDAY(放假) / WORKDAY(调休上班)
    │   │   └── HolidayRecord.java         # 实体
    │   ├── repository/
    │   │   └── HolidayRepository.java     # JdbcTemplate，幂等 upsert / 查询
    │   ├── service/
    │   │   ├── HolidayService.java        # 查询聚合
    │   │   ├── WorkdayJudgeService.java   # 核心：上班日判断
    │   │   ├── CrawlService.java          # 列表页监控 + 详情解析入库
    │   │   └── JsonExportService.java     # 按年份导出 JSON
    │   ├── scheduler/
    │   │   └── CrawlScheduler.java        # 启动抓取 + 定时监控
    │   ├── controller/
    │   │   ├── HolidayController.java     # 查询/判断/导出接口
    │   │   └── AdminController.java       # 手动触发抓取 + 内置管理页
    │   └── web/
    │       └── static/                     # 内置简单管理页面(HTML/JS/CSS)
    └── resources/
        ├── application.yml
        ├── application-dev.yml            # 本地连 PG
        ├── application-docker.yml        # 容器连 compose 内的库
        └── static/                         # 与 web/static 对应（Spring 静态资源）
```

## 5. 数据模型

表 `holiday`（`holiday_date` 唯一，用于幂等 upsert）：

| 字段 | 类型 | 说明 | 敏感等级 |
|---|---|---|---|
| id | BIGSERIAL PK | 主键 | L1 |
| holiday_date | DATE UNIQUE | 节假日/调休日期 | L1 |
| holiday_name | VARCHAR(64) | 名称，如「春节」「国庆节」 | L1 |
| day_type | VARCHAR(16) | `HOLIDAY` 放假休息 / `WORKDAY` 调休上班 | L1 |
| year | INT | 所属年份（冗余，便于按年导出） | L1 |
| source | VARCHAR(32) | 数据来源，如 `gov.cn` | L1 |
| created_at | TIMESTAMP | 创建/更新时间 | L1 |

索引：`(year)`、`(holiday_date)`。

## 6. 核心业务：上班日判断

**定义**：某日期 `d` 是上班日 ⟺
```
( d ∈ 周一~周五 且 d ∉ HOLIDAY )
或 ( d ∈ 周六/周日 且 d ∈ WORKDAY 调休上班 )
```

- 正常周末（无调休）→ 非上班日。
- 法定节假日落在工作日 → 非上班日。
- 调休上班的周末 → 上班日。

**实现**（`WorkdayJudgeService.isWorkday(LocalDate)`）：
1. 查库中该日期的 `holiday` 记录；
2. 若无记录：返回 `!(周六或周日)`；
3. 若有 `WORKDAY`：返回 `true`；
4. 若有 `HOLIDAY`：返回 `false`。

口径与国务院通知一致（如 2026 春节「2/15–2/23 放假，2/14、2/28 上班」）。

## 7. 抓取设计（国务院单一渠道）

**触发**：应用启动时 + 定时任务（默认每日 08:00，可配 cron）扫描。

**流程**：
1. 抓列表页 `https://www.gov.cn/zhengce/zhengceku/`，定位标题含「部分节假日安排的通知」的 `<a>` 链接。
2. 对**新增/未入库的 URL** 抓取详情页，jsoup 解析放假与调休的日期区间。
3. 将解析结果逐条转为 HOLIDAY / WORKDAY 记录，批量幂等 upsert 入库。
4. **幂等**：按 `holiday_date` 合并；URL 记录去重，避免重复抓取。
5. 失败处理：单次抓取失败不影响已入库数据，记录日志，下轮重试。

**解析规则**（要点）：
- 识别「X月X日至X月X日放假调休，共N天」→ 区间内所有日期为 `HOLIDAY`。
- 识别「X月X日上班」→ 该日期为 `WORKDAY`。
- 可针对不同节日名（元旦、春节、清明、劳动节、端午、中秋、国庆）分别解析。

## 8. 对外接口

统一返回 `{ code, message, data }`。鉴权沿用 rushi-aci 的 `X-API-Token` Header。

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| GET | `/api/holiday/is-workday?date=2026-02-14` | **核心**：判断是否上班日 | 公开（数据为公开节假日，L1）|
| GET | `/api/holiday/month?year=2026&month=2` | 某月节假日明细 | 公开 |
| GET | `/api/holiday/year?year=2026` | 某年全部节假日/调休 | 公开 |
| GET | `/api/holiday/export?year=2026` | 导出 `{year}.json`（引用方放 resource）| Token |
| POST | `/api/admin/crawl` | 手动触发抓取 | Token |
| GET | `/`（管理页） | 查看数据 / 手动刷新 / 下载 json | Token（页面加载数据走 Token）|

**参数校验**：`date` 必须 `yyyy-MM-dd`；`year` 4 位数字；`month` 1–12；非法返回 400 且不暴露内部结构。

**`is-workday` 响应**：
```json
{ "code": 200, "message": "ok",
  "data": { "date": "2026-02-14", "isWorkday": true, "dayType": "WORKDAY", "holidayName": "春节调休" } }
```

## 9. 内置管理页（前端）

后端内置简单静态页面（不分离，部署简单）：
- 表格展示指定年/月节假日与调休。
- 日期工作/休息状态试算（输入日期即时判断）。
- 「手动抓取」按钮（调 Token 接口）。
- 「导出 JSON」下载按钮。

技术：原生 HTML + CSS + 少量 JS，无构建。静态资源置于 `src/main/resources/static/`。

## 10. 部署（Docker + PostgreSQL）

- `docker-compose.yml`：`holiday-postgres` + `china-workday` 两服务，`depends_on` + healthcheck，复用 rushi-aci 的 .env 注入、db init 模式。
- 多 profile：本地 `dev`、容器 `docker`。
- 环境变量：`DB_NAME/DB_USER/DB_PASSWORD`、`CHINA_WORKDAY_API_TOKEN`、抓取开关等。

## 11. Maven 发布（阿里云效 Packages）

- `pom.xml` 配置 `<distributionManagement>`（repository 指向云效 Packages，id/url/认证在 `~/.m2/settings.xml` 填）。
- 发布命令 `mvn clean deploy`。
- 文档给出云效仓库搭建与 `settings.xml` 认证配置指引（仓库地址/凭据我不能代填，用户侧配置）。

## 12. 安全需求（对应 SEC 宪法）

参考工作流：本项目属「对外 HTTP 服务 + 外部 URL 抓取」，主要映射：SEC-01 认证、SEC-03 输入校验、SEC-05 凭据、SEC-08 SSRF、SEC-12 重定向、SEC-13 资源上限、SEC-15 日志脱敏。

### 12.1 信任边界图
- **不可信边界**：客户端 HTTP 请求（i̳s-workday/查询/管理接口）；外部抓取源 `gov.cn` 的页面（不可信页面内容、链接）。
- **内部信任域**：Service → Repository → PostgreSQL。
- **跨边界校验点**：接口入参统一声明式校验；抓取 URL/重定向需经 SSRF 防护；页面内容按 jsoup 白名单解析（不执行脚本）。

### 12.2 数据流敏感标注
- 节假日数据：**L1 公开**（可返回、可日志）。
- `api-token`：**L4 凭据**（不返回、不日志、环境变量注入）。
- 抓取到的外部页面内容：仅解析出日期字段入库，不存原始 HTML。

### 12.3 外部依赖风险
| 依赖 | 供应链风险 | 数据外泄 | 可用性 |
|---|---|---|---|
| jsoup | 生态成熟、维护中，锁定版本 | 仅解析 gov.cn 页面，不外传 | — |
| Spring Boot / PG | 常规 | — | — |
| gov.cn 抓取源 | — | — | 单点：gov.cn 不可达时抓取失败（不影响已入库数据，有降级）|

### 12.4 认证授权
- 查询类接口公开（返回 L1 公开数据）。
- 写/管理/导出接口：`X-API-Token`，配置缺失时**快速失败拒绝访问**（不静默放行）。
- 无持久化会话/RBAC 需求（单服务简单场景），token 仅做能力门禁。

### 12.5 错误处理与日志策略
- 错误响应统一 `{code, message}`，不暴露堆栈/类名/内部 SQL。
- 日志字段白名单：记录日期、URL 摘要、操作结果；**不记录 api-token**；异常不打印堆栈抛给前端。
- 抓取失败仅记日志，不中断服务。

### 12.6 威胁建模摘要（STRIDE）
- Spoofing：管理/导出接口 token 校验。
- Tampering：抓取内容仅白名单字段入库；外部链接经 SSRF/重定向校验。
- Repudiation：抓取与手动操作记审计日志（含时间、来源）。
- Information Disclosure：仅返回 L1 公开字段；token 永不出现在响应/日志。
- DoS：`export`/`month`/`year` 限定年份范围与分页；抓取接口限频（简单实现，防止被频繁触发）。
- Elevation of Privilege：写接口与读接口分离，写/导出需 token。

## 13. 测试策略

- **单元**：`WorkdayJudgeService`（给定 HOLIDAY/WORKDAY/普通周末/普通工作日 组合断言）；解析器对样例节假日文本断言生成记录集合。
- **集成**：H2 或测试 PG，验证 `is-workday` 接口正/反/边界用例。
- **验收用例**（SEC-PRD-6 风格）：
  - 正向：2026-02-14（调休上班）→ isWorkday=true；2026-02-15（春节放假）→ false；普通周四 → true；普通周日 → false。
  - 反向：未带/带错 token 访问管理接口 → 401/403；非法日期格式 → 400。
  - 边界：`month=13` → 400；空参数 → 400；跨年导出 `year=2026` 只含该年数据。

## 14. 里程碑

1. 脚手架 + 数据模型 + 数据库接入（对齐 rushi-aci）。
2. 上班日判断核心 + 查询/判断接口 + 测试。
3. 国务院抓取（列表监控 + 详情解析入库）+ 手动触发接口。
4. 导出 JSON + 内置管理页。
5. Docker 部署 + 阿里云效 Packages 发布 + 文档。

## 15. 待用户确认/补充

- 阿里云效 Packages 仓库的实际 `id/url`（用于 `<distributionManagement>`）与 `settings.xml` 认证信息。
- gov.cn 列表页实际 DOM 结构（实现时先手工验证再固化为 jsoup CSS 选择器，可能需迭代调整）。

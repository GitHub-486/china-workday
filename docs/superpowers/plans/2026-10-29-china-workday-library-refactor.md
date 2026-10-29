# china-workday 库化重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使 china-workday 作为 Maven 依赖被其他项目引用时 Flyway 不冲突、引用方可控启用

**Architecture:** 去掉自动配置注册 → 改为 `@EnableChinaWorkday` 显式启用 → Flyway 独立迁移表 → flyway-core 改为 runtime scope

**Tech Stack:** Spring Boot 3.2.5, Flyway 9.22.3, Maven

---

### Task 1: 去掉自动配置注册

**Files:**
- Delete: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: 删除 AutoConfiguration.imports 文件**

```bash
git rm src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

- [ ] **Step 2: 确认 EnableChinaWorkday 注解已存在（检查无需修改）**

确认 `src/main/java/com/ict/workday/config/EnableChinaWorkday.java` 内容正确：

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ChinaWorkdayAutoConfiguration.class)
public @interface EnableChinaWorkday {
}
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "refactor: 去掉 AutoConfiguration.imports，改为 @EnableChinaWorkday 显式启用"
```

---

### Task 2: Flyway 独立迁移表

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-docker.yml`
- Modify: `src/test/resources/application.yml`

- [ ] **Step 1: main application.yml 增加 flyway.table**

```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration/pg
    table: china_workday_flyway_schema
```

- [ ] **Step 2: application-docker.yml 增加 flyway.table**

`application-docker.yml` 没有 `spring.flyway.*` 配置（继承主配置），继承会自动生效，不需要修改。

- [ ] **Step 3: 测试 application.yml 增加 flyway.table**

```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration/h2
    table: china_workday_flyway_schema
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: Flyway 使用独立迁移表 china_workday_flyway_schema"
```

---

### Task 3: flyway-core scope 改为 runtime

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: pom.xml 中添加 runtime scope**

```xml
        <!-- Flyway: 数据库版本控制 -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
            <scope>runtime</scope>
        </dependency>
```

- [ ] **Step 2: 确认构建通过**

```bash
mvn compile -DskipTests -Dgpg.skip=true
```

- [ ] **Step 3: 运行测试确认通过**

```bash
mvn test -Dgpg.skip=true
```
Expected: 46 tests, 0 failures

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: flyway-core scope 改为 runtime，不传递到引用方"
```

---

### Task 4: 更新 README 文档

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 在"对外接口"章节前增加"作为依赖引用"章节**

添加以下内容：

```markdown
## 作为 Maven 依赖引用

其他 Spring Boot 项目可通过 Maven 依赖引入 china-workday：

```xml
<dependency>
    <groupId>io.github.github-486</groupId>
    <artifactId>china-workday</artifactId>
    <version>1.1.2</version>
</dependency>
```

在启动类上添加 `@EnableChinaWorkday` 启用：

```java
@SpringBootApplication
@EnableChinaWorkday
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

引用方可通过 `@Autowired` 注入 `WorkdayJudgeService` 使用上班日判断功能：

```java
@Autowired
private WorkdayJudgeService workdayJudgeService;

boolean workday = workdayJudgeService.isWorkday(LocalDate.of(2026, 10, 1));
```

> china-workday 使用独立的 Flyway 迁移历史表 `china_workday_flyway_schema`，与引用方的数据库迁移完全隔离。

```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "docs: 补充作为 Maven 依赖引用的文档"
```

---

### Task 5: 安装到本地仓库并验证

- [ ] **Step 1: 安装到本地仓库**

```bash
mvn install -DskipTests -Dgpg.skip=true
```
Expected: BUILD SUCCESS

- [ ] **Step 2: 通知用户到 rushi-aci 项目添加 `@EnableChinaWorkday` 注解**

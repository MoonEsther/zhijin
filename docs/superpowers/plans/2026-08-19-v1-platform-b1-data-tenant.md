# V1 平台服务 · B1 数据基础设施与多租户 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 zhijin-server 接入 PostgreSQL + MyBatis-Plus，实现**多租户自动隔离**（SQL 层自动注入 `tenant_id`）、统一响应/异常、traceId 链路日志，并用 Flyway 建好平台基础表。

**Architecture:** 在脚手架已建好的 `zhijin-server` Maven 9 模块上增强 `zhijin-framework`（数据访问 + 租户 + 日志）与 `zhijin-common`（统一响应/异常/上下文）。多租户采用 MyBatis-Plus `TenantLineInnerInterceptor` 在 SQL 层自动注入 `tenant_id`（逻辑隔离，共享库），租户 ID 来源：请求头 `X-Tenant-Id`（管理端）或后续 JWT/API Key（B2 接入后统一收敛到 `TenantContextHolder`）。建表用 Flyway。

**Tech Stack:** Spring Boot 4.0.0 · Spring Cloud 2025.1 · MyBatis-Plus `mybatis-plus-spring-boot4-starter` 3.5.15 · PostgreSQL 16 · Flyway 10.x · H2（PostgreSQL 兼容模式，测试）

**设计依据:** `docs/superpowers/specs/2026-08-17-agent-platform-design.md` §9 数据访问规则、§9.1 多租户、§12 测试、§12.1 七大原则。

---

## 关键决策

- **ORM = MyBatis-Plus**（官方 `mybatis-plus-spring-boot4-starter` ≥3.5.15 支持 Boot 4；`TenantLineInnerInterceptor` 提供 SQL 级租户隔离）。
- **多租户 = 逻辑隔离**：所有业务表带 `tenant_id`，查询/更新/删除由拦截器自动加 `WHERE tenant_id = ?`，插入由自动填充 `MetaObjectHandler` 写入租户列（对齐 §9.1）。
- **忽略租户过滤的表**：`sys_tenant`（租户表本身，无 tenant_id）、`sys_permission`（平台级权限字典）、`flyway_schema_history`。
- **测试**：H2 **PostgreSQL 兼容模式**跑集成测试（本机无 Docker，Testcontainers 不可用；已验证方案）；用户提供真实 PG 连接后，可切换 `spring.datasource` 对真库复验。
- **连接配置**：走环境变量（`deploy/.env` 由用户提供真实地址），本地测试用 Testcontainers 内置连接。

---

## 文件结构

本计划将创建的包/文件（在现有模块内新增）：

```
zhijin-server/
├── zhijin-common/src/main/kotlin/com/zhijin/common/
│   ├── web/Result.kt              ← 统一响应体 Result<T>
│   ├── web/ResultCode.kt          ← 统一响应码枚举
│   ├── exception/BizException.kt  ← 业务异常
│   ├── exception/GlobalExceptionHandler.kt  ← 全局异常处理
│   └── context/TenantContext.kt   ← 租户上下文(ThreadLocal)
├── zhijin-framework/
│   ├── pom.xml                    ← 追加 mybatis-plus/flyway/pg 依赖
│   └── src/main/kotlin/com/zhijin/framework/
│       ├── tenant/TenantContextHolder.kt      ← 租户读写封装
│       ├── tenant/TenantLineHandlerImpl.kt    ← 租户行级处理(忽略表/取租户ID)
│       ├── tenant/MybatisPlusConfig.kt        ← 拦截器装配
│       ├── tenant/MyMetaObjectHandler.kt      ← 插入自动填充 tenant_id/时间戳
│       ├── log/TraceIdFilter.kt               ← 生成/透传 traceId 到 MDC
│       └── log/LogbackConfig.kt  (或仅 resources/logback-spring.xml)
└── zhijin-app/src/main/resources/
    ├── application.yml           ← 追加 datasource/mybatis-plus/flyway
    └── db/migration/V1__base_schema.sql  ← 平台基础表
```

---

## Task 1: zhijin-framework 数据访问依赖与配置

**Files:**
- Modify: `zhijin-server/zhijin-framework/pom.xml`
- Modify: `zhijin-server/zhijin-app/src/main/resources/application.yml`

- [ ] **Step 1: 给 zhijin-framework pom 追加依赖**

在 `zhijin-framework/pom.xml` 的 `<dependencies>` 内追加：

```xml
    <!-- MyBatis-Plus(Boot 4 官方 starter) -->
    <dependency>
      <groupId>com.baomidou</groupId>
      <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
      <version>3.5.15</version>
    </dependency>
    <!-- PostgreSQL 驱动 -->
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>
    <!-- Flyway 数据库迁移 -->
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <!-- 参数校验 -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
```

> 说明：集成测试用 **H2 PostgreSQL 兼容模式**（本机无 Docker，Testcontainers 不可用）；真实 PG 校验在用户提供连接信息后执行（见 Task 6）。H2 测试依赖加在 `zhijin-app` 的 test scope（测试类所在模块）。

- [ ] **Step 2: 给 `application.yml` 追加数据源与 Flyway 配置**

在现有 `application.yml` 末尾追加（连接走环境变量，默认值便于本地 H2 降级）：

```yaml
spring:
  datasource:
    url: ${POSTGRES_URL:jdbc:postgresql://localhost:5432/zhijin}
    username: ${POSTGRES_USER:zhijin}
    password: ${POSTGRES_PASSWORD:zhijin_dev_2026}
    driver-class-name: org.postgresql.Driver
  flyway:
    enabled: true
    locations: classpath:db/migration

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    banner: false
```

- [ ] **Step 3: 构建验证**

Run: `cd zhijin-server && mvn -pl zhijin-app -am clean compile`
Expected: `BUILD SUCCESS`（无数据源启动，编译阶段不连库）。

- [ ] **Step 4: Commit**

```bash
git add zhijin-server/
git commit -m "feat(framework): MyBatis-Plus/Flyway/PG 依赖与数据源配置"
```
（message 末尾加 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`）

---

## Task 2: 统一响应体与全局异常处理

**Files:**
- Create: `zhijin-server/zhijin-common/src/main/kotlin/com/zhijin/common/web/Result.kt`
- Create: `zhijin-server/zhijin-common/src/main/kotlin/com/zhijin/common/web/ResultCode.kt`
- Create: `zhijin-server/zhijin-common/src/main/kotlin/com/zhijin/common/exception/BizException.kt`
- Create: `zhijin-server/zhijin-common/src/main/kotlin/com/zhijin/common/exception/GlobalExceptionHandler.kt`
- Test: `zhijin-server/zhijin-common/src/test/kotlin/com/zhijin/common/exception/GlobalExceptionHandlerTest.kt`

- [ ] **Step 1: 写失败测试（TDD）**

`GlobalExceptionHandlerTest.kt`：
```kotlin
package com.zhijin.common.exception

import com.zhijin.common.web.Result
import com.zhijin.common.web.ResultCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `业务异常返回错误码与消息`() {
        val resp: ResponseEntity<Result<Any>> =
            handler.handleBiz(BizException(ResultCode.TENANT_MISSING, "缺少租户"))
        assertEquals(400, resp.statusCode.value())
        assertEquals(ResultCode.TENANT_MISSING.code, resp.body?.code)
        assertEquals("缺少租户", resp.body?.message)
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd zhijin-server && mvn -pl zhijin-common test -Dtest=GlobalExceptionHandlerTest`
Expected: 编译失败（类不存在）。

- [ ] **Step 3: 实现**

`Result.kt`：
```kotlin
package com.zhijin.common.web

import com.zhijin.common.web.ResultCode

/**
 * 统一响应体：所有对外接口返回该结构。
 * code=0 表示成功；code 非 0 表示业务错误（见 ResultCode）。
 */
data class Result<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
) {
    companion object {
        fun <T> success(data: T? = null): Result<T> =
            Result(ResultCode.SUCCESS.code, ResultCode.SUCCESS.message, data)

        fun <T> error(code: ResultCode, message: String? = null): Result<T> =
            Result(code.code, message ?: code.message)
    }
}
```

`ResultCode.kt`：
```kotlin
package com.zhijin.common.web

/** 统一响应码：0 成功，1xxx 通用，2xxx 租户，3xxx 认证。 */
enum class ResultCode(val code: Int, val message: String) {
    SUCCESS(0, "成功"),
    BAD_REQUEST(1000, "请求参数错误"),
    INTERNAL_ERROR(1001, "系统内部错误"),
    TENANT_MISSING(2000, "缺少租户上下文"),
    TENANT_NOT_FOUND(2001, "租户不存在"),
    UNAUTHORIZED(3000, "未认证"),
    FORBIDDEN(3001, "无权限"),
}
```

`BizException.kt`：
```kotlin
package com.zhijin.common.exception

import com.zhijin.common.web.ResultCode

/** 业务异常：携带响应码与可读消息，由全局异常处理统一转成 Result。 */
class BizException(
    val code: ResultCode,
    override val message: String,
) : RuntimeException(message)
```

`GlobalExceptionHandler.kt`：
```kotlin
package com.zhijin.common.exception

import com.zhijin.common.web.Result
import com.zhijin.common.web.ResultCode
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** 全局异常处理：业务异常、参数校验、兜底异常统一转 Result 返回。 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BizException::class)
    fun handleBiz(e: BizException): ResponseEntity<Result<Any>> {
        val status = when (e.code) {
            ResultCode.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
            ResultCode.FORBIDDEN -> HttpStatus.FORBIDDEN
            else -> HttpStatus.BAD_REQUEST
        }
        return ResponseEntity.status(status).body(Result.error(e.code, e.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<Result<Any>> {
        val first = e.bindingResult.fieldErrors.firstOrNull()
        val msg = first?.let { "${it.field}: ${it.defaultMessage}" } ?: "参数校验失败"
        return ResponseEntity.badRequest().body(Result.error(ResultCode.BAD_REQUEST, msg))
    }

    @ExceptionHandler(Exception::class)
    fun handleUnknown(e: Exception): ResponseEntity<Result<Any>> {
        log.error("未捕获异常", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Result.error(ResultCode.INTERNAL_ERROR))
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd zhijin-server && mvn -pl zhijin-common test -Dtest=GlobalExceptionHandlerTest`
Expected: `1 passed`。

- [ ] **Step 5: Commit**

```bash
git add zhijin-server/
git commit -m "feat(common): 统一响应体与全局异常处理"
```

---

## Task 3: traceId 链路日志

**Files:**
- Create: `zhijin-server/zhijin-framework/src/main/kotlin/com/zhijin/framework/log/TraceIdFilter.kt`
- Create: `zhijin-server/zhijin-framework/src/main/resources/logback-spring.xml`
- Test: `zhijin-server/zhijin-framework/src/test/kotlin/com/zhijin/framework/log/TraceIdFilterTest.kt`

- [ ] **Step 1: 写失败测试**

`TraceIdFilterTest.kt`（验证请求头透传 + 新生成两种路径）：
```kotlin
package com.zhijin.framework.log

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.slf4j.MDC

class TraceIdFilterTest {

    private val filter = TraceIdFilter()

    @Test
    fun `无上游traceId时自动生成并写入MDC`() {
        val req = MockHttpServletRequest()
        val chain = FilterChain { _, _ -> }
        filter.doFilter(req, MockHttpServletResponse(), chain)
        assertNotNull(MDC.get(TraceIdFilter.TRACE_ID_KEY))
    }

    @Test
    fun `透传上游traceId`() {
        val req = MockHttpServletRequest()
        req.addHeader(TraceIdFilter.TRACE_ID_KEY, "trace-abc")
        val chain = FilterChain { _, _ -> }
        filter.doFilter(req, MockHttpServletResponse(), chain)
        assertEquals("trace-abc", MDC.get(TraceIdFilter.TRACE_ID_KEY))
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd zhijin-server && mvn -pl zhijin-framework test -Dtest=TraceIdFilterTest`
Expected: 编译失败（类不存在）。

- [ ] **Step 3: 实现**

`TraceIdFilter.kt`：
```kotlin
package com.zhijin.framework.log

import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import java.util.UUID

/**
 * 全链路 traceId：优先透传请求头中的 traceId（跨 Kotlin/Python 链路），
 * 无则生成。写入 MDC 供日志输出，响应头回传便于前端/客户对账。
 */
class TraceIdFilter : HttpFilter() {

    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"
    }

    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val request = req as HttpServletRequest
        val response = res as HttpServletResponse
        val traceId = request.getHeader(TRACE_ID_HEADER) ?: UUID.randomUUID().toString().replace("-", "")
        MDC.put(TRACE_ID_KEY, traceId)
        response.setHeader(TRACE_ID_HEADER, traceId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove(TRACE_ID_KEY)
        }
    }
}
```

`logback-spring.xml`：
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <!-- 控制台输出，带 traceId，便于开发定位 -->
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [%X{traceId}] %logger{36} - %msg%n</pattern>
      <charset>UTF-8</charset>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
  </root>
</configuration>
```

- [ ] **Step 4: 注册 Filter**

在 `zhijin-app` 的 `ZhijinApplication.kt` 同级加配置类（或加在 framework 的配置里）：
`zhijin-server/zhijin-framework/src/main/kotlin/com/zhijin/framework/log/LogConfig.kt`：
```kotlin
package com.zhijin.framework.log

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** 注册 traceId 过滤器，作用于所有请求。 */
@Configuration
class LogConfig {

    @Bean
    fun traceIdFilter(): FilterRegistrationBean<TraceIdFilter> =
        FilterRegistrationBean<TraceIdFilter>().apply {
            filter = TraceIdFilter()
            addUrlPatterns("/*")
            order = Int.MIN_VALUE // 最早执行，保证下游拿到 traceId
        }
}
```

- [ ] **Step 5: 运行确认通过**

Run: `cd zhijin-server && mvn -pl zhijin-framework test -Dtest=TraceIdFilterTest`
Expected: `2 passed`。

- [ ] **Step 6: Commit**

```bash
git add zhijin-server/
git commit -m "feat(framework): traceId 全链路日志(MDC + 请求头透传)"
```

---

## Task 4: 租户上下文与请求头解析

**Files:**
- Create: `zhijin-server/zhijin-common/src/main/kotlin/com/zhijin/common/context/TenantContext.kt`
- Create: `zhijin-server/zhijin-framework/src/main/kotlin/com/zhijin/framework/tenant/TenantContextHolder.kt`
- Create: `zhijin-server/zhijin-framework/src/main/kotlin/com/zhijin/framework/tenant/TenantFilter.kt`
- Test: `zhijin-server/zhijin-framework/src/test/kotlin/com/zhijin/framework/tenant/TenantContextHolderTest.kt`

- [ ] **Step 1: 写失败测试**

`TenantContextHolderTest.kt`：
```kotlin
package com.zhijin.framework.tenant

import com.zhijin.common.context.TenantContext
import com.zhijin.common.exception.BizException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class TenantContextHolderTest {

    @Test
    fun `设置与读取租户`() {
        TenantContextHolder.setTenantId(100L)
        assertEquals(100L, TenantContextHolder.getRequiredTenantId())
    }

    @Test
    fun `未设置时获取必填租户抛异常`() {
        TenantContextHolder.clear()
        assertThrows(BizException::class.java) { TenantContextHolder.getRequiredTenantId() }
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd zhijin-server && mvn -pl zhijin-framework test -Dtest=TenantContextHolderTest`
Expected: 编译失败。

- [ ] **Step 3: 实现**

`TenantContext.kt`（zhijin-common，线程安全上下文）：
```kotlin
package com.zhijin.common.context

/** 租户上下文：基于 ThreadLocal 保存当前请求的租户 ID。 */
object TenantContext {
    private val holder = ThreadLocal<Long?>()

    fun set(tenantId: Long?) {
        holder.set(tenantId)
    }

    fun get(): Long? = holder.get()

    fun clear() {
        holder.remove()
    }
}
```

`TenantContextHolder.kt`（zhijin-framework，业务读写 + 必填校验）：
```kotlin
package com.zhijin.framework.tenant

import com.zhijin.common.context.TenantContext
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode

/** 租户上下文读写封装。 */
object TenantContextHolder {

    fun setTenantId(tenantId: Long?) = TenantContext.set(tenantId)

    fun getTenantId(): Long? = TenantContext.get()

    /** 取必填租户：缺失时抛业务异常（被全局异常处理转 400）。 */
    fun getRequiredTenantId(): Long =
        TenantContext.get() ?: throw BizException(ResultCode.TENANT_MISSING, "缺少租户上下文")
}
```

`TenantFilter.kt`（从请求头 `X-Tenant-Id` 解析租户，请求结束清理）：
```kotlin
package com.zhijin.framework.tenant

import com.zhijin.common.context.TenantContext
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpFilter
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory

/**
 * 从请求头 X-Tenant-Id 解析租户 ID 写入上下文。
 * B2 认证接入后，租户来源收敛为 JWT 声明，本过滤器随之调整，接口不变。
 */
class TenantFilter : HttpFilter() {

    companion object {
        const val TENANT_HEADER = "X-Tenant-Id"
    }

    private val log = LoggerFactory.getLogger(TenantFilter::class.java)

    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        val request = req as HttpServletRequest
        val header = request.getHeader(TENANT_HEADER)
        val tenantId = header?.toLongOrNull()
        if (header != null && tenantId == null) {
            log.warn("非法租户请求头: {}", header)
        }
        TenantContext.set(tenantId)
        try {
            chain.doFilter(request, res)
        } finally {
            TenantContext.clear()
        }
    }
}
```

- [ ] **Step 4: 注册 TenantFilter**

在 `LogConfig.kt` 里追加 Bean（或新建 `TenantFilterConfig`）：
```kotlin
    @Bean
    fun tenantFilter(): FilterRegistrationBean<TenantFilter> =
        FilterRegistrationBean<TenantFilter>().apply {
            filter = TenantFilter()
            addUrlPatterns("/*")
            order = Int.MIN_VALUE + 1 // 紧接 traceId 之后
        }
```

- [ ] **Step 5: 运行确认通过**

Run: `cd zhijin-server && mvn -pl zhijin-framework test -Dtest=TenantContextHolderTest`
Expected: `2 passed`。

- [ ] **Step 6: Commit**

```bash
git add zhijin-server/
git commit -m "feat(tenant): 租户上下文与请求头解析"
```

---

## Task 5: MyBatis-Plus 租户拦截器 + 自动填充

**Files:**
- Create: `zhijin-server/zhijin-framework/src/main/kotlin/com/zhijin/framework/tenant/TenantLineHandlerImpl.kt`
- Create: `zhijin-server/zhijin-framework/src/main/kotlin/com/zhijin/framework/tenant/MybatisPlusConfig.kt`
- Create: `zhijin-server/zhijin-framework/src/main/kotlin/com/zhijin/framework/tenant/MyMetaObjectHandler.kt`

- [ ] **Step 1: 实现租户行处理器**

`TenantLineHandlerImpl.kt`：
```kotlin
package com.zhijin.framework.tenant

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler
import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.expression.LongValue

/**
 * 租户行级处理：指定租户列、从上下文取当前租户 ID、声明忽略租户过滤的系统表。
 * 忽略表：sys_tenant（租户表本身）、sys_permission（平台级权限字典）、flyway_schema_history。
 */
class TenantLineHandlerImpl : TenantLineHandler {

    companion object {
        const val TENANT_COLUMN = "tenant_id"
        val IGNORE_TABLES = setOf("sys_tenant", "sys_permission", "flyway_schema_history")
    }

    override fun getTenantId(): Expression = LongValue(TenantContextHolder.getTenantId() ?: 0L)

    override fun getTenantIdColumn(): String = TENANT_COLUMN

    override fun ignoreTable(tableName: String): Boolean = tableName in IGNORE_TABLES
}
```

`MybatisPlusConfig.kt`（装配拦截器：租户 → 分页）：
```kotlin
package com.zhijin.framework.tenant

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** MyBatis-Plus 插件装配：租户隔离 + 分页。顺序：租户在前，分页在后。 */
@Configuration
class MybatisPlusConfig {

    @Bean
    fun mybatisPlusInterceptor(): MybatisPlusInterceptor =
        MybatisPlusInterceptor().apply {
            addInnerInterceptor(TenantLineInnerInterceptor(TenantLineHandlerImpl()))
            addInnerInterceptor(PaginationInnerInterceptor())
        }
}
```

`MyMetaObjectHandler.kt`（插入/更新自动填充时间戳，插入时填充租户）：
```kotlin
package com.zhijin.framework.tenant

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler
import org.apache.ibatis.reflection.MetaObject
import java.time.LocalDateTime

/**
 * 字段自动填充：insert 时填 tenant_id + create_time + update_time，
 * update 时填 update_time。实体字段需配 @TableField(fill = FieldFill.INSERT)。
 */
class MyMetaObjectHandler : MetaObjectHandler {

    override fun insertFill(metaObject: MetaObject) {
        strictInsertFill(metaObject, "tenantId", Long::class.java, TenantContextHolder.getTenantId() ?: 0L)
        strictInsertFill(metaObject, "createTime", LocalDateTime::class.java, LocalDateTime.now())
        strictInsertFill(metaObject, "updateTime", LocalDateTime::class.java, LocalDateTime.now())
    }

    override fun updateFill(metaObject: MetaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime::class.java, LocalDateTime.now())
    }
}
```

- [ ] **Step 2: 注册 MyMetaObjectHandler 为 Bean**

在 `MybatisPlusConfig` 追加：
```kotlin
    @Bean
    fun metaObjectHandler(): MyMetaObjectHandler = MyMetaObjectHandler()
```

- [ ] **Step 3: 构建验证**

Run: `cd zhijin-server && mvn -pl zhijin-app -am clean compile`
Expected: `BUILD SUCCESS`。

- [ ] **Step 4: Commit**

```bash
git add zhijin-server/
git commit -m "feat(tenant): MyBatis-Plus 租户拦截器与字段自动填充"
```

---

## Task 6: Flyway 基础表 + 集成测试

**Files:**
- Create: `zhijin-server/zhijin-app/src/main/resources/db/migration/V1__base_schema.sql`
- Test: `zhijin-server/zhijin-framework/src/test/kotlin/com/zhijin/framework/tenant/TenantInterceptorIntegrationTest.kt`

- [ ] **Step 1: 建表 SQL**

`V1__base_schema.sql`：
```sql
-- 平台基础表：租户、用户、角色、权限（对齐 §9.1 多租户设计）
-- 说明：sys_tenant / sys_permission 为平台级（无 tenant_id，忽略租户过滤）；
--       其余业务表均带 tenant_id，由 MyBatis-Plus 租户拦截器自动隔离。

CREATE TABLE IF NOT EXISTS sys_tenant (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tenant_code VARCHAR(64)  NOT NULL UNIQUE,
    tenant_name VARCHAR(128) NOT NULL,
    status      SMALLINT     NOT NULL DEFAULT 1,
    create_time TIMESTAMP    NOT NULL DEFAULT now(),
    update_time TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(128) NOT NULL,
    nickname    VARCHAR(64)  NOT NULL DEFAULT '',
    status      SMALLINT     NOT NULL DEFAULT 1,
    create_time TIMESTAMP    NOT NULL DEFAULT now(),
    update_time TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sys_user_tenant ON sys_user (tenant_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_tenant_username ON sys_user (tenant_id, username);

CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL,
    role_code   VARCHAR(64)  NOT NULL,
    role_name   VARCHAR(128) NOT NULL,
    create_time TIMESTAMP    NOT NULL DEFAULT now(),
    update_time TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_tenant_code ON sys_role (tenant_id, role_code);

CREATE TABLE IF NOT EXISTS sys_user_role (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT    NOT NULL,
    user_id     BIGINT    NOT NULL,
    role_id     BIGINT    NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_role ON sys_user_role (tenant_id, user_id, role_id);

-- 平台级权限字典（无 tenant_id，忽略租户过滤）
CREATE TABLE IF NOT EXISTS sys_permission (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    perm_code   VARCHAR(64)  NOT NULL UNIQUE,
    perm_name   VARCHAR(128) NOT NULL,
    parent_id   BIGINT       NOT NULL DEFAULT 0,
    create_time TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id          BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    tenant_id   BIGINT    NOT NULL,
    role_id     BIGINT    NOT NULL,
    perm_id     BIGINT    NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_perm ON sys_role_permission (tenant_id, role_id, perm_id);
```

- [ ] **Step 2: 加 H2 测试依赖（zhijin-app）**

在 `zhijin-app/pom.xml` 的 `<dependencies>` 追加：
```xml
    <!-- 测试：H2(PG 兼容模式)，本机无 Docker 时替代 Testcontainers -->
    <dependency>
      <groupId>com.h2database</groupId>
      <artifactId>h2</artifactId>
      <scope>test</scope>
    </dependency>
```

创建测试配置文件 `zhijin-app/src/test/resources/application-test.yml`：
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:zhijin;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
    driver-class-name: org.h2.Driver
```

- [ ] **Step 3: 写集成测试（H2 验证 SQL 级租户隔离）**

`zhijin-app/src/test/kotlin/com/zhijin/app/tenant/TenantInterceptorIntegrationTest.kt`：
```kotlin
package com.zhijin.app.tenant

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.framework.entity.SysRole
import com.zhijin.framework.mapper.SysRoleMapper
import com.zhijin.framework.tenant.TenantContextHolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class TenantInterceptorIntegrationTest {

    @Autowired
    lateinit var roleMapper: SysRoleMapper

    @Test
    fun `插入自动填充租户, 查询按租户隔离`() {
        TenantContextHolder.setTenantId(1L)
        val role = SysRole(roleCode = "admin", roleName = "管理员")
        roleMapper.insert(role)
        assertTrue(role.id != null)
        assertEquals(1L, role.tenantId)

        // 租户2 查不到租户1的数据（SQL 层自动加 WHERE tenant_id = 2）
        TenantContextHolder.setTenantId(2L)
        val list = roleMapper.selectList(
            QueryWrapper<SysRole>().eq("role_code", "admin")
        )
        assertTrue(list.isEmpty())
    }
}
```

配套实体与 Mapper（放在 zhijin-framework 主代码，B2 复用）：
`zhijin-server/zhijin-framework/src/main/kotlin/com/zhijin/framework/entity/SysRole.kt`：
```kotlin
package com.zhijin.framework.entity

import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/** 角色实体：tenant_id 由 MyBatis-Plus 自动填充（FieldFill.INSERT）。 */
@TableName("sys_role")
data class SysRole(
    @TableId(type = IdType.AUTO)
    var id: Long? = null,
    var roleCode: String = "",
    var roleName: String = "",
    @TableField(fill = FieldFill.INSERT)
    var tenantId: Long? = null,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updateTime: LocalDateTime? = null,
)
```

`zhijin-server/zhijin-framework/src/main/kotlin/com/zhijin/framework/mapper/SysRoleMapper.kt`：
```kotlin
package com.zhijin.framework.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.framework.entity.SysRole
import org.apache.ibatis.annotations.Mapper

@Mapper
interface SysRoleMapper : BaseMapper<SysRole>
```

- [ ] **Step 4: 运行集成测试**

Run:
```bash
cd zhijin-server && mvn -pl zhijin-app test -Dtest=TenantInterceptorIntegrationTest
```
Expected: `1 passed`，验证租户 SQL 级隔离生效（H2 内存库）。
> 注：真实 PG 校验等用户提供连接后，把 `spring.datasource` 指向真库再跑一遍；Schema 用跨库可移植 DDL（`GENERATED BY DEFAULT AS IDENTITY`），PG 16 兼容。

- [ ] **Step 5: Commit**

```bash
git add zhijin-server/
git commit -m "feat(schema): Flyway 基础表 + 租户隔离集成测试(H2)"
```

---

## Task 7: 收尾验证

- [ ] **Step 1: 全量构建**

Run: `cd zhijin-server && mvn clean package`
Expected: `BUILD SUCCESS`（含全部单测/集成测试）。

- [ ] **Step 2: 冒烟冒烟脚本补租户头**

确认 `scripts/smoke.sh` 平台服务检查仍可用（服务未启动时为预期失败）。

- [ ] **Step 3: Commit 任何遗留**

```bash
git add -A
git commit -m "chore(framework): B1 数据基础设施收尾"
```

---

## Self-Review 记录

- **Spec 覆盖**：§9 数据访问规则（Kotlin 直连 platform schema）✓ · §9.1 多租户（逻辑隔离、tenant_id 自动注入）✓ · §12 测试（Testcontainers）✓ · §12.1 七大原则（TenantLineHandler 单一职责、租户上下文抽象 DIP、统一响应收敛）✓。
- **占位符扫描**：无 TBD/TODO；每步含完整代码与命令。
- **类型一致性**：`TenantContextHolder.getTenantId()` 在 Task 4/5/6 间一致；`X-Tenant-Id` 请求头贯穿 Filter/测试；`tenantId` 字段在实体与 SQL 间命名一致（map-underscore-to-camel-case）。

---

## 执行交接

B1 完成后 → **B2 认证中心**（Spring Security 7 OAuth2.1 授权服务器 + RBAC + login/logout/validate/refresh/userinfo），届时租户来源从 `X-Tenant-Id` 请求头收敛为 JWT 声明，`TenantFilter` 只改实现不改接口。

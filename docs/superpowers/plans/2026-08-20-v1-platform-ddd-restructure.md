# V1 DDD 架构改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 zhijin-server 各模块从 MVC 三层（controller/service/entity/mapper + 贫血模型）重构为 **DDD 四层**（interfaces / application / domain / infrastructure + 富血领域模型），对齐设计 §3.1「全面参考 Coze Studio 的 DDD 分层」。

**Architecture:** 每模块按依赖规则分层：`interfaces`（HTTP 边界，薄控制器 + DTO）→ `application`（应用服务/用例编排 + 事务）→ `domain`（富血实体/值对象/聚合/仓储接口/领域服务，**零 Spring/MyBatis 依赖**，纯 Kotlin）→ `infrastructure`（MyBatis-Plus 实现仓储、外部客户端适配器、配置）。依赖箭头严格向内；仓储接口在 domain，实现放 infrastructure（依赖倒置，DIP）。

> **版本说明（2026-08-20 v2）**：本版已按 `docs/superpowers/feedback/2026-08-20-v1-platform-ddd-restructure-feedback.md` 审核反馈修订（包名 `interface`→`interfaces`、命名统一保留 App、publish 保留版本快照、补全 ApiKey/Version 聚合示例、HttpModelComponent 归属修正等）。

**Tech Stack:** Kotlin 2.2 · Spring Boot 4 · MyBatis-Plus（仅 infrastructure 层）· 既有模块

**设计依据:** `2026-08-17-agent-platform-design.md` §3.1（Coze DDD 参考）、§12.1 七大原则（DIP/单一职责）；用户确认「先完成 B2 登录流重构，再全面 DDD 改造」。

---

## 关键决策

- **分层命名**：包结构 `interfaces / application / domain / infrastructure`。**注意：`interface` 是 Kotlin 硬关键字，不能作包名**（已实测编译失败），统一用复数 `interfaces`。
- **domain 零框架依赖**：domain 包内不得出现 `org.springframework.*`、`com.baomidou.*` 注解；实体为富血模型。
- **仓储模式**：`domain.xxx.XxxRepository` 接口 → `infrastructure.persistence.XxxRepositoryImpl`（用 MyBatis-Plus `XxxMapper` 实现）。
- **命名保留**：沿用现有 `app/App` 命名（**不改名为 Agent**），DDD 改造专注分层；P2 示例全部使用 `App` 命名。
- **领域校验抛 BizException**：领域规则违规抛 `BizException(ResultCode.BAD_REQUEST / FORBIDDEN, ...)`，**不用 `check()`**（会变 500），与全局异常处理一致。
- **应用服务**：一个用例一个方法，编排仓储 + 领域服务，管理事务（`@Transactional` 允许在 application 层）。
- **接口层薄化**：控制器只做「DTO 转换 + 调应用服务 + 返回 Result」。
- **Result/BizException/ResultCode**：web 边界（`common.web`），保持。
- **迁移顺序**：zhijin-app（示范）→ zhijin-auth → zhijin-chat → zhijin-orchestrator（包重组）→ zhijin-ai-client/common（归类）。
- **测试策略**：每迁移一模块，运行其全部测试 + 全量构建；domain 迁移后为领域逻辑补单测。

---

## 目标包结构（每模块通用）

以 `zhijin-app` 为例（其余模块同构；`interfaces` 复数）：

```
zhijin-app/src/main/kotlin/com/zhijin/app/
├── interfaces/                    ← HTTP 边界（原 controller）
│   ├── AppController.kt           ← 原 AppController（薄，只调应用服务）
│   └── dto/                       ← 原 dto（请求/响应）
├── application/
│   └── AppApplicationService.kt   ← 用例编排 + @Transactional
├── domain/
│   ├── app/
│   │   ├── App.kt                 ← 富血实体（原 App 贫血类 + 行为）
│   │   ├── AppStatus.kt           ← 值对象/枚举
│   │   └── AppRepository.kt       ← 仓储接口（纯 Kotlin）
│   ├── modelconfig/  version/  apikey/   ← 各聚合
└── infrastructure/
    ├── persistence/
    │   ├── AppRecord.kt           ← 持久化模型（原 App 贫血实体，含 createBy）
    │   └── AppRepositoryImpl.kt   ← 用 AppMapper 实现 AppRepository
    ├── mapper/                    ← MyBatis-Plus Mapper（原 mapper）
    └── crypto/                    ← CryptoServiceImpl（加密适配器）
```

**依赖规则**：`domain` 不 import 框架；`interfaces` 只依赖 `application` + `common.web`；`application` 依赖 `domain`（+必要时 infrastructure）；`infrastructure` 依赖 `domain` + 框架。

---

## P2: zhijin-app 示范模块完整迁移（详细）

> 本节是其它模块迁移的模板。顺序：domain → infrastructure → application → interfaces → 删旧 → 测试。

### Task A: 迁移 App 领域模型（App 聚合 + Version 聚合）

- [ ] **Step 1: 建 `domain/app/App.kt`（富血实体，纯 Kotlin）**

```kotlin
package com.zhijin.app.domain.app

import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import java.time.LocalDateTime

/** 应用（智能体）领域实体，富血模型：状态/发布规则即领域行为。 */
data class App(
    val id: Long?,
    val tenantId: Long,
    val appKey: String,
    val name: String,
    val description: String,
    val iconUri: String,
    val status: AppStatus,
    val createBy: Long?,
    val createTime: LocalDateTime?,
    val updateTime: LocalDateTime?,
) {
    /** 校验归属（对外抛业务异常，非 check()）。 */
    fun ensureOwnedBy(tenantId: Long) {
        if (this.tenantId != tenantId) throw BizException(ResultCode.FORBIDDEN, "无权操作")
    }

    /** 发布规则：DRAFT 或 PUBLISHED 均可发布（每次生成新版本快照）。 */
    fun ensurePublishable() {
        if (status !in setOf(AppStatus.DRAFT, AppStatus.PUBLISHED)) {
            throw BizException(ResultCode.BAD_REQUEST, "当前状态不可发布: $status")
        }
    }

    fun published(): App = copy(status = AppStatus.PUBLISHED)
}

enum class AppStatus { DRAFT, PUBLISHED, OFFLINE }
```

- [ ] **Step 2: 建 `domain/app/AppRepository.kt` + `domain/app/AppVersionRepository.kt`（仓储接口，纯 Kotlin）**

```kotlin
package com.zhijin.app.domain.app

/** 应用仓储接口（依赖倒置：实现放 infrastructure）。 */
interface AppRepository {
    fun findById(tenantId: Long, id: Long): App?
    fun save(app: App): App
    fun delete(tenantId: Long, id: Long)
}

/** 版本快照仓储接口。 */
interface AppVersionRepository {
    fun nextVersionNo(tenantId: Long, appId: Long): Int   // 现有版本数 + 1
    fun save(version: AppVersion): AppVersion
}

/** 版本快照（不可变，发布时生成）。 */
data class AppVersion(
    val id: Long?,
    val tenantId: Long,
    val appId: Long,
    val versionNo: Int,
    val workflowDsl: String?,
    val modelSnapshot: String?,
    val status: Int = 1,
    val publishTime: LocalDateTime?,
)
```

- [ ] **Step 3: 建 `infrastructure/persistence/AppRecord.kt` + `AppRepositoryImpl.kt`**

`AppRecord.kt`（持久化模型，**含 createBy**，由原 App 实体改造）：
```kotlin
package com.zhijin.app.infrastructure.persistence

import com.baomidou.mybatisplus.annotation.*
import com.zhijin.app.domain.app.App
import com.zhijin.app.domain.app.AppStatus
import java.time.LocalDateTime

/** 持久化记录（贫血，仅 infrastructure 用；由原 App 实体改造，保留 create_by 列）。 */
@TableName("app")
data class AppRecord(
    @TableId(type = IdType.AUTO)
    val id: Long? = null,
    var tenantId: Long? = null,
    var appKey: String = "",
    var name: String = "",
    var description: String = "",
    var iconUri: String = "",
    var status: Int = 0,
    var createBy: Long? = null,
    @TableField(fill = FieldFill.INSERT)
    var createTime: LocalDateTime? = null,
    @TableField(fill = FieldFill.INSERT_UPDATE)
    var updateTime: LocalDateTime? = null,
) {
    fun toDomain(): App = App(
        id = id, tenantId = tenantId!!, appKey = appKey, name = name,
        description = description, iconUri = iconUri,
        status = AppStatus.entries.firstOrNull { it.ordinal == status } ?: AppStatus.DRAFT,
        createBy = createBy, createTime = createTime, updateTime = updateTime,
    )

    companion object {
        fun from(app: App): AppRecord = AppRecord(
            id = app.id, tenantId = app.tenantId, appKey = app.appKey, name = app.name,
            description = app.description, iconUri = app.iconUri,
            status = app.status.ordinal, createBy = app.createBy,
            createTime = app.createTime, updateTime = app.updateTime,
        )
    }
}
```
> 说明：`AppStatus` 枚举序 ↔ `status` Int 的映射用 `ordinal`（与现有 `status: Int = 0` 语义一致：0 草稿 / 1 已发布 / 2 已下线）。`AppMapper` 即原 `AppMapper`（`@Mapper interface AppMapper : BaseMapper<AppRecord>`）。

`AppRepositoryImpl.kt`：
```kotlin
package com.zhijin.app.infrastructure.persistence

import com.zhijin.app.domain.app.App
import com.zhijin.app.domain.app.AppRepository
import com.zhijin.app.infrastructure.mapper.AppMapper
import org.springframework.stereotype.Repository

@Repository
class AppRepositoryImpl(private val appMapper: AppMapper) : AppRepository {

    override fun findById(tenantId: Long, id: Long): App? =
        appMapper.selectById(id)?.takeIf { it.tenantId == tenantId }?.toDomain()

    override fun save(app: App): App {
        val record = AppRecord.from(app)
        if (app.id == null) appMapper.insert(record) else appMapper.updateById(record)
        return record.toDomain()
    }

    override fun delete(tenantId: Long, id: Long) {
        appMapper.deleteById(id)
    }
}
```
`AppVersionRepositoryImpl.kt` 同理（用 AppVersionMapper，`nextVersionNo` = `selectCount(...) + 1`）。

- [ ] **Step 4: 验证 + Commit**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app -am clean compile`
Expected: `BUILD SUCCESS`（新旧代码并存过渡：旧 service 暂留，新 domain/infra 可编译）。
```bash
git add zhijin-server/zhijin-app/src/main/kotlin/com/zhijin/app/domain/ zhijin-server/zhijin-app/src/main/kotlin/com/zhijin/app/infrastructure/
git commit -m "refactor(ddd): zhijin-app 领域模型与仓储(示范)"
```
（末尾加 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`）

### Task B: 迁移应用服务 + 接口层

- [ ] **Step 1: 建 `application/AppApplicationService.kt`（publish 保留版本快照）**

```kotlin
package com.zhijin.app.application

import com.zhijin.app.domain.app.App
import com.zhijin.app.domain.app.AppRepository
import com.zhijin.app.domain.app.AppStatus
import com.zhijin.app.domain.app.AppVersion
import com.zhijin.app.domain.app.AppVersionRepository
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/** 应用应用服务：用例编排（create/get/update/delete/publish）。 */
@Service
class AppApplicationService(
    private val appRepository: AppRepository,
    private val versionRepository: AppVersionRepository,
) {

    @Transactional
    fun create(tenantId: Long, name: String, description: String, iconUri: String): App {
        val app = App(
            id = null, tenantId = tenantId,
            appKey = "app_" + UUID.randomUUID().toString().replace("-", "").take(16),
            name = name, description = description, iconUri = iconUri,
            status = AppStatus.DRAFT, createBy = null, createTime = null, updateTime = null,
        )
        return appRepository.save(app)
    }

    fun get(tenantId: Long, id: Long): App =
        appRepository.findById(tenantId, id) ?: throw BizException(ResultCode.BAD_REQUEST, "应用不存在")

    @Transactional
    fun update(tenantId: Long, id: Long, name: String, description: String, iconUri: String): App {
        val app = get(tenantId, id)
        app.ensureOwnedBy(tenantId)   // 归属校验：越权 403
        return appRepository.save(app.copy(name = name, description = description, iconUri = iconUri))
    }

    @Transactional
    fun delete(tenantId: Long, id: Long) {
        val app = get(tenantId, id)
        app.ensureOwnedBy(tenantId)
        appRepository.delete(tenantId, id)
    }

    /** 发布：保留版本快照（version_no 自增），DRAFT/PUBLISHED 均可重复发布。 */
    @Transactional
    fun publish(tenantId: Long, id: Long): AppVersion {
        val app = get(tenantId, id)
        app.ensureOwnedBy(tenantId)
        app.ensurePublishable()
        val next = versionRepository.nextVersionNo(tenantId, id)
        val version = versionRepository.save(
            AppVersion(
                id = null, tenantId = tenantId, appId = id, versionNo = next,
                workflowDsl = null, modelSnapshot = null, status = 1,
                publishTime = LocalDateTime.now(),
            )
        )
        appRepository.save(app.published())
        return version
    }
}
```
> **发布语义与现有 `PublishService` 完全一致**：允许重复发布、version_no 自增、插入不可变快照、状态置 published。响应返回 `AppVersion`（接口层映射为 `AppVersionResponse`，契约不变）。

- [ ] **Step 2: 建 `interfaces/AppController.kt`（薄控制器）**

```kotlin
package com.zhijin.app.interfaces

import com.zhijin.app.application.AppApplicationService
import com.zhijin.app.interfaces.dto.AppRequest
import com.zhijin.app.interfaces.dto.AppResponse
import com.zhijin.app.interfaces.dto.AppVersionResponse
import com.zhijin.common.web.Result
import com.zhijin.framework.tenant.TenantContextHolder
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

/** 应用端点（薄：只做 DTO 转换 + 调应用服务）。 */
@RestController
@RequestMapping("/api/apps")
class AppController(private val service: AppApplicationService) {

    private val tenantId: Long get() = TenantContextHolder.getRequiredTenantId()

    @PostMapping
    fun create(@Valid @RequestBody req: AppRequest): Result<AppResponse> =
        Result.success(service.create(tenantId, req.name, req.description, req.iconUri).toResponse())

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): Result<AppResponse> =
        Result.success(service.get(tenantId, id).toResponse())

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: AppRequest): Result<AppResponse> =
        Result.success(service.update(tenantId, id, req.name, req.description, req.iconUri).toResponse())

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): Result<Unit> {
        service.delete(tenantId, id)
        return Result.success()
    }

    @PostMapping("/{id}/publish")
    fun publish(@PathVariable id: Long): Result<AppVersionResponse> =
        Result.success(service.publish(tenantId, id).toVersionResponse())
}
```
（`AppRequest`/`AppResponse`/`AppVersionResponse` 为 DTO；`toResponse()`/`toVersionResponse()` 放接口层扩展。）

- [ ] **Step 3: 迁移剩余聚合（ModelConfig / ApiKey）**

**ModelConfig 聚合**（原 ModelConfigService + CryptoService）：
- `domain/modelconfig/`：`ModelProviderKey`、`AppModelConfig` 富血实体 + `ModelConfigRepository` 接口。
- `infrastructure/crypto/CryptoServiceImpl.kt`：实现 `CryptoService`（`encrypt/decrypt`，AES-256-GCM）——加密适配器归 infra。
- `application/ModelConfigApplicationService.kt`：addProviderKey（加密落库）、getPlainKey（解密）、saveConfig（upsert）。

**ApiKey 聚合**（原 AppApiKeyService）：
- `domain/apikey/`：`AppApiKey` 富血实体 + `ApiKeyRepository` 接口。
- `infrastructure/persistence/ApiKeyRepositoryImpl.kt`：用 `AppApiKeyMapper` 实现。**必须保留 `@InterceptorIgnore(tenantLine = "true")` 的 `findByHash` 语义**（开放 API `/v1` 鉴权时租户未确定；若被拦截器拼 `tenant_id=0` 永远查不到——这是 B5 `/v1/chat` 鉴权命脉）。加测试断言：无租户上下文查询成功。
- `application/ApiKeyApplicationService.kt`：generate（明文一次性返回，DB 存 SHA-256 哈希）、revoke、verify、findByPlainKey。

- [ ] **Step 4: 更新 ApiKeyResolverConfig（H1）**

`zhijin-app/config/ApiKeyResolverConfig.kt` 当前 `import com.zhijin.app.service.AppApiKeyService`，删除旧 service 后需改为注入新的 `ApiKeyApplicationService`（或 `ApiKeyRepository`）——**纳入本任务迁移清单**，否则 zhijin-chat 编译失败。

- [ ] **Step 5: 删除旧代码 + 测试迁移**

删除：`service/AppService.kt`、`service/ModelConfigService.kt`、`service/PublishService.kt`、`service/AppApiKeyService.kt`、`service/CryptoService.kt`、原 `controller/*`、贫血 `entity/App.kt` 等（App 已转 AppRecord）。
> 注意：`zhijin-framework` 的 `MybatisPlusConfig.@MapperScan` 仍扫描 `com.zhijin.app.mapper`——Mapper 包路径不变，保留。

测试迁移：
- `AppServiceTest` → `AppApplicationServiceTest`（mock AppRepository/AppVersionRepository，验证 create/get/update/delete/publish + 归属校验 403）。
- `PublishServiceTest` → 并入 `AppApplicationServiceTest`（**保留「第二次发布版本号为 2」断言**，验证版本快照）。
- `AppApiKeyServiceTest` → `ApiKeyApplicationServiceTest`（哈希/吊销/校验，**补 @InterceptorIgnore 绕过断言**）。
- `CryptoServiceTest` → 保留（CryptoServiceImpl 逻辑不变，调整 import）。
- 新增 `AppTest`/`AppVersionTest`（`ensurePublishable`/`published`/版本快照领域逻辑单测）。

- [ ] **Step 6: 验证 + Commit**

Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app -am test`
Expected: 全部通过（含重构后应用服务测试 + 既有 B1/B2/B3 测试）。
```bash
git add -A zhijin-server/zhijin-app/
git commit -m "refactor(ddd): zhijin-app 迁移到 DDD 四层(示范完成)"
```

---

## P3: zhijin-auth 迁移

- [ ] **Task: auth 模块 DDD 分层**
  - `domain`：`User`（富血：`verifyPassword`/`isDisabled` 行为）、`UserRepository`（`findByUsername`）、`UserId`/`TenantId` 值对象。
  - `application`：`AuthApplicationService`（validate 用例——从 JWT 读身份组装响应；logout 无状态）。
  - `infrastructure`：`UserRepositoryImpl`（SysUserMapper 实现，保留 `@InterceptorIgnore` 登录绕过）、`UserDetailsAdapter`（domain User ↔ Spring Security `ZhijinUserDetails`）、`SecurityConfig`/`OAuth2TokenCustomizer`（框架配置归 infra）。
  - `interfaces`：`AuthController`（validate/logout，薄）。
  - 说明：登录已是 Spring Security OAuth2（框架层），DDD 化主要是**解耦 domain User 与 Spring Security UserDetails**。
  - 验证：auth 相关测试全绿 + 全量构建。
- [ ] **提交**：`refactor(ddd): zhijin-auth 迁移到 DDD 四层`

---

## P4: zhijin-chat 迁移

- [ ] **Task: chat 模块 DDD 分层**
  - `domain`：`ChatSession`、`ChatMessage`（富血：`append` 行为）、`SessionRepository`、`ChatSessionId` 值对象。
  - `application`：`ChatApplicationService`（chat 用例：建/取会话、追加消息、驱动引擎、SSE 结果）。
  - `infrastructure`：`SessionRepositoryImpl`（ChatSessionMapper/ChatMessageMapper）、`ApiKeyAuthFilter`（框架过滤器，归 infra）。
  - `interfaces`：`ChatController`（薄）。
  - **注意（M5）**：`HttpModelComponent` 在 **zhijin-orchestrator** 模块（不在 chat），P4 不处理，交 P5 包重组。
  - 验证：chat 测试全绿 + 全量构建。
- [ ] **提交**：`refactor(ddd): zhijin-chat 迁移到 DDD 四层`

---

## P5: zhijin-orchestrator 包重组

- [ ] **Task: orchestrator 对齐 DDD**
  - **承认（M4）**：`zhijin-orchestrator/pom.xml` 依赖 common + framework + ai-client（Spring 全家桶传递）；仅**引擎核心代码**（model/executor/scheduler/nodes）未用 Spring 注解、为纯 Kotlin。
  - 包重组：`model`（NodeSchema/WorkflowSchema/值对象）→ `domain`；`executor`/`scheduler`/`nodes` → `domain`（领域服务/策略）或 `application`；`dsl`（WorkflowParser）→ `infrastructure`（解析适配器）；`context`（VariableStore）→ `domain` 或 `application`。
  - `HttpModelComponent` 在 orchestrator 模块 → 归 `infrastructure.model`（外部模型客户端适配器）。
  - 验证：orchestrator 17 测试全绿 + 全量构建。
- [ ] **提交**：`refactor(ddd): zhijin-orchestrator 包重组`

---

## P6: zhijin-ai-client / zhijin-common 归类

- [ ] **Task: 归类**
  - `zhijin-ai-client`：`AiClient` 为外部客户端适配器（infra），保留独立模块作为共享 infra 客户端，标注即可。
  - `zhijin-common`：`Result`/`BizException`/`ResultCode`（web 边界）→ `common.web`（保持）；`TenantContext` 保留（跨模块上下文）。
  - 验证：全量构建。
- [ ] **提交**：`refactor(ddd): ai-client/common 归类确认`

---

## P7: 收尾回归

- [ ] **Task: 全量构建 + 端到端回归**
  - Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn clean package` → `BUILD SUCCESS`。
  - 真实端到端回归（真 PG/Nacos）：OAuth2 client_credentials 发 token → `/api/apps` 建应用 → **发布两次验证 version_no 递增** → 生成 API Key → `/v1/chat`（**验证租户拦截器绕过不回归**）。
- [ ] **收尾提交**：`docs(plans): DDD 改造完成，追加执行修正记录`

---

## Self-Review 记录

- **Spec 覆盖**：§3.1 DDD 四层对齐 ✓ · §12.1 七大原则（DIP 仓储、SRP 分层）✓ · 用户确认的改造顺序 ✓。
- **反馈闭环**：审核反馈 B1-B3/H1-H3/M1-M10 全部纳入（包名 interfaces、命名保留 App、publish 保留快照、ApiKey 拦截器绕过、BizException 校验、HttpModelComponent 归属、-am 命令、三测试迁移等）。
- **测试覆盖**：每模块迁移后全量测试 + 领域单测（AppTest/PublishTest/ApiKey 绕过断言）+ P7 端到端回归。
- **类型一致性**：`AppRepository.save` 返回 App；`AppRecord.toDomain`/`AppRecord.from` 双向一致（含 createBy）；`AppStatus` 枚举 ↔ Int 用 ordinal；publish 返回 `AppVersion` → `AppVersionResponse` 契约不变。

## 执行交接

DDD 改造完成后 → **B6 用量统计 + 审计**（按 DDD 分层直接新建）→ **计划 C**（Python 真实供应商）→ **Plan D**（前端控制台）。

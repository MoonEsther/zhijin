# V1 DDD 架构改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 zhijin-server 各模块从 MVC 三层（controller/service/entity/mapper + 贫血模型）重构为 **DDD 四层**（interface / application / domain / infrastructure + 富血领域模型），对齐设计 §3.1「全面参考 Coze Studio 的 DDD 分层」。

**Architecture:** 每模块按依赖规则分层：`interface`（HTTP 边界，薄控制器 + DTO）→ `application`（应用服务/用例编排 + 事务）→ `domain`（富血实体/值对象/聚合/仓储接口/领域服务，**零 Spring/MyBatis 依赖**，纯 Kotlin）→ `infrastructure`（MyBatis-Plus 实现仓储、外部客户端适配器、配置）。依赖箭头严格向内：interface→application→domain，infrastructure→domain（实现其接口）。仓储接口在 domain 定义，实现放 infrastructure（依赖倒置，DIP）。

**Tech Stack:** Kotlin 2.2 · Spring Boot 4 · MyBatis-Plus（仅 infrastructure 层）· 既有模块

**设计依据:** `2026-08-17-agent-platform-design.md` §3.1（Coze DDD 参考）、§12.1 七大原则（DIP/单一职责）；用户确认「先完成 B2 登录流重构，再全面 DDD 改造」。

---

## 关键决策

- **分层命名**：每模块包结构 `interface / application / domain / infrastructure`（DDD 术语，替代 controller/service/entity/mapper）。
- **domain 零框架依赖**：domain 包内不得出现 `org.springframework.*`、`com.baomidou.*` 注解；实体为富血模型（含行为），非贫血 data class。
- **仓储模式**：`domain.xxx.XxxRepository` 接口（含领域查询方法）→ `infrastructure.persistence.XxxRepositoryImpl`（用 MyBatis-Plus `XxxMapper` 实现）。
- **值对象**：如 `TenantId`、`AgentStatus`、`ApiKeyHash` 等，用值对象表达领域概念而非裸 Long/String。
- **应用服务**：一个用例一个方法，编排仓储 + 领域服务，管理事务（`@Transactional` 允许在 application 层）。
- **接口层薄化**：控制器只做「DTO 转换 + 调应用服务 + 返回 Result」，业务逻辑全部下沉。
- **Result/BizException/ResultCode**：属 web 边界（interface 层），`zhijin-common` 中归类为 `common.web` 保持不变（被 interface 使用）。
- **命名**：沿用现有 `app/App` 命名（用户撤回改名「智能体」的讨论），DDD 改造期间不改名，专注分层。
- **迁移顺序**：zhijin-app（示范，聚合最典型）→ zhijin-auth → zhijin-chat → zhijin-orchestrator（包重组）→ zhijin-ai-client/common（归类）。
- **测试策略**：每迁移一个模块，运行其全部测试 + 全量构建；domain 迁移后为领域逻辑补单元测试。

---

## 目标包结构（每模块通用）

以 `zhijin-app` 为例（其余模块同构）：

```
zhijin-app/src/main/kotlin/com/zhijin/app/
├── interface/                    ← HTTP 边界（原 controller）
│   ├── AgentController.kt        ← 原 AppController（薄，只调应用服务）
│   └── dto/                      ← 原 dto（请求/响应）
├── application/
│   └── AgentApplicationService.kt   ← 用例编排 + @Transactional
├── domain/
│   ├── agent/
│   │   ├── Agent.kt              ← 富血实体（原 App 贫血类 + 行为）
│   │   ├── AgentStatus.kt        ← 值对象/枚举
│   │   └── AgentRepository.kt    ← 仓储接口（纯 Kotlin）
│   ├── modelconfig/  version/  apikey/   ← 各聚合
└── infrastructure/
    ├── persistence/
    │   └── AgentRepositoryImpl.kt   ← 用 AgentMapper 实现 AgentRepository
    ├── mapper/                   ← MyBatis-Plus Mapper（原 mapper）
    └── crypto/                   ← CryptoServiceImpl（外部加密适配器）
```

**依赖规则**（Maven 层面不变，代码层面靠包结构 + 评审保证）：
- `domain` 不 import `org.springframework.*` / `com.baomidou.*`
- `interface` 只依赖 `application` + `common.web`
- `application` 依赖 `domain` + `infrastructure`（必要时）
- `infrastructure` 依赖 `domain` + 框架

---

## P1: 分层规范与基建

- [ ] **Task 1: 确定包结构与依赖规则**
  - 产出《模块 DDD 分层规范》文档（本节内容），作为所有模块迁移的 checklist。
  - 验证：评审通过。

- [ ] **Task 2: 新建 `zhijin-app` 的 domain 骨架（示范）**
  - 创建 `com.zhijin.app.domain.agent` 包 + `Agent.kt`（先迁实体）+ `AgentRepository.kt` 接口。
  - 验证：`mvn -pl zhijin-app -am clean compile` 通过（先建接口不实现，仅编译）。

- [ ] **Task 3: 建 `zhijin-app` infrastructure + application + interface 骨架**
  - 创建 `persistence/AgentRepositoryImpl.kt`（用现有 AppMapper 实现 AgentRepository）。
  - 创建 `application/AgentApplicationService.kt`（先空壳）。
  - 创建 `interface/AgentController.kt`（迁移原 AppController 端点，暂调空壳）。
  - 验证：全量编译 + 原测试（先不删旧代码，新旧并存过渡）或直接替换（见 P2 示范详案）。

> **说明**：P1 实际与 P2 融合进行（zhijin-app 即示范），P2 给出完整迁移步骤，P1 的任务 1（规范）在 P2 完成后回填定稿。

---

## P2: zhijin-app 示范模块完整迁移（详细）

> 本节是其它模块迁移的模板。每个 Task 遵循「先建 domain → 再 infra → 再 application → 再 interface → 删旧 → 测试」。

- [ ] **Task 4: 迁移领域模型（Agent + 相关聚合）**

**Step 1: 建 `domain/agent/Agent.kt`（富血实体，纯 Kotlin）**

```kotlin
package com.zhijin.app.domain.agent

import java.time.LocalDateTime

/** 智能体应用（领域实体，富血模型：状态转换即领域行为）。 */
data class Agent(
    val id: Long?,
    val tenantId: Long,
    val appKey: String,
    val name: String,
    val description: String,
    val iconUri: String,
    val status: AgentStatus,
    val createTime: LocalDateTime?,
    val updateTime: LocalDateTime?,
) {
    /** 发布前校验：草稿才可发布。 */
    fun ensurePublishable() {
        check(status == AgentStatus.DRAFT) { "仅草稿可发布，当前状态: $status" }
    }

    fun published(): Agent = copy(status = AgentStatus.PUBLISHED)
}

/** 状态值对象。 */
enum class AgentStatus { DRAFT, PUBLISHED, OFFLINE }
```

**Step 2: 建 `domain/agent/AgentRepository.kt`（仓储接口，纯 Kotlin）**

```kotlin
package com.zhijin.app.domain.agent

/** 智能体仓储接口（依赖倒置：实现放 infrastructure）。 */
interface AgentRepository {
    fun findById(tenantId: Long, id: Long): Agent?
    fun save(agent: Agent): Agent
    fun delete(tenantId: Long, id: Long)
}
```

**Step 3: 建 `infrastructure/persistence/AgentRepositoryImpl.kt`（用现有 AppMapper 实现）**

```kotlin
package com.zhijin.app.infrastructure.persistence

import com.zhijin.app.domain.agent.Agent
import com.zhijin.app.domain.agent.AgentRepository
import com.zhijin.app.domain.agent.AgentStatus
import com.zhijin.app.infrastructure.mapper.AgentMapper
import com.zhijin.app.infrastructure.mapper.AgentRecord
import org.springframework.stereotype.Repository

/** AgentRepository 的 MyBatis-Plus 实现（基础设施层）。 */
@Repository
class AgentRepositoryImpl(private val agentMapper: AgentMapper) : AgentRepository {

    override fun findById(tenantId: Long, id: Long): Agent? =
        agentMapper.selectById(id)?.takeIf { it.tenantId == tenantId }?.toDomain()

    override fun save(agent: Agent): Agent {
        val record = agent.toRecord()
        if (agent.id == null) {
            agentMapper.insert(record)
        } else {
            agentMapper.updateById(record)
        }
        return record.toDomain()
    }

    override fun delete(tenantId: Long, id: Long) {
        agentMapper.deleteById(id)
    }
}

/** 持久化记录（贫血，仅 infrastructure 用）。 */
data class AgentRecord(
    val id: Long? = null,
    val tenantId: Long? = null,
    val appKey: String = "",
    val name: String = "",
    val description: String = "",
    val iconUri: String = "",
    val status: Int = 0,
    val createTime: java.time.LocalDateTime? = null,
    val updateTime: java.time.LocalDateTime? = null,
)
```
> 说明：原 `App` 实体（MyBatis-Plus 注解贫血类）重命名为 `AgentRecord` 并留在 `infrastructure.mapper` 包（或 `persistence`），作为持久化模型；领域实体 `Agent` 与之双向转换（`toDomain`/`toRecord`）。`AgentMapper` 即原 `AppMapper`（保留 `@Mapper` 注解）。

**Step 4: 验证**
Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app -am clean compile`
Expected: `BUILD SUCCESS`。

**Step 5: Commit**
```bash
git add zhijin-server/zhijin-app/src/main/kotlin/com/zhijin/app/domain/ zhijin-server/zhijin-app/src/main/kotlin/com/zhijin/app/infrastructure/
git commit -m "refactor(ddd): zhijin-app 领域模型与仓储(示范)"
```
（末尾加 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`）

- [ ] **Task 5: 迁移应用服务 + 接口层**

**Step 1: 建 `application/AgentApplicationService.kt`（用例编排，@Transactional）**

```kotlin
package com.zhijin.app.application

import com.zhijin.app.domain.agent.Agent
import com.zhijin.app.domain.agent.AgentRepository
import com.zhijin.app.domain.agent.AgentStatus
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** 智能体应用服务：用例编排（create/get/update/delete/publish）。 */
@Service
class AgentApplicationService(private val agentRepository: AgentRepository) {

    @Transactional
    fun create(tenantId: Long, name: String, description: String, iconUri: String): Agent {
        val agent = Agent(
            id = null, tenantId = tenantId,
            appKey = "app_" + UUID.randomUUID().toString().replace("-", "").take(16),
            name = name, description = description, iconUri = iconUri,
            status = AgentStatus.DRAFT, createTime = null, updateTime = null,
        )
        return agentRepository.save(agent)
    }

    fun get(tenantId: Long, id: Long): Agent =
        agentRepository.findById(tenantId, id) ?: throw BizException(ResultCode.BAD_REQUEST, "应用不存在")

    @Transactional
    fun update(tenantId: Long, id: Long, name: String, description: String, iconUri: String): Agent {
        val agent = get(tenantId, id)
        return agentRepository.save(
            agent.copy(name = name, description = description, iconUri = iconUri)
        )
    }

    @Transactional
    fun delete(tenantId: Long, id: Long) {
        get(tenantId, id)
        agentRepository.delete(tenantId, id)
    }

    @Transactional
    fun publish(tenantId: Long, id: Long): Agent {
        val agent = get(tenantId, id)
        agent.ensurePublishable()   // 领域行为
        return agentRepository.save(agent.published())
    }
}
```

**Step 2: 建 `interface/AgentController.kt`（薄控制器）**

```kotlin
package com.zhijin.app.interface

import com.zhijin.app.application.AgentApplicationService
import com.zhijin.app.interface.dto.AgentRequest
import com.zhijin.app.interface.dto.AgentResponse
import com.zhijin.common.web.Result
import com.zhijin.framework.tenant.TenantContextHolder
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.annotation.RestController

/** 智能体应用端点（薄：只做 DTO 转换 + 调应用服务）。 */
@RestController
@RequestMapping("/api/apps")
class AgentController(private val service: AgentApplicationService) {

    private val tenantId: Long get() = TenantContextHolder.getRequiredTenantId()

    @PostMapping
    fun create(@Valid @RequestBody req: AgentRequest): Result<AgentResponse> =
        Result.success(service.create(tenantId, req.name, req.description, req.iconUri).toResponse())

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): Result<AgentResponse> =
        Result.success(service.get(tenantId, id).toResponse())

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody req: AgentRequest): Result<AgentResponse> =
        Result.success(service.update(tenantId, id, req.name, req.description, req.iconUri).toResponse())

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): Result<Unit> {
        service.delete(tenantId, id)
        return Result.success()
    }

    @PostMapping("/{id}/publish")
    fun publish(@PathVariable id: Long): Result<AgentResponse> =
        Result.success(service.publish(tenantId, id).toResponse())
}
```
（`AgentRequest`/`AgentResponse` 为 DTO，`toResponse()` 放 DTO 伴生或接口层扩展。）

**Step 3: 迁移剩余聚合**（ModelProviderKey、AgentModelConfig、AgentVersion、AgentApiKey 同模式）——各自 domain 实体/仓储 + application 服务（ModelConfigService、PublishService、AppApiKeyService 拆分）。

**Step 4: 删除旧代码 + 测试**
- 删除原 `service/AppService.kt`、`service/ModelConfigService.kt`、`service/PublishService.kt`、`service/AppApiKeyService.kt`、`controller/*Controller.kt`、贫血 `entity/*.kt`（除转成 AgentRecord 的）。
- 更新测试：`AppServiceTest` → `AgentApplicationServiceTest`（用 mock AgentRepository，验证 create/publish 领域行为）。
- 补充 `AgentTest`（`ensurePublishable`/`published` 领域逻辑单测）。

**Step 5: 验证**
Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn -pl zhijin-app test`
Expected: 全部通过（含重构后的应用服务测试 + 既有 B1/B2/B3 测试）。

**Step 6: Commit**
```bash
git add -A zhijin-server/zhijin-app/
git commit -m "refactor(ddd): zhijin-app 迁移到 DDD 四层(示范完成)"
```

---

## P3: zhijin-auth 迁移

- [ ] **Task 6: auth 模块 DDD 分层**
  - `domain`：`User`（富血：密码校验/禁用判断）、`UserRepository`（`findByUsername`）、`TenantId`/`UserId` 值对象。
  - `application`：`AuthApplicationService`（validate 用例——从 JWT 读身份组装响应；logout 无状态）。
  - `infrastructure`：`UserRepositoryImpl`（SysUserMapper 实现）、`UserDetailsAdapter`（把 domain User 适配成 Spring Security `ZhijinUserDetails`）、`SecurityConfig`/`OAuth2TokenCustomizer`（框架配置归 infra）。
  - `interface`：`AuthController`（validate/logout，薄）。
  - 说明：登录本身已是 Spring Security OAuth2（框架层），DDD 化主要是**解耦 domain User 与 Spring Security UserDetails**：domain 定义 User，infra 提供 UserDetailsAdapter 转换。
  - 验证：auth 相关测试全绿 + 全量构建。
- [ ] **Task 7: auth 提交**
  `git commit -m "refactor(ddd): zhijin-auth 迁移到 DDD 四层"`

---

## P4: zhijin-chat 迁移

- [ ] **Task 8: chat 模块 DDD 分层**
  - `domain`：`ChatSession`、`ChatMessage`（富血：`appendMessage` 行为）、`SessionRepository`、`ChatSessionId` 值对象。
  - `application`：`ChatApplicationService`（chat 用例：建/取会话、追加消息、驱动引擎、SSE 结果）。
  - `infrastructure`：`SessionRepositoryImpl`（ChatSessionMapper/ChatMessageMapper）、`HttpModelComponent`（已属 infra 适配器，移到 `infrastructure.model`）、`ApiKeyAuthFilter`（框架过滤器，归 infra 或保留 web）。
  - `interface`：`ChatController`（薄）。
  - 验证：chat 测试全绿 + 全量构建。
- [ ] **Task 9: chat 提交**
  `git commit -m "refactor(ddd): zhijin-chat 迁移到 DDD 四层"`

---

## P5: zhijin-orchestrator 包重组

- [ ] **Task 10: orchestrator 对齐 DDD**
  - 本模块已接近 DDD（纯引擎）：`model`（NodeSchema/WorkflowSchema/值对象）→ `domain`；`executor`/`scheduler`/`nodes` → `domain`（领域服务/策略实现）或 `application`；`dsl`（WorkflowParser）→ `infrastructure`（解析适配器）；`context`（VariableStore）→ `domain` 或 `application`。
  - 主要工作是**包重命名**（model→domain 等）+ 依赖确认（orchestrator 不依赖 Spring——已是纯 Kotlin 引擎，保持）。
  - 验证：orchestrator 16 测试全绿 + 全量构建。
- [ ] **Task 11: orchestrator 提交**
  `git commit -m "refactor(ddd): zhijin-orchestrator 包重组"`

---

## P6: zhijin-ai-client / zhijin-common 归类

- [ ] **Task 12: 归类**
  - `zhijin-ai-client`：`AiClient` 属 `infrastructure`（外部适配器）——包名改为 `com.zhijin.aiclient` 内分 `interface` 语义？实为外部客户端，保留 `com.zhijin.aiclient`，标注为 infra 适配器即可（或挪到各模块 `infrastructure.client`，避免跨模块耦合——决定：保留独立模块，作为共享 infra 客户端）。
  - `zhijin-common`：`Result`/`BizException`/`ResultCode`（web 边界）→ `common.web`（保持）；`TenantContext` → 保留（跨模块上下文）。无需大改，确认归类即可。
  - 验证：全量构建。
- [ ] **Task 13: 提交**
  `git commit -m "refactor(ddd): ai-client/common 归类确认"`

---

## P7: 收尾回归

- [ ] **Task 14: 全量构建 + 端到端回归**
  - Run: `cd C:\mypro\JavaProject\zhijin\zhijin-server && mvn clean package`
  - Expected: `BUILD SUCCESS`（全部模块 + 全部测试）。
  - 真实端到端回归：启动应用（真 PG/Nacos），走一遍「OAuth2 client_credentials 发 token → /api/apps 建应用 → 生成 API Key → /v1/chat」（复现 B2/B3/B5 联调，确认 DDD 重构无行为回归）。
- [ ] **Task 15: 收尾提交**
  ```bash
  git add -A
  git commit -m "docs(plans): DDD 改造完成，追加执行修正记录"
  ```

---

## Self-Review 记录

- **Spec 覆盖**：§3.1 DDD 四层对齐 ✓ · §12.1 七大原则（DIP 仓储模式、SRP 分层、依赖倒置）✓ · 用户确认的改造顺序 ✓。
- **测试覆盖**：每个模块迁移后全量测试 + 领域逻辑单测（AgentTest 等）+ P7 端到端回归。
- **占位符扫描**：P2 示范给全代码；P3-P6 为模式化描述（复用 P2 模板），无 TBD 占位。
- **类型一致性**：`AgentRepository.save` 返回 Agent；`AgentRecord.toDomain`/`Agent.toRecord` 双向一致；`AgentStatus` 枚举在领域与持久化（Int）间有映射。

## 执行交接

DDD 改造完成后 → **B6 用量统计 + 审计**（按 DDD 分层直接新建）→ **计划 C**（Python 真实供应商）→ **Plan D**（前端控制台，OAuth2 登录流 + 画布）。

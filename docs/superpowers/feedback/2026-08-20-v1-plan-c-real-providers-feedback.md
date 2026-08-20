# 计划 C：Python 真实供应商接入 审核反馈（v3 复审）

> **关联计划:** [`docs/superpowers/plans/2026-08-20-v1-plan-c-real-providers.md`](../plans/2026-08-20-v1-plan-c-real-providers.md)（v3 版）
>
> **审核方式:** v3 计划与当前代码逐条对照（`VariableStore` 读写方法及 key 格式、spring-web 7.0.7 jar 内 Jackson 类、`AiClient` 现有转换器装配方式、Python 适配器代码），并对 v2 反馈 N1~N6 逐项复核。
>
> **结论:** v2 的 N1（tenantId 写死 0）、N2'（WorkflowResult 无 outputs）、N3（Jackson 2 包名）、N6（测试清单）已全部修复且与代码吻合。**剩 2 个实质问题**：N4'（AiClient 重写版 jsonMapper 未装配进 RestClient，反序列化必失败）和 N5（C5 环境变量回退仍未落地，V1 默认链路必 401）。其余为轻微项。修完即可执行。

---

## 1. v2 反馈（N1~N6）闭环复核

| v2 项 | v3 处理 | 复核 |
|---|---|---|
| N1 tenantId 写死 0 | ✅ 端口签名去掉 tenantId（L334~341），`resolvePlainKey(it)`（L445），适配 Bean 从 `TenantContextHolder.getRequiredTenantId()` 取（L471~474） | 通过（chatAsync 子线程已 setTenantId，上下文有效） |
| N2' WorkflowResult 无 outputs | ✅ 改用 `store` 局部变量 + `store.readNodeOutput("llm", "usage")`（L635~642） | **通过且无需兜底代码**：`VariableStore.readNodeOutput` **已存在**（`VariableStore.kt` L22），key 格式 `"$nodeId.$outputKey"` 与 `writeNodeOutput`（L15）及 LlmNode 写入的 `"usage"` 完全匹配（L665~668 的「若不存在需新增」可删除，方法已有） |
| N3 Jackson 2 包名 | ✅ `tools.jackson.annotation.JsonProperty`（L545） | 通过 |
| N4 AiClient 丢 KotlinModule 配置 | ⚠️ **修了一半**：jsonMapper 变量保留了（L583~585），但 **restClient 没有把它装配进消息转换器**（L587~590 只有 baseUrl + defaultHeader）→ 见 N4' | **残留（阻塞）** |
| N5 C5 环境变量回退 | ⚠️ 仍只写进 Self-Review（L727），Task 1 Python 适配器代码无实现 → 见 N5 | **残留（中-高）** |
| N6 测试清单 | ✅ 补 ChatApplicationServiceTest / WorkflowIntegrationTest / WorkflowRunnerTest + 全量 mvn test 兜底（L513~516） | 通过 |

---

## 2. 剩余实质问题

### N4'. AiClient 重写版：jsonMapper 是「死变量」，未装配进 RestClient → 反序列化必失败
- **计划位置:** Task 3 Step 1（L583~590）。
- **现状（已核实）:** 现有 `AiClient.kt` L22~31 用 `configureMessageConverters { clientBuilder.registerDefaults(); clientBuilder.withJsonConverter(JacksonJsonHttpMessageConverter(jsonMapper)) }` 把 KotlinModule JsonMapper **装进 RestClient**（B5 执行时踩坑后的必要配置，现有代码有注释）。v3 重写版只 `JsonMapper.builder().addModule(KotlinModule...).build()` 定义了变量，**RestClient 仍是默认转换器**——KotlinModule 不生效，`body(CompletionResponse::class.java)` 反序列化 Kotlin data class（无默认构造器）会抛异常。
- **修订:** 补回 `configureMessageConverters` 装配（`JacksonJsonHttpMessageConverter(jsonMapper)`，该类在 spring-web 7.0.7 中存在，已核实）。
- **附带（轻微）:** L549 `import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder` 未使用（该类在 spring-web 7.0.7 中仍存在，不会编译失败，但应删除死 import）。

### N5. C5（providerKeyId=null 回退）仍未落地到 Python 适配器代码
- **计划位置:** Task 1 Step 2/3 适配器代码（openai_adapter/claude_adapter 直接用传入的 `api_key`，无回退）；Self-Review L727 仅一句「Python 适配器回退环境变量（如 QWEN_API_KEY）」。
- **影响:** V1 `ChatApplicationService` 的 `providerKeyId` 恒为 null（L631）→ `HttpModelComponent` 传 `plainKey=""` → Python `api_key=""` → **真实供应商必 401**。默认链路不可用，除非回退真正实现。
- **修订:** Task 1 落地（二选一）：① Python 适配器 `complete` 内 `api_key or os.getenv("QWEN_API_KEY")`（claude 对应 `ANTHROPIC_API_KEY`，按 provider 映射）；② Kotlin 侧 `plainKey.ifEmpty { System.getenv("QWEN_API_KEY") ?: "" }`。建议①（Python 侧按适配器归属更内聚），并把行为写进任务步骤而非仅 Self-Review。

---

## 3. 轻微项（可不改）

| # | 说明 |
|---|---|
| N7 | L665~668 的 `readNodeOutput` 兜底代码可删除——方法已存在且 key 格式匹配（见 N2' 复核） |
| N8 | LlmNodeTest 测试片段 `NodeSchema(...)`（L504）为占位符，Self-Review「无 TBD」不严谨；测试细节执行时补全即可 |
| N9 | `complete()` 向后兼容路径 `completeWithUsage(prompt, model, "qwen", "").content` ✅；`provider="qwen"` 写死属 C6 已注明局限，V1 接受 |
| N10 | 「既有 62 测试」（L673）为估算，执行时以实际为准 |
| N11 | `latencyMillis` 测量方式未写（chat 用例起止计时即可） |

---

## 4. 修订清单（按 Task 组织）

| 位置 | 修订动作 |
|---|---|
| Task 3 Step 1 | ① restClient 补 `configureMessageConverters` 装配 jsonMapper（N4'）；② 删除 L549 死 import（N4' 附带） |
| Task 1 Step 2/3 | ③ 适配器落地 `api_key or os.getenv(...)` 回退（N5） |
| Task 3 Step 2 | ④ 删除 readNodeOutput 兜底代码说明（方法已存在）（N7，可选） |

---

## 5. 验证记录（本轮新增）

- **`VariableStore`:** `readNodeOutput(nodeId, outputKey)` 已存在（`VariableStore.kt` L22）；`writeNodeOutput` key 格式 `"$nodeId.$outputKey"`（L15）→ `readNodeOutput("llm", "usage")` 命中 LlmNode 写入的 `llm.usage` ✅。
- **spring-web 7.0.7 jar:** 内含 `JacksonJsonHttpMessageConverter`（现有装配可用）与 `Jackson2ObjectMapperBuilder`（死 import 不炸，但应删）→ N4' 修订可行、L549 不会编译失败。
- **Python 适配器:** `openai_adapter.py`/`claude_adapter.py` 的 `complete` 直接使用入参 `api_key`，无环境变量回退 → N5 成立。

---

## 6. 执行交接

修订 N4'/N5 后按 Task 1→4 执行；完成后 → **Plan D**（前端控制台）。

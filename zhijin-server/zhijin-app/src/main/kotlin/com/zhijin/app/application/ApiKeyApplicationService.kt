package com.zhijin.app.application

import com.zhijin.app.domain.apikey.ApiKeyRepository
import com.zhijin.app.domain.apikey.AppApiKey
import com.zhijin.app.interfaces.dto.ApiKeyResponse
import com.zhijin.billingaudit.domain.audit.AuditLog
import com.zhijin.billingaudit.domain.audit.AuditRecorder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.UUID

/**
 * 应用 API Key 应用服务：生成时明文返回一次，DB 仅存 SHA-256 哈希。
 * 校验/吊销均基于哈希与 status 状态，杜绝明文二次恢复。
 */
@Service
class ApiKeyApplicationService(
    private val apiKeyRepository: ApiKeyRepository,
    /** 审计记录端口（依赖倒置注入，默认 no-op 保证无适配器时流程不受影响）。 */
    private val auditRecorder: AuditRecorder = AuditRecorder {},
) {

    /** 生成新 API Key：返回明文一次，入库 keyHash 为 SHA-256(plain)。 */
    @Transactional
    fun generate(tenantId: Long, appId: Long, name: String): ApiKeyResponse {
        // 明文形如 ak_ + 32 位随机十六进制（去横线），一次性返回给调用方
        val plain = "ak_" + UUID.randomUUID().toString().replace("-", "")
        val key = apiKeyRepository.save(
            AppApiKey(
                id = null, tenantId = tenantId, appId = appId,
                keyHash = sha256(plain), name = name, status = 1,
            )
        )
        // 记录审计（P8：username 暂取不到用户信息，传空串）
        auditRecorder.record(
            AuditLog(tenantId = tenantId, username = "", action = "API_KEY_GENERATE", targetType = "api_key", targetId = key.id)
        )
        // save 回填自增主键 id 后，方可组装响应
        return ApiKeyResponse(id = key.id!!, plainKey = plain, name = name)
    }

    /** 吊销 API Key：置 status=0；keyId 不存在、租户不匹配或不属于该应用时静默跳过（幂等）。 */
    @Transactional
    fun revoke(tenantId: Long, appId: Long, keyId: Long) {
        val key = apiKeyRepository.findById(tenantId, keyId) ?: return
        // 应用级隔离：keyId 属于同租户其他应用时视为不存在，防止跨应用吊销（与 list 的应用维度隔离一致）
        if (key.appId != appId) return
        apiKeyRepository.save(key.revoked())
        // 记录审计（P8：username 暂取不到用户信息，传空串）
        auditRecorder.record(
            AuditLog(tenantId = tenantId, username = "", action = "API_KEY_REVOKE", targetType = "api_key", targetId = keyId)
        )
    }

    /**
     * 按租户+应用列出 API Key（详情页列表数据源）。
     * 只返回 id/name/createTime；明文不可恢复故不返回，plainKey 一律置空。
     */
    fun list(tenantId: Long, appId: Long): List<ApiKeyResponse> =
        apiKeyRepository.findByTenantAndApp(tenantId, appId)
            .map { ApiKeyResponse(id = it.id!!, plainKey = "", name = it.name, createTime = it.createTime) }

    /** 校验 API Key（供开放 API 鉴权用）：仅当哈希命中且 status=1 时通过。 */
    fun verify(tenantId: Long, appId: Long, plainKey: String): Boolean =
        apiKeyRepository.findActiveByHash(tenantId, appId, sha256(plainKey)) != null

    /**
     * 通过明文 Key 反查租户+应用（V1 开放 API /v1 鉴权用）。返回 null 表示无效。
     * 底层 findByHash 必须绕开租户拦截器：调用时机在租户上下文建立之前，租户由 Key 解析而来。
     */
    fun findByPlainKey(plainKey: String): Pair<Long, Long>? {
        val key = apiKeyRepository.findByHash(sha256(plainKey)) ?: return null
        if (!key.isActive()) return null
        return Pair(key.tenantId, key.appId)
    }

    /** 将明文做 SHA-256 摘要并输出 64 位小写十六进制串。 */
    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

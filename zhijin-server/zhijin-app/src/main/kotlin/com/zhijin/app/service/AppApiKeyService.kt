package com.zhijin.app.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.app.dto.ApiKeyResponse
import com.zhijin.app.entity.AppApiKey
import com.zhijin.app.mapper.AppApiKeyMapper
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.UUID

/**
 * 应用 API Key 服务：生成时明文返回一次，DB 仅存 SHA-256 哈希。
 * 校验/吊销均基于哈希与 status 状态，杜绝明文二次恢复。
 */
@Service
class AppApiKeyService(private val keyMapper: AppApiKeyMapper) {

    /** 生成新 API Key：返回明文一次，入库 keyHash 为 SHA-256(plain)。 */
    fun generate(tenantId: Long, appId: Long, name: String): ApiKeyResponse {
        // 明文形如 ak_ + 32 位随机十六进制（去横线），一次性返回给调用方
        val plain = "ak_" + UUID.randomUUID().toString().replace("-", "")
        val key = AppApiKey(
            tenantId = tenantId, appId = appId,
            keyHash = sha256(plain), name = name, status = 1,
        )
        // insert 回填自增主键 id 后，方可组装响应
        keyMapper.insert(key)
        return ApiKeyResponse(id = key.id!!, plainKey = plain, name = name)
    }

    /** 吊销 API Key：置 status=0；keyId 不存在或租户不匹配时静默跳过（幂等）。 */
    fun revoke(tenantId: Long, appId: Long, keyId: Long) {
        val key = keyMapper.selectById(keyId) ?: return
        if (key.tenantId != tenantId) return
        keyMapper.updateById(key.copy(status = 0))
    }

    /** 校验 API Key（供 B5 开放 API 鉴权用）：仅当哈希命中且 status=1 时通过。 */
    fun verify(tenantId: Long, appId: Long, plainKey: String): Boolean {
        val key = keyMapper.selectList(
            QueryWrapper<AppApiKey>()
                .eq("tenant_id", tenantId).eq("app_id", appId).eq("key_hash", sha256(plainKey))
        ).firstOrNull()
        return key != null && key.status == 1
    }

    /** 将明文做 SHA-256 摘要并输出 64 位小写十六进制串。 */
    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

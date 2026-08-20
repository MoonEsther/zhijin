package com.zhijin.app.application

import com.zhijin.app.domain.modelconfig.AppModelConfig
import com.zhijin.app.domain.modelconfig.CryptoService
import com.zhijin.app.domain.modelconfig.ModelConfigRepository
import com.zhijin.app.domain.modelconfig.ModelProviderKey
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 模型配置应用服务：供应商 Key 管理（加密落库）+ 应用模型配置（upsert）。 */
@Service
class ModelConfigApplicationService(
    private val repository: ModelConfigRepository,
    private val crypto: CryptoService,
) {

    /** 新增供应商 Key：加密后存储，明文不落库。 */
    @Transactional
    fun addProviderKey(tenantId: Long, provider: String, name: String, plainKey: String): ModelProviderKey {
        val key = ModelProviderKey(
            id = null, tenantId = tenantId, provider = provider, name = name,
            encryptedKey = crypto.encrypt(plainKey), status = 1,
        )
        return repository.saveProviderKey(key)
    }

    /** 读取解密后的 Key（供调用时下发，不对外返回明文）。 */
    fun getPlainKey(tenantId: Long, keyId: Long): String {
        val key = repository.findProviderKeyById(tenantId, keyId)
            ?: throw BizException(ResultCode.BAD_REQUEST, "供应商 Key 不存在")
        return crypto.decrypt(key.encryptedKey)
    }

    /** 保存应用模型配置（每个应用一条，upsert）。 */
    @Transactional
    fun saveConfig(tenantId: Long, appId: Long, provider: String, modelName: String, providerKeyId: Long?) {
        val existing = repository.findConfig(tenantId, appId)
        val config = AppModelConfig(
            id = existing?.id, tenantId = tenantId, appId = appId, provider = provider,
            modelName = modelName, providerKeyId = providerKeyId,
        )
        repository.saveConfig(config)
    }
}

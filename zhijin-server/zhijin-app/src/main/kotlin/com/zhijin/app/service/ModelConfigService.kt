package com.zhijin.app.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.app.entity.AppModelConfig
import com.zhijin.app.entity.ModelProviderKey
import com.zhijin.app.mapper.AppModelConfigMapper
import com.zhijin.app.mapper.ModelProviderKeyMapper
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import org.springframework.stereotype.Service

/** 模型配置：供应商 Key 管理（加密落库）+ 应用模型配置。 */
@Service
class ModelConfigService(
    private val keyMapper: ModelProviderKeyMapper,
    private val configMapper: AppModelConfigMapper,
    private val crypto: CryptoService,
) {

    /** 新增供应商 Key：加密后存储，明文不落库。 */
    fun addProviderKey(tenantId: Long, provider: String, name: String, plainKey: String): ModelProviderKey {
        val key = ModelProviderKey(
            tenantId = tenantId, provider = provider, name = name,
            encryptedKey = crypto.encrypt(plainKey), status = 1,
        )
        keyMapper.insert(key)
        return key
    }

    /** 读取解密后的 Key（供调用时下发，不对外返回明文）。 */
    fun getPlainKey(tenantId: Long, keyId: Long): String {
        val key = keyMapper.selectById(keyId)
            ?: throw BizException(ResultCode.BAD_REQUEST, "供应商 Key 不存在")
        if (key.tenantId != tenantId) throw BizException(ResultCode.FORBIDDEN, "无权访问")
        return crypto.decrypt(key.encryptedKey)
    }

    /** 保存应用模型配置（每个应用一条，upsert）。 */
    fun saveConfig(tenantId: Long, appId: Long, provider: String, modelName: String, providerKeyId: Long?) {
        val existing = configMapper.selectOne(
            QueryWrapper<AppModelConfig>().eq("app_id", appId).eq("tenant_id", tenantId)
        )
        val cfg = AppModelConfig(
            tenantId = tenantId, appId = appId, provider = provider,
            modelName = modelName, providerKeyId = providerKeyId,
        )
        if (existing == null) configMapper.insert(cfg) else configMapper.updateById(cfg.copy(id = existing.id))
    }
}

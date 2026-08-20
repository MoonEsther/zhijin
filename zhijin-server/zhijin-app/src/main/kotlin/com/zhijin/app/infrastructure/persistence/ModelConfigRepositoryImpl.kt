package com.zhijin.app.infrastructure.persistence

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.app.domain.modelconfig.AppModelConfig
import com.zhijin.app.domain.modelconfig.ModelConfigRepository
import com.zhijin.app.domain.modelconfig.ModelProviderKey
import com.zhijin.app.mapper.AppModelConfigMapper
import com.zhijin.app.mapper.ModelProviderKeyMapper
import org.springframework.stereotype.Repository

/** 模型配置仓储实现：基于 MyBatis-Plus Mapper，记录与领域实体互转。 */
@Repository
class ModelConfigRepositoryImpl(
    private val keyMapper: ModelProviderKeyMapper,
    private val configMapper: AppModelConfigMapper,
) : ModelConfigRepository {

    override fun saveProviderKey(key: ModelProviderKey): ModelProviderKey {
        val record = ModelProviderKeyRecord.from(key)
        if (key.id == null) keyMapper.insert(record) else keyMapper.updateById(record)
        return record.toDomain()
    }

    override fun findProviderKeyById(tenantId: Long, keyId: Long): ModelProviderKey? =
        keyMapper.selectById(keyId)?.takeIf { it.tenantId == tenantId }?.toDomain()

    override fun findConfig(tenantId: Long, appId: Long): AppModelConfig? =
        configMapper.selectOne(
            QueryWrapper<AppModelConfigRecord>().eq("app_id", appId).eq("tenant_id", tenantId)
        )?.toDomain()

    override fun saveConfig(config: AppModelConfig): AppModelConfig {
        val record = AppModelConfigRecord.from(config)
        if (config.id == null) configMapper.insert(record) else configMapper.updateById(record)
        return record.toDomain()
    }
}

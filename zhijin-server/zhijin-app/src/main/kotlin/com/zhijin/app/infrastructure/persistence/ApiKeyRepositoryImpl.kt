package com.zhijin.app.infrastructure.persistence

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.app.domain.apikey.ApiKeyRepository
import com.zhijin.app.domain.apikey.AppApiKey
import com.zhijin.app.mapper.AppApiKeyMapper
import org.springframework.stereotype.Repository

/** 应用 API Key 仓储实现：基于 MyBatis-Plus Mapper，记录与领域实体互转。 */
@Repository
class ApiKeyRepositoryImpl(private val keyMapper: AppApiKeyMapper) : ApiKeyRepository {

    override fun save(key: AppApiKey): AppApiKey {
        val record = AppApiKeyRecord.from(key)
        if (key.id == null) keyMapper.insert(record) else keyMapper.updateById(record)
        return record.toDomain()
    }

    override fun findById(tenantId: Long, keyId: Long): AppApiKey? =
        keyMapper.selectById(keyId)?.takeIf { it.tenantId == tenantId }?.toDomain()

    /**
     * 关键：必须委托给带 @InterceptorIgnore(tenantLine="true") 的 Mapper 方法，
     * 以保证开放 API /v1 鉴权（租户尚未确定）时不被租户拦截器拼上 tenant_id=0。
     */
    override fun findByHash(hash: String): AppApiKey? =
        keyMapper.findByHash(hash)?.toDomain()

    override fun findActiveByHash(tenantId: Long, appId: Long, hash: String): AppApiKey? =
        keyMapper.selectOne(
            QueryWrapper<AppApiKeyRecord>()
                .eq("tenant_id", tenantId).eq("app_id", appId).eq("key_hash", hash)
        )?.toDomain()?.takeIf { it.isActive() }

    /**
     * 列出某应用的 API Key：显式加 tenant_id 条件与租户拦截器自动附加的过滤叠加（AND 语义），
     * 保证即使该表被加入拦截器忽略清单也不会出现跨租户泄露。
     */
    override fun findByTenantAndApp(tenantId: Long, appId: Long): List<AppApiKey> =
        keyMapper.selectList(
            QueryWrapper<AppApiKeyRecord>().eq("tenant_id", tenantId).eq("app_id", appId)
        ).map { it.toDomain() }
}

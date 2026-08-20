package com.zhijin.app.infrastructure.persistence

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.app.domain.app.AppVersion
import com.zhijin.app.domain.app.AppVersionRepository
import com.zhijin.app.mapper.AppVersionMapper
import org.springframework.stereotype.Repository

@Repository
class AppVersionRepositoryImpl(private val versionMapper: AppVersionMapper) : AppVersionRepository {

    override fun nextVersionNo(tenantId: Long, appId: Long): Int =
        // 按 AppVersionRecord 实体类型计数（现有版本数 + 1），勿用 Any 避免泛型失配
        versionMapper.selectCount(
            QueryWrapper<AppVersionRecord>().eq("app_id", appId).eq("tenant_id", tenantId)
        ).toInt() + 1

    override fun save(version: AppVersion): AppVersion {
        // 转 AppVersionMapper 的持久化记录（AppVersionRecord 保留 publish_by 列）
        val record = AppVersionRecord.from(version)
        if (version.id == null) versionMapper.insert(record) else versionMapper.updateById(record)
        return record.toDomain()
    }
}

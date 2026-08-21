package com.zhijin.app.infrastructure.persistence

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.app.domain.app.App
import com.zhijin.app.domain.app.AppRepository
import com.zhijin.app.mapper.AppMapper   // 注意：Mapper 保留在 com.zhijin.app.mapper（不移动，@MapperScan 硬编码），import 用原包
import org.springframework.stereotype.Repository

@Repository
class AppRepositoryImpl(private val appMapper: AppMapper) : AppRepository {

    override fun findById(tenantId: Long, id: Long): App? =
        appMapper.selectById(id)?.takeIf { it.tenantId == tenantId }?.toDomain()

    /** 租户全部应用：QueryWrapper 按 tenant_id 过滤（列表页用，不做分页——V1 应用量级小）。 */
    override fun findAll(tenantId: Long): List<App> =
        appMapper.selectList(QueryWrapper<AppRecord>().eq("tenant_id", tenantId)).map { it.toDomain() }

    override fun save(app: App): App {
        val record = AppRecord.from(app)
        if (app.id == null) appMapper.insert(record) else appMapper.updateById(record)
        return record.toDomain()
    }

    override fun delete(tenantId: Long, id: Long) {
        appMapper.deleteById(id)
    }
}

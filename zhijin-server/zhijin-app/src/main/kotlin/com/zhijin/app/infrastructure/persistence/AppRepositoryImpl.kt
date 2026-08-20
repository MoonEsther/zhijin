package com.zhijin.app.infrastructure.persistence

import com.zhijin.app.domain.app.App
import com.zhijin.app.domain.app.AppRepository
import com.zhijin.app.mapper.AppMapper   // 注意：Mapper 保留在 com.zhijin.app.mapper（不移动，@MapperScan 硬编码），import 用原包
import org.springframework.stereotype.Repository

@Repository
class AppRepositoryImpl(private val appMapper: AppMapper) : AppRepository {

    override fun findById(tenantId: Long, id: Long): App? =
        appMapper.selectById(id)?.takeIf { it.tenantId == tenantId }?.toDomain()

    override fun save(app: App): App {
        val record = AppRecord.from(app)
        if (app.id == null) appMapper.insert(record) else appMapper.updateById(record)
        return record.toDomain()
    }

    override fun delete(tenantId: Long, id: Long) {
        appMapper.deleteById(id)
    }
}

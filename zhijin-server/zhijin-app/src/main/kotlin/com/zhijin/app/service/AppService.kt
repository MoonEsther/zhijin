package com.zhijin.app.service

import com.zhijin.app.dto.AppRequest
import com.zhijin.app.dto.AppResponse
import com.zhijin.app.infrastructure.persistence.AppRecord
import com.zhijin.app.mapper.AppMapper
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import org.springframework.stereotype.Service
import java.util.UUID

/** 应用管理：CRUD。租户隔离由 MyBatis-Plus 拦截器自动保证。 */
@Service
class AppService(private val appMapper: AppMapper) {

    fun create(tenantId: Long, req: AppRequest): AppResponse {
        val app = AppRecord(
            tenantId = tenantId,
            appKey = "app_" + UUID.randomUUID().toString().replace("-", "").take(16),
            name = req.name,
            description = req.description,
            iconUri = req.iconUri,
            status = 0,
        )
        appMapper.insert(app)
        return app.toResponse()
    }

    fun get(tenantId: Long, id: Long): AppResponse =
        appMapper.selectById(id)?.takeIf { it.tenantId == tenantId }
            ?.toResponse()
            ?: throw BizException(ResultCode.BAD_REQUEST, "应用不存在")

    fun update(tenantId: Long, id: Long, req: AppRequest): AppResponse {
        val app = appMapper.selectById(id)
            ?: throw BizException(ResultCode.BAD_REQUEST, "应用不存在")
        if (app.tenantId != tenantId) throw BizException(ResultCode.FORBIDDEN, "无权操作")
        val updated = app.copy(name = req.name, description = req.description, iconUri = req.iconUri)
        appMapper.updateById(updated)
        return updated.toResponse()
    }

    fun delete(tenantId: Long, id: Long) {
        val app = appMapper.selectById(id)
            ?: throw BizException(ResultCode.BAD_REQUEST, "应用不存在")
        if (app.tenantId != tenantId) throw BizException(ResultCode.FORBIDDEN, "无权操作")
        appMapper.deleteById(id)
    }

    private fun AppRecord.toResponse() = AppResponse(
        id = id!!, appKey = appKey, name = name, description = description,
        iconUri = iconUri, status = status,
    )
}

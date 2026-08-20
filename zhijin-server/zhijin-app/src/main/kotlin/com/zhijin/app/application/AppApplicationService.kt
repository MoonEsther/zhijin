package com.zhijin.app.application

import com.zhijin.app.domain.app.App
import com.zhijin.app.domain.app.AppRepository
import com.zhijin.app.domain.app.AppStatus
import com.zhijin.app.domain.app.AppVersion
import com.zhijin.app.domain.app.AppVersionRepository
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/** 应用应用服务：用例编排（create/get/update/delete/publish）。 */
@Service
class AppApplicationService(
    private val appRepository: AppRepository,
    private val versionRepository: AppVersionRepository,
) {

    @Transactional
    fun create(tenantId: Long, name: String, description: String, iconUri: String): App {
        val app = App(
            id = null, tenantId = tenantId,
            appKey = "app_" + UUID.randomUUID().toString().replace("-", "").take(16),
            name = name, description = description, iconUri = iconUri,
            status = AppStatus.DRAFT, createBy = null, createTime = null, updateTime = null,
        )
        return appRepository.save(app)
    }

    fun get(tenantId: Long, id: Long): App =
        appRepository.findById(tenantId, id) ?: throw BizException(ResultCode.BAD_REQUEST, "应用不存在")

    @Transactional
    fun update(tenantId: Long, id: Long, name: String, description: String, iconUri: String): App {
        val app = get(tenantId, id)
        app.ensureOwnedBy(tenantId)   // 越权 403
        return appRepository.save(app.copy(name = name, description = description, iconUri = iconUri))
    }

    @Transactional
    fun delete(tenantId: Long, id: Long) {
        val app = get(tenantId, id)
        app.ensureOwnedBy(tenantId)
        appRepository.delete(tenantId, id)
    }

    /** 发布：保留版本快照（version_no 自增），DRAFT/PUBLISHED 均可重复发布。 */
    @Transactional
    fun publish(tenantId: Long, id: Long): AppVersion {
        val app = get(tenantId, id)
        app.ensureOwnedBy(tenantId)
        app.ensurePublishable()
        val next = versionRepository.nextVersionNo(tenantId, id)
        val version = versionRepository.save(
            AppVersion(
                id = null, tenantId = tenantId, appId = id, versionNo = next,
                workflowDsl = null, modelSnapshot = null, status = 1, publishBy = null,
                publishTime = LocalDateTime.now(),
            )
        )
        appRepository.save(app.published())
        return version
    }
}

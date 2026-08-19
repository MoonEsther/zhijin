package com.zhijin.app.service

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.app.dto.AppVersionResponse
import com.zhijin.app.entity.App
import com.zhijin.app.entity.AppVersion
import com.zhijin.app.mapper.AppMapper
import com.zhijin.app.mapper.AppVersionMapper
import com.zhijin.common.exception.BizException
import com.zhijin.common.web.ResultCode
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/** 版本发布：draft → published，version_no 自增，生成不可变快照。 */
@Service
class PublishService(
    private val appMapper: AppMapper,
    private val versionMapper: AppVersionMapper,
) {

    fun publish(tenantId: Long, appId: Long): AppVersionResponse {
        // 校验应用存在且归属当前租户
        val app = appMapper.selectById(appId)
            ?: throw BizException(ResultCode.BAD_REQUEST, "应用不存在")
        if (app.tenantId != tenantId) throw BizException(ResultCode.FORBIDDEN, "无权操作")

        // 按当前版本数量自增得到新版本号（快照不可变，因此只增不删）
        val next = versionMapper.selectCount(
            QueryWrapper<AppVersion>().eq("app_id", appId).eq("tenant_id", tenantId)
        ).toInt() + 1

        // 生成发布快照：workflowDsl / modelSnapshot 当前为占位空值，后续任务落地
        val version = AppVersion(
            tenantId = tenantId, appId = appId, versionNo = next,
            workflowDsl = null, modelSnapshot = null, status = 1,
            publishTime = LocalDateTime.now(),
        )
        versionMapper.insert(version)

        // 应用状态由草稿(0)置为已发布(1)
        appMapper.updateById(app.copy(status = 1))
        return AppVersionResponse(id = version.id!!, versionNo = version.versionNo, status = version.status)
    }
}

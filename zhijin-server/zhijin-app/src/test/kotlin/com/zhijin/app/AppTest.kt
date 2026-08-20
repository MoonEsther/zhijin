package com.zhijin.app

import com.zhijin.app.domain.app.App
import com.zhijin.app.domain.app.AppStatus
import com.zhijin.app.domain.app.AppVersion
import com.zhijin.common.exception.BizException
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/** App 领域实体行为测试：发布规则/状态流转/归属校验/版本快照不可变。 */
class AppTest {

    private fun app(tenantId: Long = 1L, status: AppStatus = AppStatus.DRAFT) = App(
        id = 1L, tenantId = tenantId, appKey = "app_x", name = "x",
        description = "", iconUri = "", status = status,
        createBy = null, createTime = null, updateTime = null,
    )

    @Test
    fun `草稿可发布`() {
        assertDoesNotThrow { app(status = AppStatus.DRAFT).ensurePublishable() }
    }

    @Test
    fun `已发布可重复发布`() {
        assertDoesNotThrow { app(status = AppStatus.PUBLISHED).ensurePublishable() }
    }

    @Test
    fun `下线不可发布`() {
        assertThrows(BizException::class.java) { app(status = AppStatus.OFFLINE).ensurePublishable() }
    }

    @Test
    fun `published_状态置为已发布`() {
        assertEquals(AppStatus.PUBLISHED, app(status = AppStatus.DRAFT).published().status)
    }

    @Test
    fun `ensureOwnedBy_租户匹配通过`() {
        assertDoesNotThrow { app(tenantId = 1L).ensureOwnedBy(1L) }
    }

    @Test
    fun `ensureOwnedBy_租户不匹配抛403`() {
        assertThrows(BizException::class.java) { app(tenantId = 2L).ensureOwnedBy(1L) }
    }

    @Test
    fun `版本快照不可变`() {
        val v1 = AppVersion(
            id = 1L, tenantId = 1L, appId = 1L, versionNo = 1,
            workflowDsl = null, modelSnapshot = null, status = 1, publishBy = null,
            publishTime = LocalDateTime.now(),
        )
        val v2 = v1.copy(versionNo = 2)
        assertEquals(1, v1.versionNo)  // copy 生成新对象，原快照不被修改
        assertEquals(2, v2.versionNo)
    }
}

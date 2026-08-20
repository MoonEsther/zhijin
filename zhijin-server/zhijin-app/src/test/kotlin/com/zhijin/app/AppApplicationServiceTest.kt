package com.zhijin.app

import com.zhijin.app.application.AppApplicationService
import com.zhijin.app.domain.app.App
import com.zhijin.app.domain.app.AppRepository
import com.zhijin.app.domain.app.AppStatus
import com.zhijin.app.domain.app.AppVersion
import com.zhijin.app.domain.app.AppVersionRepository
import com.zhijin.billingaudit.domain.audit.AuditLog
import com.zhijin.billingaudit.domain.audit.AuditRecorder
import com.zhijin.common.exception.BizException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

/**
 * AppApplicationService 单元测试：模拟 AppRepository / AppVersionRepository。
 * 合并原 AppServiceTest（create/get/update/delete + 403 越权）与
 * PublishServiceTest（首次发布 versionNo=1、第二次发布 versionNo=2）。
 */
class AppApplicationServiceTest {

    private val appRepository = mock(AppRepository::class.java)
    private val versionRepository = mock(AppVersionRepository::class.java)
    private val service = AppApplicationService(appRepository, versionRepository)

    private fun app(id: Long = 1L, tenantId: Long = 1L, status: AppStatus = AppStatus.DRAFT) = App(
        id = id, tenantId = tenantId, appKey = "app_x", name = "旧名",
        description = "", iconUri = "", status = status,
        createBy = null, createTime = null, updateTime = null,
    )

    // 模拟仓储 insert 后回填自增主键 id 的真实行为（IdType.AUTO）
    private fun backfillId() = Answer<App> { invocation: InvocationOnMock ->
        invocation.getArgument<App>(0).copy(id = 1L)
    }

    // ---- Mockito 与 Kotlin 非空参数的适配 ----
    // Mockito 的 any()/argThat() 运行时返回 null，而 Kotlin 会对非空参数在调用点插入
    // 编译期非空校验（Intrinsics.checkNotNullExpressionValue），导致 mock 未被调用、匹配器残留。
    // 这里先调用 any()/argThat() 注册"任意匹配"语义，再返回一个非空占位实例，以绕过该校验。
    private fun anyApp(): App {
        any(App::class.java)
        return app()
    }

    private fun anyVersion(): AppVersion {
        any(AppVersion::class.java)
        return AppVersion(
            id = null, tenantId = 1L, appId = 1L, versionNo = 1,
            workflowDsl = null, modelSnapshot = null, status = 1, publishBy = null, publishTime = null,
        )
    }

    private fun appThat(predicate: (App) -> Boolean): App {
        org.mockito.ArgumentMatchers.argThat<App> { predicate(it) }
        return app()
    }

    @Test
    fun `创建应用生成appKey并返回草稿`() {
        `when`(appRepository.save(anyApp())).thenAnswer(backfillId())
        val created = service.create(1L, "客服助手", "售前咨询", "")
        assertNotNull(created.id)
        assert(created.appKey.startsWith("app_"))
        assertEquals(AppStatus.DRAFT, created.status)
    }

    @Test
    fun `查询不存在应用抛业务异常`() {
        `when`(appRepository.findById(1L, 99L)).thenReturn(null)
        assertThrows(BizException::class.java) { service.get(1L, 99L) }
    }

    @Test
    fun `更新应用信息成功`() {
        `when`(appRepository.findById(1L, 1L)).thenReturn(app())
        `when`(appRepository.save(anyApp())).thenAnswer(backfillId())
        val updated = service.update(1L, 1L, "新名", "描述", "icon")
        assertEquals("新名", updated.name)
        assertEquals(AppStatus.DRAFT, updated.status)
    }

    @Test
    fun `更新他人应用抛403`() {
        `when`(appRepository.findById(1L, 1L)).thenReturn(app(tenantId = 2L))
        assertThrows(BizException::class.java) { service.update(1L, 1L, "x", "", "") }
    }

    @Test
    fun `删除应用成功`() {
        `when`(appRepository.findById(1L, 1L)).thenReturn(app())
        service.delete(1L, 1L)
        verify(appRepository).delete(1L, 1L)
    }

    @Test
    fun `删除他人应用抛403`() {
        `when`(appRepository.findById(1L, 1L)).thenReturn(app(tenantId = 2L))
        assertThrows(BizException::class.java) { service.delete(1L, 1L) }
    }

    @Test
    fun `首次发布版本号为1且应用置为已发布`() {
        `when`(appRepository.findById(1L, 1L)).thenReturn(app(status = AppStatus.DRAFT))
        `when`(versionRepository.nextVersionNo(1L, 1L)).thenReturn(1)
        `when`(versionRepository.save(anyVersion())).thenAnswer { invocation ->
            invocation.getArgument<AppVersion>(0).copy(id = 99L)
        }
        `when`(appRepository.save(anyApp())).thenAnswer(backfillId())

        val v = service.publish(1L, 1L)
        assertEquals(1, v.versionNo)
        assertEquals(1, v.status)
        // 发布成功必须把应用状态落库为已发布
        verify(appRepository).save(appThat { it.status == AppStatus.PUBLISHED })
    }

    @Test
    fun `第二次发布版本号为2`() {
        // 已发布过的应用可重复发布：版本表已有 1 条历史 → 新版本号 2
        `when`(appRepository.findById(1L, 1L)).thenReturn(app(status = AppStatus.PUBLISHED))
        `when`(versionRepository.nextVersionNo(1L, 1L)).thenReturn(2)
        `when`(versionRepository.save(anyVersion())).thenAnswer { invocation ->
            invocation.getArgument<AppVersion>(0).copy(id = 99L)
        }
        `when`(appRepository.save(anyApp())).thenAnswer(backfillId())

        val v = service.publish(1L, 1L)
        assertEquals(2, v.versionNo)
    }

    @Test
    fun `下线应用不可发布抛业务异常`() {
        `when`(appRepository.findById(1L, 1L)).thenReturn(app(status = AppStatus.OFFLINE))
        assertThrows(BizException::class.java) { service.publish(1L, 1L) }
    }

    @Test
    fun `创建应用记录审计`() {
        `when`(appRepository.save(anyApp())).thenAnswer(backfillId())
        // 捕获型 AuditRecorder：验证 create 成功后记录 APP_CREATE 审计
        val auditLogs = mutableListOf<AuditLog>()
        val serviceWithAudit = AppApplicationService(
            appRepository,
            versionRepository,
            AuditRecorder { auditLogs.add(it) },
        )
        serviceWithAudit.create(1L, "客服助手", "售前咨询", "")

        assertEquals(1, auditLogs.size)
        assertEquals("APP_CREATE", auditLogs.first().action)
        assertEquals("app", auditLogs.first().targetType)
        assertEquals(1L, auditLogs.first().targetId)
        assertEquals(1L, auditLogs.first().tenantId)
    }
}

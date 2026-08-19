package com.zhijin.app

import com.zhijin.app.entity.App
import com.zhijin.app.entity.AppVersion
import com.zhijin.app.mapper.AppMapper
import com.zhijin.app.mapper.AppVersionMapper
import com.zhijin.app.service.PublishService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

class PublishServiceTest {

    private val appMapper = mock(AppMapper::class.java)
    private val versionMapper = mock(AppVersionMapper::class.java)
    private val service = PublishService(appMapper, versionMapper)

    @Test
    fun `首次发布版本号为1`() {
        // 模拟应用存在（草稿态 status=0），版本表无历史记录
        `when`(appMapper.selectById(1L)).thenReturn(App(id = 1L, tenantId = 1L, appKey = "app_x", name = "x", status = 0))
        `when`(versionMapper.selectCount(any())).thenReturn(0L)
        // 模拟 MyBatis-Plus insert 后回填自增主键 id（IdType.AUTO 的真实行为）
        `when`(versionMapper.insert(any(AppVersion::class.java))).thenAnswer(object : Answer<Int> {
            override fun answer(invocation: InvocationOnMock): Int {
                invocation.getArgument<AppVersion>(0).id = 99L
                return 1
            }
        })
        val v = service.publish(1L, 1L)
        assertEquals(1, v.versionNo)
    }

    @Test
    fun `第二次发布版本号为2`() {
        // 模拟应用已发布过（status=1），版本表已有 1 条历史
        `when`(appMapper.selectById(1L)).thenReturn(App(id = 1L, tenantId = 1L, appKey = "app_x", name = "x", status = 1))
        `when`(versionMapper.selectCount(any())).thenReturn(1L)
        // 同样需要回填 insert 的自增主键 id，否则 service 内 id!! 会 NPE
        `when`(versionMapper.insert(any(AppVersion::class.java))).thenAnswer(object : Answer<Int> {
            override fun answer(invocation: InvocationOnMock): Int {
                invocation.getArgument<AppVersion>(0).id = 99L
                return 1
            }
        })
        val v = service.publish(1L, 1L)
        assertEquals(2, v.versionNo)
    }
}

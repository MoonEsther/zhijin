package com.zhijin.app

import com.zhijin.app.dto.AppRequest
import com.zhijin.app.infrastructure.persistence.AppRecord
import com.zhijin.app.mapper.AppMapper
import com.zhijin.app.service.AppService
import com.zhijin.common.exception.BizException
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

class AppServiceTest {

    private val mapper = mock(AppMapper::class.java)
    private val service = AppService(mapper)

    @Test
    fun `创建应用生成appKey并返回`() {
        // 模拟 MyBatis-Plus insert 后回填自增主键 id（IdType.AUTO 的真实行为）
        `when`(mapper.insert(any(AppRecord::class.java))).thenAnswer(object : Answer<Int> {
            override fun answer(invocation: InvocationOnMock): Int {
                invocation.getArgument<AppRecord>(0).id = 1L
                return 1
            }
        })
        val created = service.create(1L, AppRequest(name = "客服助手", description = "售前咨询"))
        assertNotNull(created.id)
        assert(created.appKey.startsWith("app_"))
    }

    @Test
    fun `查询不存在应用抛业务异常`() {
        `when`(mapper.selectById(99L)).thenReturn(null)
        assertThrows(BizException::class.java) { service.get(1L, 99L) }
    }
}

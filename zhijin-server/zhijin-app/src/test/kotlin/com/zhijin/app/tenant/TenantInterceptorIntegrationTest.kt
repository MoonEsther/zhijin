package com.zhijin.app.tenant

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.zhijin.framework.entity.SysRole
import com.zhijin.framework.mapper.SysRoleMapper
import com.zhijin.framework.tenant.TenantContextHolder
import com.zhijin.common.context.TenantContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class TenantInterceptorIntegrationTest {

    @Autowired
    lateinit var roleMapper: SysRoleMapper

    @AfterEach
    fun cleanUp() {
        TenantContext.clear()
    }

    @Test
    fun `插入自动填充租户, 查询按租户隔离`() {
        // 注意：role_code 不能用 "admin" —— AdminSeeder 已为租户1播种 admin 角色，
        // 会撞 uk_sys_role_tenant_code 唯一索引；改用 viewer 保持"插入+隔离"断言意图不变。
        TenantContextHolder.setTenantId(1L)
        val role = SysRole(roleCode = "viewer", roleName = "只读")
        roleMapper.insert(role)
        assertTrue(role.id != null)
        assertEquals(1L, role.tenantId)

        // 租户2 查不到租户1的数据（SQL 层自动加 WHERE tenant_id = 2）
        TenantContextHolder.setTenantId(2L)
        val list = roleMapper.selectList(
            QueryWrapper<SysRole>().eq("role_code", "viewer")
        )
        assertTrue(list.isEmpty())
    }
}

package com.zhijin.auth

import com.zhijin.auth.application.OrgApplicationService
import com.zhijin.auth.domain.organization.Organization
import com.zhijin.auth.domain.organization.OrganizationRepository
import com.zhijin.auth.domain.role.RoleRepository
import com.zhijin.common.exception.BizException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/**
 * OrgApplicationService 单元测试：模拟组织仓储 + 角色仓储。
 * 覆盖组织树组装、父子组织校验、组织级角色分配前置校验。
 */
class OrgApplicationServiceTest {

    private val orgRepository = mock(OrganizationRepository::class.java)
    private val roleRepository = mock(RoleRepository::class.java)
    private val service = OrgApplicationService(orgRepository, roleRepository)

    // Mockito 与 Kotlin 非空参数适配：先注册 any() 匹配，再返回非空占位实例
    private fun anyOrg(): Organization {
        any(Organization::class.java)
        return org(0, 0, "")
    }

    private fun org(id: Long, parentId: Long, orgName: String) =
        Organization(id = id, tenantId = 1L, parentId = parentId, orgName = orgName, sort = 0, status = 1)

    @Test
    fun `tree组装父子层级`() {
        // 根组织(1) → 子组织(2)，以及独立的第二个根组织(3)
        `when`(orgRepository.listByTenant(1L)).thenReturn(
            listOf(
                org(1, 0, "根组织"),
                org(2, 1, "研发部"),
                org(3, 0, "根组织2"),
            )
        )
        val tree = service.tree(1L)
        assertEquals(2, tree.size)
        // 第一个根组织下挂研发部
        assertEquals("根组织", tree[0].orgName)
        assertEquals(1, tree[0].children.size)
        assertEquals("研发部", tree[0].children[0].orgName)
        assertEquals("根组织2", tree[1].orgName)
        assertEquals(0, tree[1].children.size)
    }

    @Test
    fun `tree空列表返回空`() {
        `when`(orgRepository.listByTenant(1L)).thenReturn(emptyList())
        assertEquals(0, service.tree(1L).size)
    }

    @Test
    fun `create名称为空抛业务异常`() {
        assertThrows(BizException::class.java) { service.create(1L, 0L, " ", 0) }
    }

    @Test
    fun `create父组织不存在抛业务异常`() {
        `when`(orgRepository.findById(1L, 99L)).thenReturn(null)
        assertThrows(BizException::class.java) { service.create(1L, 99L, "研发部", 0) }
    }

    @Test
    fun `create父组织存在时创建成功`() {
        `when`(orgRepository.findById(1L, 1L)).thenReturn(org(1, 0, "根组织"))
        // 服务层以 id=null 构造新组织传给 save，故用 anyOrg() 匹配并回填 id=2
        `when`(orgRepository.save(anyLong(), anyOrg())).thenAnswer { inv ->
            inv.getArgument<Organization>(1).copy(id = 2L)
        }
        val created = service.create(1L, 1L, "研发部", 0)
        assertEquals("研发部", created.orgName)
        assertEquals(1L, created.parentId)
        assertEquals(2L, created.id)
    }

    @Test
    fun `update组织不存在抛业务异常`() {
        `when`(orgRepository.findById(1L, 99L)).thenReturn(null)
        assertThrows(BizException::class.java) { service.update(1L, 99L, 0L, "研发部", 0, 1) }
    }

    @Test
    fun `delete组织不存在抛业务异常`() {
        `when`(orgRepository.findById(1L, 99L)).thenReturn(null)
        assertThrows(BizException::class.java) { service.delete(1L, 99L) }
    }

    @Test
    fun `assignRoles组织不存在抛业务异常`() {
        `when`(orgRepository.findById(1L, 99L)).thenReturn(null)
        assertThrows(BizException::class.java) { service.assignRoles(1L, 99L, listOf(1L)) }
    }

    @Test
    fun `assignRoles成功重设组织角色`() {
        `when`(orgRepository.findById(1L, 1L)).thenReturn(org(1, 0, "根组织"))
        service.assignRoles(1L, 1L, listOf(1L, 2L))
        verify(roleRepository).assignOrgRoles(1L, 1L, listOf(1L, 2L))
    }
}

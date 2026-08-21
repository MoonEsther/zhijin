package com.zhijin.auth

import com.zhijin.auth.application.RbacApplicationService
import com.zhijin.auth.domain.permission.Permission
import com.zhijin.auth.domain.permission.PermissionRepository
import com.zhijin.auth.domain.role.Role
import com.zhijin.auth.domain.role.RoleRepository
import com.zhijin.auth.domain.user.User
import com.zhijin.auth.domain.user.UserRepository
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
 * RbacApplicationService 单元测试：模拟三个仓储端口。
 * 覆盖核心 RBAC 规则：权限合并（用户角色 ∪ 组织角色）、角色唯一性校验、用户角色分配前置校验。
 */
class RbacApplicationServiceTest {

    // Mockito 与 Kotlin 非空参数适配：先注册 any() 匹配，再返回非空占位实例
    private fun anyRole(): Role {
        any(Role::class.java)
        return Role(id = null, tenantId = 1L, roleCode = "", roleName = "", perms = emptyList())
    }

    private val userRepository = mock(UserRepository::class.java)
    private val roleRepository = mock(RoleRepository::class.java)
    private val permissionRepository = mock(PermissionRepository::class.java)
    private val service = RbacApplicationService(userRepository, roleRepository, permissionRepository)

    private fun user(orgId: Long? = null) = User(
        id = 1L, tenantId = 1L, username = "u", password = "", nickname = "", status = 1, orgId = orgId
    )

    @Test
    fun `getPerms合并用户角色与组织角色并去重`() {
        // 用户直接绑定 admin 角色，所属组织(orgId=10)绑定 viewer 角色
        `when`(userRepository.findById(1L, 1L)).thenReturn(user(orgId = 10L))
        `when`(roleRepository.findRolesByUserId(1L, 1L)).thenReturn(
            listOf(Role(id = 1L, tenantId = 1L, roleCode = "admin", roleName = "管理员", perms = listOf("app:view", "app:create")))
        )
        `when`(roleRepository.findRolesByOrgId(1L, 10L)).thenReturn(
            listOf(Role(id = 2L, tenantId = 1L, roleCode = "viewer", roleName = "只读", perms = listOf("app:view", "usage:view")))
        )
        // app:view 在用户角色与组织角色中重复，distinct 后应只保留一次
        assertEquals(listOf("app:view", "app:create", "usage:view"), service.getPerms(1L, 1L))
    }

    @Test
    fun `getPerms用户无组织时仅返回用户角色`() {
        `when`(userRepository.findById(1L, 1L)).thenReturn(user(orgId = null))
        `when`(roleRepository.findRolesByUserId(1L, 1L)).thenReturn(
            listOf(Role(id = 1L, tenantId = 1L, roleCode = "admin", roleName = "管理员", perms = listOf("app:view")))
        )
        // orgId 为空 → 组织角色查询不应被触发
        assertEquals(listOf("app:view"), service.getPerms(1L, 1L))
        verify(roleRepository, org.mockito.Mockito.never()).findRolesByOrgId(anyLong(), anyLong())
    }

    @Test
    fun `getPerms同一角色同时被用户与组织绑定时按角色去重`() {
        `when`(userRepository.findById(1L, 1L)).thenReturn(user(orgId = 10L))
        `when`(roleRepository.findRolesByUserId(1L, 1L)).thenReturn(
            listOf(Role(id = 1L, tenantId = 1L, roleCode = "admin", roleName = "管理员", perms = listOf("app:create")))
        )
        // 组织绑定了同一角色 id=1（distinctBy { it.id } 应只保留一个，避免重复扁平化）
        `when`(roleRepository.findRolesByOrgId(1L, 10L)).thenReturn(
            listOf(Role(id = 1L, tenantId = 1L, roleCode = "admin", roleName = "管理员", perms = listOf("app:create")))
        )
        assertEquals(listOf("app:create"), service.getPerms(1L, 1L))
    }

    @Test
    fun `createRole编码为空抛业务异常`() {
        assertThrows(BizException::class.java) { service.createRole(1L, " ", "管理员", emptyList()) }
    }

    @Test
    fun `createRole编码重复抛业务异常`() {
        `when`(roleRepository.findByCode(1L, "admin")).thenReturn(
            Role(id = 1L, tenantId = 1L, roleCode = "admin", roleName = "管理员", perms = emptyList())
        )
        assertThrows(BizException::class.java) { service.createRole(1L, "admin", "管理员", emptyList()) }
    }

    @Test
    fun `createRole成功保存并透传返回`() {
        `when`(roleRepository.findByCode(1L, "dev")).thenReturn(null)
        // validatePerms 会先查权限字典确认 permCode 合法
        `when`(permissionRepository.findAll()).thenReturn(
            listOf(Permission(id = 1L, permCode = "app:view", permName = "查看应用", parentId = 0L))
        )
        // 注意：一旦使用 any() 匹配器，所有参数都必须是匹配器（eq/anyLong/anyRole）
        `when`(roleRepository.save(anyLong(), anyRole())).thenAnswer { inv ->
            inv.getArgument<Role>(1).copy(id = 100L)
        }
        val result = service.createRole(1L, "dev", "开发者", listOf("app:view"))
        assertEquals(100L, result.id)
        assertEquals("dev", result.roleCode)
        assertEquals(listOf("app:view"), result.perms)
        // 保存时权限点透传到仓储（由仓储落库关联表）
        verify(roleRepository).save(1L, Role(id = null, tenantId = 1L, roleCode = "dev", roleName = "开发者", perms = listOf("app:view")))
    }

    @Test
    fun `createRole非法权限点拒绝且不写库`() {
        `when`(roleRepository.findByCode(1L, "dev")).thenReturn(null)
        // 权限字典中不存在 "no:perm"，validatePerms 应在写库前抛出
        `when`(permissionRepository.findAll()).thenReturn(
            listOf(Permission(id = 1L, permCode = "app:view", permName = "查看应用", parentId = 0L))
        )
        assertThrows(BizException::class.java) { service.createRole(1L, "dev", "开发者", listOf("no:perm")) }
        // 校验失败在写库前，save 不应被调用（配合 @Transactional 双保险）
        verify(roleRepository, org.mockito.Mockito.never()).save(anyLong(), anyRole())
    }

    @Test
    fun `updateRole角色不存在抛业务异常`() {
        `when`(roleRepository.findById(1L, 99L)).thenReturn(null)
        assertThrows(BizException::class.java) { service.updateRole(1L, 99L, "dev", "开发者", emptyList()) }
    }

    @Test
    fun `updateRole编码冲突排除自身`() {
        val existing = Role(id = 1L, tenantId = 1L, roleCode = "admin", roleName = "管理员", perms = emptyList())
        `when`(roleRepository.findById(1L, 1L)).thenReturn(existing)
        // 编码仍为 admin 但属于自身 → 不视为冲突
        `when`(roleRepository.findByCode(1L, "admin")).thenReturn(existing)
        `when`(roleRepository.save(anyLong(), anyRole())).thenAnswer { inv -> inv.getArgument<Role>(1) }
        // 更新后 roleCode 不变、roleName 生效（证明"编码冲突排除自身"未误判）
        val result = service.updateRole(1L, 1L, "admin", "超级管理员", emptyList())
        assertEquals("admin", result.roleCode)
        assertEquals("超级管理员", result.roleName)
    }

    @Test
    fun `updateRole编码被其他角色占用抛业务异常`() {
        val existing = Role(id = 1L, tenantId = 1L, roleCode = "admin", roleName = "管理员", perms = emptyList())
        val other = Role(id = 2L, tenantId = 1L, roleCode = "dev", roleName = "开发者", perms = emptyList())
        `when`(roleRepository.findById(1L, 1L)).thenReturn(existing)
        `when`(roleRepository.findByCode(1L, "dev")).thenReturn(other)
        assertThrows(BizException::class.java) { service.updateRole(1L, 1L, "dev", "管理员", emptyList()) }
    }

    @Test
    fun `deleteRole角色不存在抛业务异常`() {
        `when`(roleRepository.findById(1L, 99L)).thenReturn(null)
        assertThrows(BizException::class.java) { service.deleteRole(1L, 99L) }
    }

    @Test
    fun `assignUserRoles用户不存在抛业务异常`() {
        `when`(userRepository.findById(1L, 99L)).thenReturn(null)
        assertThrows(BizException::class.java) { service.assignUserRoles(1L, 99L, listOf(1L)) }
    }

    @Test
    fun `assignUserRoles成功重设角色`() {
        `when`(userRepository.findById(1L, 1L)).thenReturn(user())
        service.assignUserRoles(1L, 1L, listOf(1L, 2L))
        verify(roleRepository).assignUserRoles(1L, 1L, listOf(1L, 2L))
    }

    @Test
    fun `listPermissions返回平台全部权限点`() {
        `when`(permissionRepository.findAll()).thenReturn(
            listOf(Permission(id = 1L, permCode = "app:view", permName = "查看应用", parentId = 0L))
        )
        assertEquals(1, service.listPermissions().size)
        assertEquals("app:view", service.listPermissions()[0].permCode)
    }

    @Test
    fun `listUsers返回用户角色ID与角色名`() {
        // 用户 u 绑定角色 id=1（管理员）、id=2（只读）；listUsers 需把 roleIds 解析为 roleNames
        `when`(userRepository.listByTenant(1L)).thenReturn(listOf(user()))
        `when`(roleRepository.findRoleIdsByUserId(1L, 1L)).thenReturn(listOf(1L, 2L))
        `when`(roleRepository.listByTenant(1L)).thenReturn(
            listOf(
                Role(id = 1L, tenantId = 1L, roleCode = "admin", roleName = "管理员", perms = emptyList()),
                Role(id = 2L, tenantId = 1L, roleCode = "viewer", roleName = "只读", perms = emptyList()),
            )
        )
        val users = service.listUsers(1L)
        assertEquals(1, users.size)
        assertEquals(listOf(1L, 2L), users[0].roleIds)
        // roleNames 按 roleIds 顺序解析，前端「当前角色」列直接渲染该字段
        assertEquals(listOf("管理员", "只读"), users[0].roleNames)
    }
}

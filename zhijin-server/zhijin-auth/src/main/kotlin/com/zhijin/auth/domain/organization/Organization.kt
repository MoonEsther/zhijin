package com.zhijin.auth.domain.organization

/**
 * 组织领域实体（富血模型，纯 Kotlin）。
 *
 * 对应 sys_organization 表。parent_id=0 表示根组织，节点通过 parent_id 构成组织树。
 * 用户通过 org_id 归属组织，组织通过 sys_org_role 绑定角色（组织级角色授权），
 * 实现"用户权限 = 用户角色 ∪ 组织角色"的继承语义。
 */
data class Organization(
    val id: Long?,
    val tenantId: Long,
    val parentId: Long,
    val orgName: String,
    val sort: Int,
    val status: Int,
)

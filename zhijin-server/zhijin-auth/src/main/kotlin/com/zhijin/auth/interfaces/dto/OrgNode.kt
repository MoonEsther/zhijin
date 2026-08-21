package com.zhijin.auth.interfaces.dto

/**
 * 组织节点（响应 DTO）：组织树节点。children 为子组织（叶子为空列表）。
 * 同时被 OrgApplicationService.tree 组装使用（本项目约定 application 可依赖 interfaces/dto，见 AuthApplicationService）。
 */
data class OrgNode(
    val id: Long,
    val parentId: Long,
    val orgName: String,
    val sort: Int,
    val status: Int,
    val children: List<OrgNode> = emptyList(),
)

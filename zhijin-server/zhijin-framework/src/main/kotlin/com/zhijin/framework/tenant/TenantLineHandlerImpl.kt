package com.zhijin.framework.tenant

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler
import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.expression.LongValue

/**
 * 租户行级处理：指定租户列、从上下文取当前租户 ID、声明忽略租户过滤的系统表。
 * 忽略表：sys_tenant（租户表本身）、sys_permission（平台级权限字典）、flyway_schema_history。
 */
class TenantLineHandlerImpl : TenantLineHandler {

    companion object {
        const val TENANT_COLUMN = "tenant_id"
        val IGNORE_TABLES = setOf("sys_tenant", "sys_permission", "flyway_schema_history")
    }

    override fun getTenantId(): Expression = LongValue(TenantContextHolder.getTenantId() ?: 0L)

    override fun getTenantIdColumn(): String = TENANT_COLUMN

    override fun ignoreTable(tableName: String): Boolean = tableName in IGNORE_TABLES
}

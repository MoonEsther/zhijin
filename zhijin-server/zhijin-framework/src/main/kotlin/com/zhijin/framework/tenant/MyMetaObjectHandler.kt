package com.zhijin.framework.tenant

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler
import org.apache.ibatis.reflection.MetaObject
import java.time.LocalDateTime

/**
 * 字段自动填充：insert 时填 tenant_id + create_time + update_time，
 * update 时填 update_time。实体字段需配 @TableField(fill = FieldFill.INSERT)。
 */
class MyMetaObjectHandler : MetaObjectHandler {

    override fun insertFill(metaObject: MetaObject) {
        strictInsertFill(metaObject, "tenantId", Long::class.javaObjectType, TenantContextHolder.getTenantId() ?: 0L)
        strictInsertFill(metaObject, "createTime", LocalDateTime::class.java, LocalDateTime.now())
        strictInsertFill(metaObject, "updateTime", LocalDateTime::class.java, LocalDateTime.now())
    }

    override fun updateFill(metaObject: MetaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime::class.java, LocalDateTime.now())
    }
}

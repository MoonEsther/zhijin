package com.zhijin.billingaudit.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.billingaudit.infrastructure.persistence.AuditLogRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface AuditLogMapper : BaseMapper<AuditLogRecord>

package com.zhijin.billingaudit.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.billingaudit.infrastructure.persistence.UsageRecordRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface UsageRecordMapper : BaseMapper<UsageRecordRecord>

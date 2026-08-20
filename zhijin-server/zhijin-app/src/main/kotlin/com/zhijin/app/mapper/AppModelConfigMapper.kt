package com.zhijin.app.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.app.infrastructure.persistence.AppModelConfigRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface AppModelConfigMapper : BaseMapper<AppModelConfigRecord>

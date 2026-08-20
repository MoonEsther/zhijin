package com.zhijin.app.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.app.infrastructure.persistence.AppRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface AppMapper : BaseMapper<AppRecord>

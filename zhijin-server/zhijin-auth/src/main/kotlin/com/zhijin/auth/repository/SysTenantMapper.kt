package com.zhijin.auth.repository

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.auth.entity.SysTenant
import org.apache.ibatis.annotations.Mapper

@Mapper
interface SysTenantMapper : BaseMapper<SysTenant>

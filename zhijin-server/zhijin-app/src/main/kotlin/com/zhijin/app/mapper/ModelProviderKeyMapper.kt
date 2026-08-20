package com.zhijin.app.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.app.infrastructure.persistence.ModelProviderKeyRecord
import org.apache.ibatis.annotations.Mapper

@Mapper
interface ModelProviderKeyMapper : BaseMapper<ModelProviderKeyRecord>

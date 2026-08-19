package com.zhijin.framework.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.framework.entity.SysRole
import org.apache.ibatis.annotations.Mapper

/** 角色 Mapper：由 MyBatis-Plus 提供通用 CRUD，配合租户拦截器实现数据隔离。 */
@Mapper
interface SysRoleMapper : BaseMapper<SysRole>

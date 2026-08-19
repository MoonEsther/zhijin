package com.zhijin.auth.repository

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.auth.entity.SysUser
import org.apache.ibatis.annotations.Mapper

@Mapper
interface SysUserMapper : BaseMapper<SysUser>, SysUserRepository {
    override fun findByUsername(username: String): SysUser? =
        selectOne(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>().eq("username", username))
}

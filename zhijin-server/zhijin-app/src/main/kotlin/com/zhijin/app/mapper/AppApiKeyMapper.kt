package com.zhijin.app.mapper

import com.baomidou.mybatisplus.annotation.InterceptorIgnore
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.app.infrastructure.persistence.AppApiKeyRecord
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface AppApiKeyMapper : BaseMapper<AppApiKeyRecord> {

    /**
     * 按明文 Key 的 SHA-256 哈希反查 API Key（开放 API /v1 鉴权用）。
     * 必须绕过租户拦截器：调用时租户尚未确定（租户本身由 Key 解析而来），
     * 若让租户拦截器自动拼上 tenant_id=0，将永远查不到有效 Key。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM app_api_key WHERE key_hash = #{hash} LIMIT 1")
    fun findByHash(@Param("hash") hash: String): AppApiKeyRecord?
}

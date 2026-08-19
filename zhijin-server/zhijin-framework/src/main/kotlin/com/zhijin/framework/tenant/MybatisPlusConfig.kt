package com.zhijin.framework.tenant

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor
import org.mybatis.spring.annotation.MapperScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** MyBatis-Plus 插件装配：租户隔离 + 分页。顺序：租户在前，分页在后。 */
@Configuration
@MapperScan("com.zhijin.framework.mapper")
class MybatisPlusConfig {

    @Bean
    fun mybatisPlusInterceptor(): MybatisPlusInterceptor =
        MybatisPlusInterceptor().apply {
            addInnerInterceptor(TenantLineInnerInterceptor(TenantLineHandlerImpl()))
            addInnerInterceptor(PaginationInnerInterceptor())
        }

    @Bean
    fun metaObjectHandler(): MyMetaObjectHandler = MyMetaObjectHandler()
}

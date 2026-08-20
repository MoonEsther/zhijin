package com.zhijin.framework.tenant

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor
import org.apache.ibatis.annotations.Mapper
import org.mybatis.spring.annotation.MapperScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** MyBatis-Plus 插件装配：租户隔离 + 分页。顺序：租户在前，分页在后。 */
@Configuration
@MapperScan(
    basePackages = ["com.zhijin.framework.mapper", "com.zhijin.auth.repository", "com.zhijin.app.mapper", "com.zhijin.chat.mapper"],
    // 只注册 @Mapper 注解的接口，避免把同包的仓储抽象接口(SysUserRepository)也注册成 Mapper Bean
    annotationClass = Mapper::class,
)
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

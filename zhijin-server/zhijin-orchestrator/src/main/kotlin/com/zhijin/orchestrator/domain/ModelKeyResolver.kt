package com.zhijin.orchestrator.domain

/**
 * 模型 Key 解析端口（依赖倒置，解决 C1 模块依赖方向问题）。
 * 实现在 zhijin-app（有 ModelProviderKey 表访问权限），通过适配 Bean 注入。
 * 签名不含 tenantId（解决 N1）：适配 Bean 内部从 TenantContextHolder 取。
 */
fun interface ModelKeyResolver {
    /**
     * 根据 Key ID 返回解密后的明文 Key。
     * 返回 null 表示 Key 不存在或已禁用。
     */
    fun resolvePlainKey(providerKeyId: Long): String?
}

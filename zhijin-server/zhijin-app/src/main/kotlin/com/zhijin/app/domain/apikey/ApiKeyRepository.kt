package com.zhijin.app.domain.apikey

/** 应用 API Key 仓储接口（依赖倒置：实现放 infrastructure）。 */
interface ApiKeyRepository {

    /** 新增/更新 API Key（insert 回填自增 id 后返回领域实体）。 */
    fun save(key: AppApiKey): AppApiKey

    /** 按 id 查询（租户不匹配返回 null）。 */
    fun findById(tenantId: Long, keyId: Long): AppApiKey?

    /**
     * 按哈希反查 API Key（开放 API /v1 鉴权用）。
     * 实现必须委托给带 @InterceptorIgnore(tenantLine="true") 的 Mapper 方法：
     * 调用时租户尚未确定（租户本身由 Key 解析而来），须绕开租户拦截器。
     */
    fun findByHash(hash: String): AppApiKey?

    /** 按租户+应用+哈希查询启用态 Key（校验用）；不存在或已吊销返回 null。 */
    fun findActiveByHash(tenantId: Long, appId: Long, hash: String): AppApiKey?
}

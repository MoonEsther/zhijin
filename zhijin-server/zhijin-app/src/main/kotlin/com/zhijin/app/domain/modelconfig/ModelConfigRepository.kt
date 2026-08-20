package com.zhijin.app.domain.modelconfig

/** 模型配置仓储接口（依赖倒置：实现放 infrastructure）。 */
interface ModelConfigRepository {

    /** 新增/更新供应商 Key（insert 回填自增 id 后返回领域实体）。 */
    fun saveProviderKey(key: ModelProviderKey): ModelProviderKey

    /** 按 id 查询供应商 Key（租户不匹配返回 null）。 */
    fun findProviderKeyById(tenantId: Long, keyId: Long): ModelProviderKey?

    /** 查询应用模型配置（每个应用一条）。 */
    fun findConfig(tenantId: Long, appId: Long): AppModelConfig?

    /** 新增/更新应用模型配置（insert 回填自增 id 后返回领域实体）。 */
    fun saveConfig(config: AppModelConfig): AppModelConfig
}

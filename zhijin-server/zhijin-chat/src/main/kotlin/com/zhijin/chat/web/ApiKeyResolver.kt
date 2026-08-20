package com.zhijin.chat.web

/**
 * 开放 API 鉴权解析器：把明文 API Key 解析为 (租户ID, 应用ID)。
 *
 * 为何用函数式接口而非直接依赖 zhijin-app 的 AppApiKeyService：
 * zhijin-app 已经依赖 zhijin-chat（运行时装配需要），若 zhijin-chat 反向依赖 zhijin-app
 * 会构成 Maven 模块循环依赖。故以本接口解耦，由 zhijin-app 提供适配 Bean 实现。
 */
fun interface ApiKeyResolver {

    /** 通过明文 Key 反查租户+应用；无效返回 null。 */
    fun findByPlainKey(plainKey: String): Pair<Long, Long>?
}

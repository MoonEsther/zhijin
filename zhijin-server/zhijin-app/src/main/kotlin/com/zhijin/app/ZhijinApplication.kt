package com.zhijin.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 平台服务启动入口（Kotlin + Spring Boot 4）。
 *
 * 注解说明：
 * - scanBasePackages 覆盖全部 zhijin 模块，各模块通过 Spring 组件扫描装配。
 */
@SpringBootApplication(scanBasePackages = ["com.zhijin"])
class ZhijinApplication

fun main(args: Array<String>) {
    runApplication<ZhijinApplication>(*args)
}

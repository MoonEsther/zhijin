package com.zhijin.orchestrator.domain

/** 显式边（决策 18）：管拓扑，输入引用管数据绑定。 */
data class Connection(val fromNode: String, val toNode: String)

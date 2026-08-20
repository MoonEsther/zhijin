package com.zhijin.chat.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.chat.infrastructure.persistence.ChatMessageRecord
import org.apache.ibatis.annotations.Mapper

/**
 * 消息 Mapper（持久化层）：只处理 [ChatMessageRecord]，返回记录后由
 * SessionRepositoryImpl 转领域实体。保持 @Mapper 与包路径不变（@MapperScan 硬编码扫描）。
 */
@Mapper
interface ChatMessageMapper : BaseMapper<ChatMessageRecord>

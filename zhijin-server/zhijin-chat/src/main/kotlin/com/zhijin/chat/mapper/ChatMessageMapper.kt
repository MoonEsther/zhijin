package com.zhijin.chat.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.chat.entity.ChatMessage
import org.apache.ibatis.annotations.Mapper

@Mapper
interface ChatMessageMapper : BaseMapper<ChatMessage>

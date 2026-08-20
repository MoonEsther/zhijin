package com.zhijin.chat.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.zhijin.chat.entity.ChatSession
import org.apache.ibatis.annotations.Mapper

@Mapper
interface ChatSessionMapper : BaseMapper<ChatSession>

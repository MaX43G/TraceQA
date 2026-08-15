package edu.zjut.traceqa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zjut.traceqa.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息数据访问接口。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
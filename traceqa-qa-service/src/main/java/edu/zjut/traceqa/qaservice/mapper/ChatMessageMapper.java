package edu.zjut.traceqa.qaservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zjut.traceqa.common.model.po.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息数据访问接口。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
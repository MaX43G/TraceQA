package edu.zjut.traceqa.qaservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zjut.traceqa.common.model.po.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天会话数据访问接口。
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
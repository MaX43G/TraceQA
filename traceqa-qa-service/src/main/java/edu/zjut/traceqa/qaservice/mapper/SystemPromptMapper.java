package edu.zjut.traceqa.qaservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zjut.traceqa.common.model.po.SystemPrompt;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统提示词数据访问接口。
 */
@Mapper
public interface SystemPromptMapper extends BaseMapper<SystemPrompt> {
}
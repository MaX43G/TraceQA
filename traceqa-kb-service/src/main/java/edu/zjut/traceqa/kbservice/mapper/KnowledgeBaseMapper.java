package edu.zjut.traceqa.kbservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zjut.traceqa.common.model.po.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库数据访问接口。
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
}
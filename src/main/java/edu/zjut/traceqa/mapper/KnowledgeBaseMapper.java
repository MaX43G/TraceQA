package edu.zjut.traceqa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zjut.traceqa.model.po.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库数据访问接口。
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {
}
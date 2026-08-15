package edu.zjut.traceqa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zjut.traceqa.entity.Document;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档数据访问接口。
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}
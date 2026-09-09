package edu.zjut.traceqa.kbservice.lightrag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zjut.traceqa.common.model.po.LightRagChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * LightRAG 切片数据访问接口（跨库：traceqa_lightrag.lightrag_chunks）。
 */
@Mapper
public interface LightRagChunkMapper extends BaseMapper<LightRagChunk> {

    @Select("SELECT id, content, metadata, create_time FROM lightrag_chunks " +
            "WHERE metadata->>'file_path' LIKE #{pattern} ORDER BY id")
    List<LightRagChunk> selectByFilePathPattern(String pattern);
}

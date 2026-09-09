package edu.zjut.traceqa.common.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * LightRAG 文档切片（对应 traceqa_lightrag.lightrag_chunks 表）。
 */
@Data
@TableName("lightrag_chunks")
public class LightRagChunk {

    @TableId(type = IdType.INPUT)
    private String id;

    private String content;

    private String metadata;

    private LocalDateTime createTime;
}

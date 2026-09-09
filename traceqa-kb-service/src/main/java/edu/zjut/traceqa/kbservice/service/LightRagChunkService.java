package edu.zjut.traceqa.kbservice.service;

import edu.zjut.traceqa.common.model.po.LightRagChunk;
import edu.zjut.traceqa.kbservice.lightrag.LightRagChunkMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 从 LightRAG 数据库（lightrag_chunks 表）读取文档切片内容。
 *
 * <p>当本地文件丢失时，作为 reindexEs 的降级数据源。</p>
 */
@Service
public class LightRagChunkService {

    private static final Logger log = LoggerFactory.getLogger(LightRagChunkService.class);

    @Resource
    private LightRagChunkMapper lightRagChunkMapper;

    /**
     * 根据文件名匹配，获取该文档所有 LightRAG 切片内容并按顺序拼接。
     *
     * @param fileName 原始文件名（如 "机器学习 (周志华).md"）
     * @return 拼接后的完整文本，未找到时返回 null
     */
    public String getDocumentContent(String fileName) {
        try {
            String pattern = "%" + extractBaseName(fileName) + "%";
            List<LightRagChunk> chunks = lightRagChunkMapper.selectByFilePathPattern(pattern);
            if (chunks.isEmpty()) {
                log.debug("LightRAG 中未找到匹配切片：{}", fileName);
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (LightRagChunk chunk : chunks) {
                String content = chunk.getContent();
                if (content != null && !content.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append("\n\n");
                    }
                    sb.append(content);
                }
            }
            log.info("从 LightRAG 恢复文档内容：{}，切片数={}", fileName, chunks.size());
            return sb.toString();
        } catch (Exception e) {
            log.error("查询 LightRAG 切片失败：{}, err={}", fileName, e.getMessage());
            return null;
        }
    }

    private String extractBaseName(String fileName) {
        if (fileName == null) return "";
        String name = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return name.replaceAll("_part\\d+of\\d+$", "");
    }
}

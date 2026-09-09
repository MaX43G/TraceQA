package edu.zjut.traceqa.kbservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 从 LightRAG 数据库（lightrag_chunks 表）读取文档切片内容。
 */
@Service
public class LightRagChunkService {

    private static final Logger log = LoggerFactory.getLogger(LightRagChunkService.class);

    @Resource
    @Qualifier("lightRagJdbcTemplate")
    private JdbcTemplate lightRagJdbcTemplate;

    /**
     * 根据文件名匹配，获取该文档所有 LightRAG 切片内容并按顺序拼接。
     *
     * @param fileName 原始文件名（如 "机器学习 (周志华).md"）
     * @return 拼接后的完整文本，未找到时返回 null
     */
    public String getDocumentContent(String fileName) {
        try {
            String searchPattern = "%" + extractBaseName(fileName) + "%";
            List<Map<String, Object>> rows = lightRagJdbcTemplate.queryForList(
                    "SELECT content, metadata FROM lightrag_chunks WHERE metadata->>'file_path' LIKE ? ORDER BY id",
                    searchPattern);
            if (rows.isEmpty()) {
                log.debug("LightRAG 中未找到匹配切片：{}", fileName);
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> row : rows) {
                String content = (String) row.get("content");
                if (content != null && !content.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append("\n\n");
                    }
                    sb.append(content);
                }
            }
            log.info("从 LightRAG 恢复文档内容：{}，切片数={}", fileName, rows.size());
            return sb.toString();
        } catch (Exception e) {
            log.error("查询 LightRAG 切片失败：{}, err={}", fileName, e.getMessage());
            return null;
        }
    }

    /**
     * 提取文件名基础部分（去掉 _partXofY 后缀和扩展名）。
     */
    private String extractBaseName(String fileName) {
        if (fileName == null) return "";
        String name = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return name.replaceAll("_part\\d+of\\d+$", "");
    }
}

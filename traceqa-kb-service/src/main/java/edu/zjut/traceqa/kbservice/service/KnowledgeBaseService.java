package edu.zjut.traceqa.kbservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.zjut.traceqa.common.convert.DtoMapper;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.common.model.po.Document;
import edu.zjut.traceqa.common.model.po.KnowledgeBase;
import edu.zjut.traceqa.common.model.vo.KnowledgeBaseDTO;
import edu.zjut.traceqa.kbservice.mapper.DocumentMapper;
import edu.zjut.traceqa.kbservice.mapper.KnowledgeBaseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库服务。
 *
 * <p>提供知识库的增删改查与文档计数，支撑管理员后台的知识库与文档管理。
 * 系统使用<b>全部知识库</b>检索，不区分知识库、不做按库隔离（LightRAG 为单一全局索引）。</p>
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMapper documentMapper;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper, DocumentMapper documentMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMapper = documentMapper;
    }

    /**
     * 查询全部知识库
     */
    public List<KnowledgeBaseDTO> list() {
        return knowledgeBaseMapper.selectList(
                        new LambdaQueryWrapper<KnowledgeBase>()
                                .orderByAsc(KnowledgeBase::getId))
                .stream().map(KnowledgeBaseDTO::of).toList();
    }

    /**
     * 创建知识库
     */
    public KnowledgeBaseDTO create(KnowledgeBaseDTO dto) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        kb.setCourse(dto.getCourse());
        kb.setStatus(1);
        knowledgeBaseMapper.insert(kb);
        log.info("创建知识库：{}", kb.getName());
        return DtoMapper.INSTANCE.toKnowledgeBaseDTO(kb);
    }

    /**
     * 更新知识库
     */
    public KnowledgeBaseDTO update(Long id, KnowledgeBaseDTO dto) {
        KnowledgeBase kb = requireById(id);
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        kb.setCourse(dto.getCourse());
        knowledgeBaseMapper.updateById(kb);
        return DtoMapper.INSTANCE.toKnowledgeBaseDTO(kb);
    }

    /**
     * 删除知识库（同时逻辑删除其下文档）
     */
    public void delete(Long id) {
        requireById(id);
        documentMapper.delete(new LambdaQueryWrapper<Document>()
                .eq(Document::getKnowledgeBaseId, id));
        knowledgeBaseMapper.deleteById(id);
        log.info("删除知识库：{}", id);
    }

    /**
     * 校验知识库存在
     */
    public KnowledgeBase requireById(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        return kb;
    }
}
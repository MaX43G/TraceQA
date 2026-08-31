package edu.zjut.traceqa.kbservice.config;

import edu.zjut.traceqa.common.model.po.KnowledgeBase;
import edu.zjut.traceqa.kbservice.mapper.KnowledgeBaseMapper;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 知识库服务初始化数据装载。
 *
 * <p>首次启动时预置默认《数据挖掘》课程知识库。</p>
 */
@Component
@AllArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final KnowledgeBaseMapper knowledgeBaseMapper;


    /**
     * 启动装载默认知识库
     */
    @Override
    public void run(org.springframework.boot.@NonNull ApplicationArguments args) {
        try {
            initKnowledgeBase();
        } catch (Exception e) {
            log.warn("初始化「默认知识库」失败：{}", e.getMessage());
        }
    }

    /**
     * 知识库表为空时创建默认知识库
     */
    private void initKnowledgeBase() {
        Long count = knowledgeBaseMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName("《数据挖掘》课程知识库");
        kb.setDescription("覆盖数据挖掘教材与课程 PPT 的核心知识");
        kb.setCourse("数据挖掘");
        kb.setStatus(1);
        knowledgeBaseMapper.insert(kb);
        log.info("已创建默认知识库：{}", kb.getName());
    }
}
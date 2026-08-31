package edu.zjut.traceqa.qaservice.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.zjut.traceqa.common.model.po.SystemPrompt;
import edu.zjut.traceqa.qaservice.mapper.SystemPromptMapper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 问答服务初始化数据装载。
 *
 * <p>启动时幂等地预置全部 Agent 场景的系统提示词（内容取自 {@link PromptDefaults}）。</p>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SystemPromptMapper systemPromptMapper;

    public DataInitializer(SystemPromptMapper systemPromptMapper) {
        this.systemPromptMapper = systemPromptMapper;
    }

    /**
     * 启动装载系统提示词
     */
    @Override
    public void run(org.springframework.boot.@NonNull ApplicationArguments args) {
        try {
            initSystemPrompts();
        } catch (Exception e) {
            log.warn("初始化「系统提示词」失败：{}", e.getMessage());
        }
    }

    /**
     * 按场景缺省创建系统提示词
     */
    private void initSystemPrompts() {
        for (Map.Entry<String, String> entry : PromptDefaults.CONTENT.entrySet()) {
            String scenario = entry.getKey();
            Long count = systemPromptMapper.selectCount(
                    new LambdaQueryWrapper<SystemPrompt>().eq(SystemPrompt::getScenario, scenario));
            if (count != null && count > 0) {
                continue;
            }
            SystemPrompt prompt = new SystemPrompt();
            prompt.setScenario(scenario);
            prompt.setName(PromptDefaults.NAMES.getOrDefault(scenario, scenario));
            prompt.setContent(entry.getValue());
            prompt.setEnabled(1);
            prompt.setRemark("系统预置提示词，管理员可编辑内容");
            systemPromptMapper.insert(prompt);
        }
        log.info("系统提示词初始化完成");
    }
}
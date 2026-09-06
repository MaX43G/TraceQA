package edu.zjut.traceqa.qaservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.common.model.po.SystemPrompt;
import edu.zjut.traceqa.qaservice.config.PromptDefaults;
import edu.zjut.traceqa.qaservice.mapper.SystemPromptMapper;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统提示词服务。
 *
 * <p>支持管理员动态管理各 Agent 场景的系统提示词；同场景仅启用一个，
 * 读取时优先返回启用项，未启用则返回默认提示词，保证优雅降级。</p>
 */
@Service
public class SystemPromptService {

    @Resource
    private SystemPromptMapper systemPromptMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    /**
     * 查询提示词列表
     */
    public List<SystemPrompt> list() {
        return systemPromptMapper.selectList(
                new LambdaQueryWrapper<SystemPrompt>().orderByAsc(SystemPrompt::getScenario));
    }

    /**
     * 按场景获取启用中的提示词，数据库缺失时回退到默认模板
     */
    public SystemPrompt getActive(String scenario) {
        SystemPrompt prompt = systemPromptMapper.selectOne(
                new LambdaQueryWrapper<SystemPrompt>()
                        .eq(SystemPrompt::getScenario, scenario)
                        .eq(SystemPrompt::getEnabled, 1)
                        .last("LIMIT 1"));
        if (prompt != null) {
            return prompt;
        }
        return buildDefault(scenario);
    }

    /**
     * 从默认模板构造兜底提示词（不落库）
     */
    private SystemPrompt buildDefault(String scenario) {
        String content = PromptDefaults.CONTENT.get(scenario);
        if (content == null) {
            return null;
        }
        SystemPrompt fallback = new SystemPrompt();
        fallback.setScenario(scenario);
        fallback.setName(PromptDefaults.NAMES.getOrDefault(scenario, scenario));
        fallback.setContent(content);
        fallback.setEnabled(1);
        return fallback;
    }

    /**
     * 更新提示词
     */
    public SystemPrompt update(SystemPrompt prompt) {
        SystemPrompt exist = systemPromptMapper.selectById(prompt.getId());
        if (exist == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提示词不存在");
        }
        systemPromptMapper.updateById(prompt);
        // 通知缓存了该提示词的组件（如 Answer/Intent Agent）失效缓存，使新提示词立即生效
        eventPublisher.publishEvent(new SystemPromptChangedEvent(prompt.getScenario()));
        return prompt;
    }

    /**
     * 启用指定提示词，并停用同场景其他提示词
     */
    public void enable(Long id) {
        SystemPrompt prompt = requireById(id);
        systemPromptMapper.update(null,
                new LambdaUpdateWrapper<SystemPrompt>()
                        .eq(SystemPrompt::getScenario, prompt.getScenario())
                        .set(SystemPrompt::getEnabled, 0));
        prompt.setEnabled(1);
        systemPromptMapper.updateById(prompt);
        // 停用/启用会改变 getActive 结果，通知缓存失效
        eventPublisher.publishEvent(new SystemPromptChangedEvent(prompt.getScenario()));
    }

    /**
     * 查询指定提示词，不存在抛业务异常
     */
    private SystemPrompt requireById(Long id) {
        SystemPrompt prompt = systemPromptMapper.selectById(id);
        if (prompt == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提示词不存在");
        }
        return prompt;
    }
}
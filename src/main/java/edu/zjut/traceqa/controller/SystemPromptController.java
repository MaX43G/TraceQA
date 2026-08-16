package edu.zjut.traceqa.controller;

import jakarta.annotation.Resource;
import cn.dev33.satoken.annotation.SaCheckPermission;
import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.model.vo.SystemPromptDTO;
import edu.zjut.traceqa.model.po.SystemPrompt;
import edu.zjut.traceqa.service.SystemPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import edu.zjut.traceqa.common.convert.DtoMapper;

/**
 * 系统提示词接口（管理员动态管理各 Agent 提示词）。
 */
@Tag(name = "系统提示词", description = "各 Agent 场景系统提示词的动态管理")
@RestController
@RequestMapping("/api/prompts")
public class SystemPromptController {

    @Resource
    private SystemPromptService systemPromptService;

    

    @Operation(summary = "查询全部系统提示词")
    @GetMapping
    public ApiResponse<List<SystemPromptDTO>> list() {
        return ApiResponse.ok(systemPromptService.list().stream().map(SystemPromptDTO::of).toList());
    }

    @Operation(summary = "创建系统提示词（平台预置场景，不支持新增）")
    @PostMapping
    @SaCheckPermission("prompt:manage")
    public ApiResponse<SystemPromptDTO> create(@Valid @RequestBody SystemPromptDTO dto) {
        throw new BizException(ErrorCode.PARAM_ERROR,
                "系统提示词由平台预置，管理员仅可编辑已有提示词，不支持新增场景");
    }

    @Operation(summary = "更新系统提示词")
    @PutMapping("/{id}")
    @SaCheckPermission("prompt:manage")
    public ApiResponse<SystemPromptDTO> update(@PathVariable Long id, @Valid @RequestBody SystemPromptDTO dto) {
        SystemPrompt prompt = toEntity(dto);
        prompt.setId(id);
        return ApiResponse.ok(DtoMapper.INSTANCE.toSystemPromptDTO(systemPromptService.update(prompt)));
    }

    @Operation(summary = "启用指定提示词（自动停用同场景其他项）")
    @PutMapping("/{id}/enable")
    @SaCheckPermission("prompt:manage")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        systemPromptService.enable(id);
        return ApiResponse.ok();
    }


    /** DTO 转实体 */
    private SystemPrompt toEntity(SystemPromptDTO dto) {
        SystemPrompt prompt = new SystemPrompt();
        prompt.setScenario(dto.getScenario());
        prompt.setName(dto.getName());
        prompt.setContent(dto.getContent());
        prompt.setEnabled(dto.getEnabled() == null ? 0 : dto.getEnabled());
        prompt.setRemark(dto.getRemark());
        return prompt;
    }
}
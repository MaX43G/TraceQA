package edu.zjut.traceqa.kbservice.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.model.vo.KnowledgeBaseDTO;
import edu.zjut.traceqa.common.rbac.RequirePermission;
import edu.zjut.traceqa.kbservice.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库接口。
 */
@Tag(name = "知识库", description = "知识库的增删改查")
@RestController
@RequestMapping("/api/kbs")
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 查询知识库列表
     */
    @Operation(summary = "查询知识库列表")
    @GetMapping
    public ApiResponse<List<KnowledgeBaseDTO>> list() {
        return ApiResponse.ok(knowledgeBaseService.list());
    }

    /**
     * 创建知识库
     */
    @Operation(summary = "创建知识库")
    @RequirePermission("kb:manage")
    @PostMapping
    public ApiResponse<KnowledgeBaseDTO> create(@Valid @RequestBody KnowledgeBaseDTO dto) {
        return ApiResponse.ok(knowledgeBaseService.create(dto));
    }

    /**
     * 更新知识库
     */
    @Operation(summary = "更新知识库")
    @RequirePermission("kb:manage")
    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseDTO> update(@PathVariable Long id, @Valid @RequestBody KnowledgeBaseDTO dto) {
        return ApiResponse.ok(knowledgeBaseService.update(id, dto));
    }

    /**
     * 删除知识库
     */
    @Operation(summary = "删除知识库")
    @RequirePermission("kb:manage")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return ApiResponse.ok();
    }
}
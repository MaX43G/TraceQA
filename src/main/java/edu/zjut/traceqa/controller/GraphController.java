package edu.zjut.traceqa.controller;

import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.service.GraphVizService;
import jakarta.annotation.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识图谱路径可视化接口（登录用户）。
 */
@Tag(name = "图谱可视化", description = "将回答相关的知识图谱路径渲染为图")
@RestController
@RequestMapping("/api/graph")
public class GraphController {

    @Resource
    private GraphVizService graphVizService;

    @Operation(summary = "根据实体词/问题，返回相关知识图谱子图（nodes + edges）")
    @PostMapping("/viz")
    public ApiResponse<Map<String, Object>> viz(@RequestBody Map<String, Object> body) {
        List<String> terms = parseTerms(body);
        return ApiResponse.ok(graphVizService.visualize(terms));
    }

    private List<String> parseTerms(Map<String, Object> body) {
        if (body == null) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        Object termsRaw = body.get("terms");
        if (termsRaw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    terms.add(String.valueOf(o));
                }
            }
        }
        Object query = body.get("query");
        if (terms.isEmpty() && query != null) {
            // 从问题/文本中按非中文词符切分，粗提取候选实体
            for (String part : String.valueOf(query).split("[\\s，。；、？！：:（）()\\[\\]{}<>《》\"'“”’—…~`]+")) {
                String t = part.trim();
                if (t.length() >= 2 && t.length() <= 16) {
                    terms.add(t);
                }
            }
        }
        return terms.stream().limit(5).toList();
    }
}
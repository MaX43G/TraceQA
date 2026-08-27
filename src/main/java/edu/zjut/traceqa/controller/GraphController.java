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
            terms = extractTerms(String.valueOf(query));
        }
        return terms.stream().distinct().limit(6).toList();
    }

    private List<String> extractTerms(String query) {
        List<String> terms = new ArrayList<>();
        String[] parts = query.split(
                "[\\s，。；、？！：:（）()\\[\\]{}<>《》\"'“”‘’—…~`+=|/\\\\和与及或区别联系对比比较关系怎么如何什么为什么是呢吗请解释一下的]");
        for (String part : parts) {
            String t = part.trim();
            if (t.length() >= 2 && t.length() <= 16 && !terms.contains(t)) {
                terms.add(t);
            }
        }
        String flat = query.replaceAll("\\s+", "");
        if (flat.length() > 16) {
            String head = flat.substring(0, 16);
            if (!terms.contains(head)) {
                terms.add(head);
            }
        }
        return terms;
    }
}
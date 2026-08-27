package edu.zjut.traceqa.service;

import edu.zjut.traceqa.config.LightRagClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识图谱路径可视化服务。
 *
 * <p>给定一组实体词，逐个查询 LightRAG 以该实体为起点的连通子图，合并节点与边，
 * 归一化为前端可渲染的 {@code {nodes:[{id,label,type}], edges:[{source,target,label}]}}。
 * 仅做轻度归一化，字段缺失时以宽松方式取可用字段。</p>
 */
@Slf4j
@Service
public class GraphVizService {

    @Resource
    private LightRagClient lightRagClient;

    /** 每个实体词的图谱最大深度与节点数 */
    private static final int MAX_DEPTH = 2;
    private static final int MAX_NODES = 40;

    /** 合并多个实体词的连通子图 */
    public Map<String, Object> visualize(List<String> terms) {
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> edgeKeys = new HashSet<>();

        for (String term : terms) {
            if (term == null || term.isBlank() || term.length() < 2) {
                continue;
            }
            Map<String, Object> sub;
            try {
                sub = lightRagClient.getGraph(term, MAX_DEPTH, MAX_NODES);
            } catch (Exception e) {
                log.debug("图谱可视化跳过实体：term={}, err={}", term, e.getMessage());
                continue;
            }
            collectNodes(sub, nodes);
            collectEdges(sub, edges, edgeKeys);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("nodes", new ArrayList<>(nodes.values()));
        out.put("edges", edges);
        return out;
    }

    private void collectNodes(Map<String, Object> sub, Map<String, Map<String, Object>> nodes) {
        Object raw = sub.get("nodes");
        if (!(raw instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            String id = firstNonBlank(m.get("id"), m.get("entity_name"), m.get("name"));
            if (id.isBlank()) {
                continue;
            }
            Map<String, Object> node = nodes.computeIfAbsent(id, k -> {
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("id", id);
                n.put("label", firstNonBlank(m.get("display_name"), m.get("entity_name"), m.get("name")));
                n.put("type", str(m.get("entity_type")));
                return n;
            });
            // 补充缺失的 type/label
            if (node.get("label") == null || node.get("label").toString().isBlank()) {
                node.put("label", id);
            }
            if (node.get("type") == null || node.get("type").toString().isBlank()) {
                node.put("type", str(m.get("entity_type")));
            }
        }
    }

    private void collectEdges(Map<String, Object> sub, List<Map<String, Object>> edges, Set<String> edgeKeys) {
        Object raw = sub.get("edges");
        if (!(raw instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            String source = firstNonBlank(m.get("src_name"), m.get("src_id"));
            String target = firstNonBlank(m.get("tgt_name"), m.get("tgt_id"));
            if (source.isBlank() || target.isBlank()) {
                continue;
            }
            String key = source + "\u0001" + target;
            if (!edgeKeys.add(key)) {
                continue;
            }
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", source);
            edge.put("target", target);
            edge.put("label", firstNonBlank(m.get("description"), m.get("keywords") == null ? "" : String.valueOf(m.get("keywords"))));
            edges.add(edge);
        }
    }

    private String firstNonBlank(Object... values) {
        for (Object v : values) {
            String s = str(v);
            if (!s.isBlank()) {
                return s;
            }
        }
        return "";
    }

    private String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof List<?> list && !list.isEmpty()) {
            return String.join("、", list.stream().map(String::valueOf).toList());
        }
        return String.valueOf(o);
    }
}
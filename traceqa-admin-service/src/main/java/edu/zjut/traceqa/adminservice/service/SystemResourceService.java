package edu.zjut.traceqa.adminservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.OperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 系统资源检测与清理服务。
 *
 * <p>采集服务器 CPU / 内存 / 磁盘占用与设备基础信息（宿主机视角，经
 * Docker Engine API 与 {@code com.sun.management.OperatingSystemMXBean} 读取）；
 * 并提供对 Docker 无用资源的清理（镜像 / 容器 / 卷 / 构建缓存）。</p>
 *
 * <p>依赖：admin-service 需以可访问 {@code /var/run/docker.sock} 的方式运行，
 * 且镜像内需含 curl，方可执行 Docker Engine API 调用；未挂载 socket 时相关能力自动降级。</p>
 */
@Service
public class SystemResourceService {

    private static final Logger log = LoggerFactory.getLogger(SystemResourceService.class);

    /** Docker Engine API 的 unix socket 路径 */
    private static final String DOCKER_SOCKET = "/var/run/docker.sock";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 系统资源快照：CPU / 内存 / 磁盘 / 负载 / 基础信息 / Docker 可回收空间
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cpu", cpuInfo());
        data.put("memory", memoryInfo());
        data.put("disk", diskInfo());
        data.put("loadAverage", loadAverage());
        data.put("uptimeSeconds", hostUptime());
        data.put("host", hostInfo());
        data.put("docker", dockerSummary());
        return data;
    }

    /**
     * 执行系统清理。当前支持 Docker 无用资源清理。
     */
    public Map<String, Object> cleanup(String mode) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode == null ? "docker" : mode);
        if (!dockerAvailable()) {
            result.put("available", false);
            result.put("message",
                    "Docker socket 未挂载，无法执行 Docker 清理。请在 docker-compose 中将宿主机 "
                            + DOCKER_SOCKET + " 挂载到 admin-service 容器。");
            return result;
        }
        result.put("available", true);
        long freedBytes = 0;
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(pruneStep("/containers/prune", "已停止容器"));
        steps.add(pruneStep("/images/prune", "悬空/未使用镜像"));
        steps.add(pruneStep("/build/prune", "构建缓存"));
        steps.add(pruneStep("/volumes/prune", "未使用数据卷"));
        for (Map<String, Object> s : steps) {
            freedBytes += ((Number) s.getOrDefault("freedBytes", 0L)).longValue();
        }
        result.put("freedBytes", freedBytes);
        result.put("freedHuman", humanBytes(freedBytes));
        result.put("steps", steps);
        result.put("after", dockerSummary());
        return result;
    }

    // ==================== 采集 ====================

    private Map<String, Object> cpuInfo() {
        OperatingSystemMXBean os = osBean();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("percent", round(os.getCpuLoad() * 100));        // 整体 CPU 使用率
        m.put("processPercent", round(os.getProcessCpuLoad() * 100));
        return m;
    }

    private Map<String, Object> memoryInfo() {
        OperatingSystemMXBean os = osBean();
        long total = os.getTotalMemorySize();
        long free = os.getFreeMemorySize();
        long used = Math.max(total - free, 0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalBytes", total);
        m.put("usedBytes", used);
        m.put("freeBytes", free);
        m.put("usedPercent", total <= 0 ? 0 : round(used * 100.0 / total));
        m.put("totalHuman", humanBytes(total));
        m.put("usedHuman", humanBytes(used));
        m.put("freeHuman", humanBytes(free));
        return m;
    }

    private List<Map<String, Object>> diskInfo() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            for (java.nio.file.Path root : FileSystems.getDefault().getRootDirectories()) {
                FileStore fs = java.nio.file.Files.getFileStore(root);
                long total = fs.getTotalSpace();
                long usable = fs.getUsableSpace();
                long used = Math.max(total - usable, 0);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("mount", root.toString());
                m.put("type", fs.type());
                m.put("totalBytes", total);
                m.put("usedBytes", used);
                m.put("freeBytes", usable);
                m.put("usedPercent", total <= 0 ? 0 : round(used * 100.0 / total));
                m.put("totalHuman", humanBytes(total));
                m.put("usedHuman", humanBytes(used));
                m.put("freeHuman", humanBytes(usable));
                list.add(m);
            }
        } catch (IOException e) {
            log.warn("采集磁盘信息失败：{}", e.getMessage());
        }
        return list;
    }

    private double loadAverage() {
        return ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
    }

    private long hostUptime() {
        try {
            String line = java.nio.file.Files.readString(java.nio.file.Path.of("/proc/uptime"));
            String sec = line.trim().split("\\s+")[0];
            return Math.round(Double.parseDouble(sec));
        } catch (Exception e) {
            return 0;
        }
    }

    private Map<String, Object> hostInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        // 优先从 Docker Engine 读取宿主机信息，失败则回退 JVM 属性
        Map<String, Object> docker = dockerInfo();
        if (!docker.isEmpty()) {
            m.putAll(docker);
        } else {
            m.put("hostname", hostnameFromEnv());
            m.put("osName", System.getProperty("os.name"));
            m.put("osArch", System.getProperty("os.arch"));
        }
        m.put("cpuCores", osBean().getAvailableProcessors());
        m.put("javaVersion", System.getProperty("java.version"));
        m.put("containerHostname", hostnameFromEnv());
        return m;
    }

    private Map<String, Object> dockerSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!dockerAvailable()) {
            m.put("available", false);
            return m;
        }
        m.put("available", true);
        String df = dockerApi("/system/df", "GET");
        try {
            JsonNode root = objectMapper.readTree(df);
            long total = 0;
            long reclaimable = 0;
            for (String key : List.of("Images", "Containers", "Volumes", "BuildCache")) {
                JsonNode arr = root.path(key);
                if (arr.isArray()) {
                    for (JsonNode it : arr) {
                        reclaimable += it.path("Reclaimable").asLong(0);
                        total += it.path("Size").asLong(0);
                    }
                }
            }
            m.put("totalSizeBytes", total);
            m.put("reclaimableBytes", reclaimable);
            m.put("totalSizeHuman", humanBytes(total));
            m.put("reclaimableHuman", humanBytes(reclaimable));
        } catch (Exception e) {
            m.put("error", "解析 Docker df 失败：" + e.getMessage());
        }
        return m;
    }

    private Map<String, Object> dockerInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!dockerAvailable()) {
            return m;
        }
        String info = dockerApi("/info", "GET");
        try {
            JsonNode root = objectMapper.readTree(info);
            m.put("hostname", root.path("Name").asText(""));
            m.put("osName", root.path("OperatingSystem").asText(""));
            m.put("osArch", root.path("Architecture").asText(""));
            m.put("kernelVersion", root.path("KernelVersion").asText(""));
            m.put("dockerVersion", root.path("ServerVersion").asText(""));
        } catch (Exception e) {
            // 忽略，回退 JVM 属性
        }
        return m;
    }

    // ==================== 清理 ====================

    private Map<String, Object> pruneStep(String path, String label) {
        Map<String, Object> step = new LinkedHashMap<>();
        long before = dockerDfReclaimable();
        String resp = dockerApi(path, "POST");
        long reclaimed = 0;
        try {
            reclaimed = objectMapper.readTree(resp).path("SpaceReclaimed").asLong(0);
        } catch (Exception ignore) {
            // 部分接口可能无 SpaceReclaimed
        }
        step.put("label", label);
        step.put("endpoint", path);
        step.put("freedBytes", reclaimed);
        step.put("freedHuman", humanBytes(reclaimed));
        step.put("reclaimableAfter", before);
        return step;
    }

    private long dockerDfReclaimable() {
        Map<String, Object> s = dockerSummary();
        return s.containsKey("reclaimableBytes") ? ((Number) s.get("reclaimableBytes")).longValue() : 0;
    }

    // ==================== 基础设施 ====================

    private OperatingSystemMXBean osBean() {
        return (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    private boolean dockerAvailable() {
        return java.nio.file.Files.exists(java.nio.file.Path.of(DOCKER_SOCKET));
    }

    /** 调用 Docker Engine API（经 unix socket + curl） */
    private String dockerApi(String path, String method) {
        List<String> cmd = new ArrayList<>(List.of(
                "curl", "-s", "-m", "15", "-X", method,
                "--unix-socket", DOCKER_SOCKET, "http://localhost" + path));
        return exec(cmd);
    }

    /** 执行命令并返回 stdout+stderr */
    private String exec(List<String> cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "{\"error\":\"timeout\"}";
            }
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String hostnameFromEnv() {
        String h = System.getenv("HOSTNAME");
        return (h == null || h.isEmpty()) ? "unknown" : h;
    }

    private double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        int i = -1;
        double v = bytes;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format("%.1f %s", v, units[i]);
    }
}

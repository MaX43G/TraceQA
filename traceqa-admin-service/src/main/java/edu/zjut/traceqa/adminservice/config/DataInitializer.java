package edu.zjut.traceqa.adminservice.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.zjut.traceqa.common.model.po.Announcement;
import edu.zjut.traceqa.adminservice.mapper.AnnouncementMapper;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 管理服务初始化数据装载。
 *
 * <p>首次启动时预置欢迎公告。</p>
 */
@Component
@AllArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AnnouncementMapper announcementMapper;

    /**
     * 启动装载默认公告
     */
    @Override
    public void run(org.springframework.boot.@NonNull ApplicationArguments args) {
        try {
            initAnnouncement();
        } catch (Exception e) {
            log.warn("初始化「默认公告」失败：{}", e.getMessage());
        }
    }

    /**
     * 无未删除公告时创建欢迎公告
     */
    private void initAnnouncement() {
        Long count = announcementMapper.selectCount(
                new LambdaQueryWrapper<Announcement>().eq(Announcement::getDeleted, 0));
        if (count != null && count > 0) {
            return;
        }
        Announcement a = new Announcement();
        a.setTitle("欢迎使用溯知 · TraceQA");
        a.setContent("欢迎使用《数据挖掘》智能问答平台。输入问题即可获得基于知识图谱与向量检索的智能回答，支持语音输入与「猜你想问」智能追问。");
        a.setEnabled(1);
        announcementMapper.insert(a);
        log.info("已创建默认公告");
    }
}
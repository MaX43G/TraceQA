package edu.zjut.traceqa.adminservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.zjut.traceqa.common.context.UserContext;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.common.model.po.Announcement;
import edu.zjut.traceqa.adminservice.mapper.AnnouncementMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统公告服务：公开获取当前启用的公告、管理员维护公告。
 */
@Service
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;

    public AnnouncementService(AnnouncementMapper announcementMapper) {
        this.announcementMapper = announcementMapper;
    }

    /**
     * 公开获取所有启用的公告（按更新时间倒序，最多 20 条）
     */
    public List<Map<String, Object>> active() {
        return announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getEnabled, 1).eq(Announcement::getDeleted, 0)
                        .orderByDesc(Announcement::getUpdateTime).last("LIMIT 20"))
                .stream().map(this::view).toList();
    }

    /**
     * 管理员获取全部公告
     */
    public List<Map<String, Object>> listAll() {
        requireAdmin();
        return announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getDeleted, 0).orderByDesc(Announcement::getId))
                .stream().map(this::view).toList();
    }

    /**
     * 管理员新增/修改公告
     */
    public Map<String, Object> save(Long id, String title, String content, Integer enabled) {
        requireAdmin();
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "公告标题不能为空");
        }
        Announcement a;
        if (id != null) {
            a = announcementMapper.selectById(id);
            if (a == null || a.getDeleted() == 1) {
                throw new BizException(ErrorCode.NOT_FOUND, "公告不存在");
            }
            a.setTitle(title);
            a.setContent(content == null ? "" : content);
            a.setEnabled(enabled == null ? 1 : enabled);
            announcementMapper.updateById(a);
        } else {
            a = Announcement.builder()
                    .title(title)
                    .content(content == null ? "" : content)
                    .enabled(enabled == null ? 1 : enabled)
                    .build();
            announcementMapper.insert(a);
        }
        return view(a);
    }

    /**
     * 管理员删除公告
     */
    public void delete(Long id) {
        requireAdmin();
        Announcement a = announcementMapper.selectById(id);
        if (a == null || a.getDeleted() == 1) {
            return;
        }
        a.setDeleted(1);
        announcementMapper.updateById(a);
    }

    private void requireAdmin() {
        var user = UserContext.get();
        if (user == null || !user.hasPermission("user:manage")) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可操作");
        }
    }

    private Map<String, Object> view(Announcement a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("title", a.getTitle());
        m.put("content", a.getContent());
        m.put("enabled", a.getEnabled());
        m.put("createTime", a.getCreateTime());
        m.put("updateTime", a.getUpdateTime());
        return m;
    }
}
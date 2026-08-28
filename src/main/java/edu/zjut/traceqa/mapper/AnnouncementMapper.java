package edu.zjut.traceqa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zjut.traceqa.model.po.Announcement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统公告 Mapper。
 */
@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {
}
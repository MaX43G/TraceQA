package edu.zjut.traceqa.adminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zjut.traceqa.common.model.po.Announcement;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统公告数据访问接口。
 */
@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {
}
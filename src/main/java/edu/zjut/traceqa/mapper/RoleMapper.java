package edu.zjut.traceqa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zjut.traceqa.entity.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色数据访问接口（RBAC）。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
package edu.zjut.traceqa.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zjut.traceqa.common.model.po.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问接口。
 *
 * <p>继承 MyBatis-Plus BaseMapper 提供通用 CRUD，复杂查询一律通过
 * {@code LambdaQueryWrapper} 在服务层完成，严禁手写原生 SQL。</p>
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
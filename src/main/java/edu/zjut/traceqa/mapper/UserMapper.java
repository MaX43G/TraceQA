package edu.zjut.traceqa.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.zjut.traceqa.model.po.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问接口。
 *
 * <p>继承 {@link BaseMapper} 获得通用 CRUD，禁止手写原生 SQL。
 * 复杂查询通过 MyBatis-Plus 的条件构造器 {@code LambdaQueryWrapper} 完成。</p>
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
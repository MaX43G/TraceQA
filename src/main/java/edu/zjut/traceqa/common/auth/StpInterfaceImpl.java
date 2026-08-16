package edu.zjut.traceqa.common.auth;

import cn.dev33.satoken.stp.StpInterface;
import edu.zjut.traceqa.model.po.User;
import edu.zjut.traceqa.mapper.UserMapper;
import edu.zjut.traceqa.service.AuthService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * sa-token 权限数据源：按登录用户 ID 返回角色与权限码集合，
 * 供 {@code @SaCheckPermission / @SaCheckRole} 注解鉴权使用。
 *
 * <p>权限码完全取自数据库角色表（管理员角色在 DB 中已展开为全部权限码），
 * 无任何角色名特殊判断。</p>
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Resource
    private UserMapper userMapper;

    @Resource
    private AuthService authService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        User user = findUser(loginId);
        return user == null ? List.of() : authService.resolvePermissions(user.getRoleCode());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = findUser(loginId);
        return user == null ? List.of() : List.of(user.getRoleCode());
    }

    private User findUser(Object loginId) {
        try {
            return userMapper.selectById(Long.valueOf(String.valueOf(loginId)));
        } catch (Exception e) {
            return null;
        }
    }
}

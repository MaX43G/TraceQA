package edu.zjut.traceqa.userservice.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.zjut.traceqa.common.api.PageResult;
import edu.zjut.traceqa.common.convert.DtoMapper;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.common.model.po.Role;
import edu.zjut.traceqa.common.model.po.User;
import edu.zjut.traceqa.common.model.vo.AdminUserVO;
import edu.zjut.traceqa.common.model.vo.RoleDTO;
import edu.zjut.traceqa.userservice.mapper.RoleMapper;
import edu.zjut.traceqa.userservice.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理员服务（RBAC 用户与角色管理）。
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    public AdminService(UserMapper userMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    /**
     * 分页查询用户列表
     */
    public PageResult<AdminUserVO> pageUsers(String keyword, long page, long size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(User::getUsername, keyword)
                        .or()
                        .like(User::getNickname, keyword))
                .orderByDesc(User::getId);
        IPage<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result, AdminUserVO::of);
    }

    /**
     * 启用/禁用用户（禁用时立即强制注销其全部登录会话）。
     */
    public void updateUserStatus(Long id, int status) {
        User user = requireUser(id);
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "非法状态值");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        if (status == 0) {
            kickUserSessions(id);
        }
        log.info("用户状态更新：id={}, status={}", id, status);
    }

    /**
     * 强制注销用户的所有 sa-token 会话
     */
    private void kickUserSessions(Long userId) {
        try {
            StpUtil.logout(userId);
            log.info("已强制注销被禁用用户的全部会话：userId={}", userId);
        } catch (Exception e) {
            log.warn("强制注销用户会话失败：userId={}, err={}", userId, e.getMessage());
        }
    }

    /**
     * 变更用户角色
     */
    public void updateUserRole(Long id, String roleCode) {
        User user = requireUser(id);
        Role role = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode).last("LIMIT 1"));
        if (role == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "角色不存在");
        }
        user.setRoleCode(roleCode);
        userMapper.updateById(user);
        log.info("用户角色变更：id={}, role={}", id, roleCode);
    }

    /**
     * 查询全部角色
     */
    public List<RoleDTO> listRoles() {
        return roleMapper.selectList(null).stream().map(RoleDTO::of).toList();
    }

    /**
     * 更新角色权限
     */
    public RoleDTO updateRole(Long id, RoleDTO dto) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        role.setName(dto.getName());
        role.setPermissions(dto.getPermissions());
        role.setDescription(dto.getDescription());
        roleMapper.updateById(role);
        return DtoMapper.INSTANCE.toRoleDTO(role);
    }

    /**
     * 校验用户存在
     */
    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
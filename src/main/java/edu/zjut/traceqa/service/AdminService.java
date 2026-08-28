package edu.zjut.traceqa.service;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.stp.StpUtil;
import edu.zjut.traceqa.common.api.PageResult;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.model.vo.AdminUserVO;
import edu.zjut.traceqa.model.vo.RoleDTO;
import edu.zjut.traceqa.model.po.Role;
import edu.zjut.traceqa.model.po.User;
import edu.zjut.traceqa.mapper.RoleMapper;
import edu.zjut.traceqa.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import edu.zjut.traceqa.common.convert.DtoMapper;

/**
 * 管理员服务（RBAC 用户与角色管理）。
 */
@Slf4j
@Service
public class AdminService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private RoleMapper roleMapper;


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
     * 启用/禁用用户（禁用时立即强制注销其全部登录会话，杜绝「禁用后仍可继续使用」）
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
     * 强制注销用户的所有 sa-token 会话。
     *
     * <p>Web 拦截器已按请求实时校验用户状态，此处再主动清理会话缓存，
     * 保证禁用瞬间旧 Token 立即失效（无需等下一次请求才被发现）。</p>
     */
    private void kickUserSessions(Long userId) {
        try {
            // 注销该账号的所有会话（含其全部已签发 Token），并同步清理会话内缓存数据
            StpUtil.logout(userId);
            log.info("已强制注销被禁用用户的全部会话：userId={}", userId);
        } catch (Exception e) {
            // 会话清理失败不影响状态落库，拦截器仍会拦截后续请求
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
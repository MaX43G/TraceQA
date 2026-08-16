package edu.zjut.traceqa.service;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.zjut.traceqa.common.auth.JwtService;
import edu.zjut.traceqa.common.auth.UserContext;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.dto.auth.LoginRequest;
import edu.zjut.traceqa.dto.auth.LoginResponse;
import edu.zjut.traceqa.dto.auth.RegisterRequest;
import edu.zjut.traceqa.dto.auth.UserInfo;
import edu.zjut.traceqa.entity.Role;
import edu.zjut.traceqa.entity.User;
import edu.zjut.traceqa.mapper.RoleMapper;
import edu.zjut.traceqa.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 认证服务。
 *
 * <p>负责注册、登录与当前用户信息查询。密码统一 BCrypt 加密存储，
 * 登录成功后签发 JWT 令牌。用户名与密码校验失败均返回「用户名或密码错误」，
 * 避免账号枚举攻击。</p>
 */
@Slf4j
@Service
public class AuthService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private JwtService jwtService;

    

    /** 用户注册（默认角色 USER） */
    public UserInfo register(RegisterRequest request) {
        User exist = findByUsername(request.username());
        if (exist != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名已被占用");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname() == null ? request.username() : request.nickname());
        user.setRoleCode("USER");
        user.setStatus(1);
        userMapper.insert(user);
        log.info("用户注册成功：{}", request.username());
        return UserInfo.of(user, resolvePermissions(user.getRoleCode()));
    }

    /** 用户登录，签发令牌 */
    public LoginResponse login(LoginRequest request) {
        User user = findByUsername(request.username());
        // 统一错误提示，防账号枚举
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        List<String> permissions = resolvePermissions(user.getRoleCode());
        UserContext.LoginUser loginUser = new UserContext.LoginUser(
                user.getId(), user.getUsername(), user.getNickname(), user.getRoleCode(), permissions);
        String token = jwtService.generateToken(loginUser);
        log.info("用户登录成功：{}", request.username());
        return LoginResponse.of(user, permissions, token);
    }

    /** 查询当前登录用户信息 */
    public UserInfo currentUser() {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return UserInfo.of(user, resolvePermissions(user.getRoleCode()));
    }

    /** 修改当前用户密码（校验原密码，BCrypt 加密新密码） */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        log.info("用户修改密码成功：{}", user.getUsername());
    }

    /** 解析角色权限码集合 */
    public List<String> resolvePermissions(String roleCode) {
        Role role = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode).last("LIMIT 1"));
        if (role == null || role.getPermissions() == null || role.getPermissions().isBlank()) {
            return List.of();
        }
        return Arrays.stream(role.getPermissions().split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .toList();
    }

    /** 按用户名查询用户 */
    private User findByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username).last("LIMIT 1"));
    }
}
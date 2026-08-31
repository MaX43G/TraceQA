package edu.zjut.traceqa.userservice.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.zjut.traceqa.common.auth.LoginUser;
import edu.zjut.traceqa.common.client.FileClient;
import edu.zjut.traceqa.common.context.UserContext;
import edu.zjut.traceqa.common.convert.DtoMapper;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.common.model.dto.LoginRequest;
import edu.zjut.traceqa.common.model.dto.RegisterRequest;
import edu.zjut.traceqa.common.model.po.Role;
import edu.zjut.traceqa.common.model.po.User;
import edu.zjut.traceqa.common.model.vo.LoginResponse;
import edu.zjut.traceqa.common.model.vo.UserInfo;
import edu.zjut.traceqa.userservice.mapper.RoleMapper;
import edu.zjut.traceqa.userservice.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * 认证服务。
 *
 * <p>负责注册、登录、登出、当前用户信息、昵称/密码/头像维护。
 * 密码统一 BCrypt 加密存储；登录态由 sa-token 管理（Redis 持久化 + 单端登录），
 * 登录成功后写入 {@link LoginUser} 供网关读取透传。</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * 系统全部权限码（管理员角色持有，DB 层展开存储）
     */
    public static final List<String> ALL_PERMISSIONS = List.of(
            "user:manage", "role:manage", "kb:manage", "prompt:manage",
            "kb:view", "doc:view", "chat:manage"
    );

    /**
     * 管理员权限码的逗号拼接串（写入角色表）
     */
    public static final String ALL_PERMISSIONS_STR = String.join(",", ALL_PERMISSIONS);

    /**
     * 登录防爆破：允许的最大失败次数与锁定时长
     */
    private static final int LOGIN_MAX_FAIL = 5;
    private static final Duration LOGIN_LOCK_TTL = Duration.ofMinutes(10);
    private static final Duration LOGIN_FAIL_TTL = Duration.ofMinutes(10);

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final FileClient fileClient;
    private final StringRedisTemplate stringRedisTemplate;

    public AuthService(UserMapper userMapper, RoleMapper roleMapper, PasswordEncoder passwordEncoder,
                       FileClient fileClient, StringRedisTemplate stringRedisTemplate) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
        this.fileClient = fileClient;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 用户注册（默认角色 USER；账号需英文数字且唯一，注册后不可修改）。
     */
    public UserInfo register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "两次输入的密码不一致");
        }
        User exist = findByUsername(request.getUsername());
        if (exist != null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "账号已被占用");
        }
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .roleCode("USER")
                .status(1)
                .build();
        userMapper.insert(user);
        log.info("用户注册成功：{}", request.getUsername());
        return DtoMapper.INSTANCE.toUserInfo(user, resolvePermissions(user.getRoleCode()));
    }

    /**
     * 用户登录（sa-token 单端登录：同一账号新登录会顶掉旧会话）；含账号+IP 级防爆破。
     */
    public LoginResponse login(HttpServletRequest request, LoginRequest req) {
        String ip = resolveIp(request);
        String failKey = "login:fail:" + req.getUsername() + ":" + ip;
        String lockKey = "login:lock:" + req.getUsername() + ":" + ip;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
            throw new BizException(ErrorCode.RATE_LIMITED, "尝试次数过多，请 10 分钟后再试");
        }
        User user = findByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            recordFail(failKey, lockKey);
            log.warn("登录失败：username={}, ip={}", req.getUsername(), ip);
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        try {
            stringRedisTemplate.delete(failKey);
            stringRedisTemplate.delete(lockKey);
        } catch (Exception ignored) {
        }
        List<String> permissions = resolvePermissions(user.getRoleCode());
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getNickname(),
                user.getRoleCode(), permissions, user.getAvatar());
        StpUtil.login(user.getId());
        StpUtil.getSession().set(LoginUser.SESSION_KEY, loginUser);
        String token = StpUtil.getTokenValue();
        log.info("用户登录成功：{}", req.getUsername());
        return LoginResponse.of(user, permissions, token);
    }

    /**
     * 记录一次登录失败；达到阈值则锁定（账号+IP）
     */
    private void recordFail(String failKey, String lockKey) {
        try {
            Long count = stringRedisTemplate.opsForValue().increment(failKey);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(failKey, LOGIN_FAIL_TTL);
            }
            if (count != null && count >= LOGIN_MAX_FAIL) {
                stringRedisTemplate.opsForValue().set(lockKey, "1", LOGIN_LOCK_TTL);
                log.warn("登录审计：账号+IP 触发锁定 {}", lockKey);
            }
        } catch (Exception e) {
            log.debug("登录失败计数降级（Redis 不可用）：{}", e.getMessage());
        }
    }

    /**
     * 解析客户端 IP（优先 X-Forwarded-For）
     */
    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 当前用户登出
     */
    public void logout() {
        StpUtil.logout();
    }

    /**
     * 查询当前登录用户信息（账号被禁用时拒绝返回）。
     */
    public UserInfo currentUser() {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号已被禁用");
        }
        return DtoMapper.INSTANCE.toUserInfo(user, resolvePermissions(user.getRoleCode()));
    }

    /**
     * 修改当前用户昵称
     */
    public void updateNickname(String nickname) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        user.setNickname(nickname);
        userMapper.updateById(user);
        log.info("用户修改昵称成功：{}", userId);
    }

    /**
     * 上传并更新当前用户头像（经文件服务写入 MinIO）。
     */
    public String updateAvatar(MultipartFile file) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "头像文件为空");
        }
        try {
            String url = uploadAvatar(file);
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setAvatar(url);
                userMapper.updateById(user);
            }
            log.info("用户更新头像成功：{}", userId);
            return url;
        } catch (IOException e) {
            log.error("读取头像文件失败：{}", e.getMessage());
            throw new BizException(ErrorCode.FILE_ERROR, "头像文件读取失败");
        }
    }

    /**
     * 调用文件服务上传头像字节
     */
    private String uploadAvatar(MultipartFile file) throws IOException {
        var resp = fileClient.uploadAvatar(file.getBytes(), file.getContentType());
        if (resp == null || resp.getCode() != ErrorCode.SUCCESS.getCode()) {
            throw new BizException(ErrorCode.FILE_ERROR, "头像上传失败");
        }
        return resp.getData();
    }

    /**
     * 修改当前用户密码（校验原密码，BCrypt 加密新密码）。
     */
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

    /**
     * 解析角色权限码集合
     */
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

    /**
     * 按用户名查询用户
     */
    private User findByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username).last("LIMIT 1"));
    }
}
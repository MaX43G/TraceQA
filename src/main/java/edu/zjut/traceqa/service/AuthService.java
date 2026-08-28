package edu.zjut.traceqa.service;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.dev33.satoken.stp.StpUtil;
import edu.zjut.traceqa.common.auth.UserContext;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.model.dto.LoginRequest;
import edu.zjut.traceqa.model.vo.LoginResponse;
import edu.zjut.traceqa.model.dto.RegisterRequest;
import edu.zjut.traceqa.model.vo.UserInfo;
import edu.zjut.traceqa.model.po.Role;
import edu.zjut.traceqa.model.po.User;
import edu.zjut.traceqa.mapper.RoleMapper;
import edu.zjut.traceqa.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import edu.zjut.traceqa.common.convert.DtoMapper;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证服务。
 *
 * <p>负责注册、登录、登出、当前用户信息与昵称修改。密码统一 BCrypt 加密存储。
 * 登录态由 sa-token 管理（Redis 持久化 + 单端登录：同一账号同时只允许一个用户在线）。
 * 用户名与密码校验失败均返回「用户名或密码错误」，避免账号枚举攻击。</p>
 */
@Slf4j
@Service
public class AuthService {

    /** 系统全部权限码（管理员角色持有，DB 层展开存储） */
    public static final List<String> ALL_PERMISSIONS = List.of(
            "user:manage", "role:manage", "kb:manage", "prompt:manage",
            "kb:view", "doc:view", "chat:manage"
    );
    /** 管理员权限码的逗号拼接串（写入角色表） */
    public static final String ALL_PERMISSIONS_STR = String.join(",", ALL_PERMISSIONS);

    @Resource
    private UserMapper userMapper;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private FileStorageService fileStorageService;

    /** 用户注册（默认角色 USER；账号需英文数字且唯一，注册后不可修改） */
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

    /** 用户登录（sa-token 单端登录：同一账号新登录会顶掉旧会话） */
    public LoginResponse login(LoginRequest request) {
        User user = findByUsername(request.getUsername());
        // 统一错误提示，防账号枚举
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号已被禁用");
        }
        List<String> permissions = resolvePermissions(user.getRoleCode());
        UserContext.LoginUser loginUser = new UserContext.LoginUser(
                user.getId(), user.getUsername(), user.getNickname(), user.getRoleCode(), permissions);
        StpUtil.login(user.getId());
        StpUtil.getSession().set(UserContext.SESSION_KEY, loginUser);
        String token = StpUtil.getTokenValue();
        log.info("用户登录成功：{}", request.getUsername());
        return LoginResponse.of(user, permissions, token);
    }

    /** 当前用户登出 */
    public void logout() {
        StpUtil.logout();
    }

    /** 查询当前登录用户信息（账号被禁用时拒绝返回并注销会话） */
    public UserInfo currentUser() {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        // 防御纵深：即使拦截器未拦截（如缓存延迟），被禁用账号也拿不到用户信息
        if (user.getStatus() == null || user.getStatus() != 1) {
            StpUtil.logout();
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号已被禁用");
        }
        return DtoMapper.INSTANCE.toUserInfo(user, resolvePermissions(user.getRoleCode()));
    }

    /** 修改当前用户昵称 */
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
        // 同步刷新会话中的用户信息
        UserContext.LoginUser lu = UserContext.get();
        if (lu != null) {
            lu.setNickname(nickname);
            StpUtil.getSession().set(UserContext.SESSION_KEY, lu);
        }
        log.info("用户修改昵称成功：{} → {}", user.getUsername(), nickname);
    }

    /** 上传并更新当前用户头像（文件经前端裁剪后提交） */
    public String updateAvatar(MultipartFile file) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "头像文件为空");
        }
        try {
            String url = fileStorageService.uploadAvatar(file.getBytes(), file.getContentType());
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

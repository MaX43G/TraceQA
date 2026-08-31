package edu.zjut.traceqa.userservice.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.zjut.traceqa.common.model.po.Role;
import edu.zjut.traceqa.common.model.po.User;
import edu.zjut.traceqa.userservice.mapper.RoleMapper;
import edu.zjut.traceqa.userservice.mapper.UserMapper;
import edu.zjut.traceqa.userservice.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务初始化数据装载。
 *
 * <p>启动时幂等地预置 ADMIN / USER 角色与默认管理员、普通用户账号。
 * 默认账号密码从环境变量读取（{@code DEFAULT_ADMIN_PASSWORD}/{@code DEFAULT_USER_PASSWORD}），
 * 未配置时跳过创建对应默认用户，避免在代码中硬编码弱口令。</p>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /** 默认管理员账号密码（从环境变量注入，可为空） */
    @Value("${DEFAULT_ADMIN_PASSWORD:}")
    private String defaultAdminPassword;

    /** 默认普通用户账号密码（从环境变量注入，可为空） */
    @Value("${DEFAULT_USER_PASSWORD:}")
    private String defaultUserPassword;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserMapper userMapper, RoleMapper roleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 启动装载初始化数据
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        safeInit(this::initRoles, "角色");
        safeInit(this::initUsers, "默认用户");
        log.info("用户服务初始化数据装载完成");
    }

    /**
     * 初始化角色（缺省创建）
     */
    private void initRoles() {
        saveRoleIfAbsent("ADMIN", "管理员", AuthService.ALL_PERMISSIONS_STR, "拥有系统全部权限");
        saveRoleIfAbsent("USER", "普通用户", "kb:view,doc:view,chat:manage", "课程问答与知识库查看权限");
    }

    /**
     * 初始化默认用户（缺省创建；未配置密码则跳过对应账号）
     */
    private void initUsers() {
        if (defaultAdminPassword == null || defaultAdminPassword.isBlank()) {
            log.warn("未配置 DEFAULT_ADMIN_PASSWORD，跳过创建默认管理员账号");
        } else {
            saveUserIfAbsent("admin", defaultAdminPassword, "系统管理员", "ADMIN");
        }
        if (defaultUserPassword == null || defaultUserPassword.isBlank()) {
            log.warn("未配置 DEFAULT_USER_PASSWORD，跳过创建默认普通用户账号");
        } else {
            saveUserIfAbsent("user", defaultUserPassword, "学生用户", "USER");
        }
    }

    private void saveRoleIfAbsent(String code, String name, String permissions, String desc) {
        Role exist = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, code));
        if (exist != null) {
            return;
        }
        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        role.setPermissions(permissions);
        role.setDescription(desc);
        roleMapper.insert(role);
    }

    private void saveUserIfAbsent(String username, String rawPassword, String nickname, String roleCode) {
        User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exist != null) {
            return;
        }
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .nickname(nickname)
                .roleCode(roleCode)
                .status(1)
                .build();
        userMapper.insert(user);
    }

    private void safeInit(Runnable task, String name) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("初始化「{}」失败（请检查表结构是否已迁移）：{}", name, e.getMessage());
        }
    }
}
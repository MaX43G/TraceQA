package edu.zjut.traceqa.config;

import jakarta.annotation.Resource;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.zjut.traceqa.model.po.KnowledgeBase;
import edu.zjut.traceqa.model.po.Role;
import edu.zjut.traceqa.model.po.SystemPrompt;
import edu.zjut.traceqa.model.po.User;
import edu.zjut.traceqa.mapper.KnowledgeBaseMapper;
import edu.zjut.traceqa.mapper.RoleMapper;
import edu.zjut.traceqa.mapper.SystemPromptMapper;
import edu.zjut.traceqa.mapper.UserMapper;
import edu.zjut.traceqa.mapper.AnnouncementMapper;
import edu.zjut.traceqa.model.po.Announcement;
import edu.zjut.traceqa.service.AuthService;
import edu.zjut.traceqa.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统初始化数据装载器。
 *
 * <p>应用启动时幂等写入：默认角色（ADMIN/USER）、管理员账号、默认知识库、
 * 以及各 Agent 场景的默认系统提示词。</p>
 */
@Slf4j
@Component
public class DataInitializer implements ApplicationRunner {

    /** 管理员默认密码（生产环境务必通过环境变量覆盖后修改） */
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123456";
    private static final String DEFAULT_USER_PASSWORD = "user123456";

    @Resource
    private UserMapper userMapper;
    @Resource
    private RoleMapper roleMapper;
    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Resource
    private SystemPromptMapper systemPromptMapper;
    @Resource
    private AnnouncementMapper announcementMapper;
    @Resource
    private FileStorageService fileStorageService;
    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initRoles();
        initUsers();
        initKnowledgeBase();
        initSystemPrompts();
        initAnnouncement();
        fileStorageService.ensureBucketConfigured();
        log.info("系统初始化数据装载完成");
    }

    /** 初始化角色 */
    private void initRoles() {
        saveRoleIfAbsent("ADMIN", "管理员", AuthService.ALL_PERMISSIONS_STR,
                "拥有系统全部权限");
        saveRoleIfAbsent("USER", "普通用户",
                "kb:view,doc:view,chat:manage", "课程问答与知识库查看权限");
    }

    /** 初始化默认用户 */
    private void initUsers() {
        saveUserIfAbsent("admin", DEFAULT_ADMIN_PASSWORD, "系统管理员", "ADMIN");
        saveUserIfAbsent("user", DEFAULT_USER_PASSWORD, "学生用户", "USER");
    }

    /** 初始化默认知识库 */
    private void initKnowledgeBase() {
        long count = knowledgeBaseMapper.selectCount(null);
        if (count > 0) {
            return;
        }
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName("《数据挖掘》课程知识库");
        kb.setDescription("覆盖数据挖掘教材与课程 PPT 的核心知识");
        kb.setCourse("数据挖掘");
        kb.setStatus(1);
        knowledgeBaseMapper.insert(kb);
        log.info("已创建默认知识库：{}", kb.getName());
    }

    /** 初始化各 Agent 场景系统提示词 */
    private void initSystemPrompts() {
        PromptDefaults.CONTENT.forEach((scenario, content) -> {
            savePromptIfAbsent(scenario, PromptDefaults.NAMES.getOrDefault(scenario, scenario),
                    content, "系统预置提示词，管理员可编辑内容");
        });
    }

    /** 幂等写入角色 */
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

    /** 幂等写入用户 */
    private void saveUserIfAbsent(String username, String rawPassword, String nickname, String roleCode) {
        User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exist != null) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setRoleCode(roleCode);
        user.setStatus(1);
        userMapper.insert(user);
        log.info("已创建默认账号：{} / {}（角色：{}）", username, rawPassword, roleCode);
    }

    /** 初始化默认公告（幂等） */
    private void initAnnouncement() {
        long count = announcementMapper.selectCount(new LambdaQueryWrapper<Announcement>().eq(Announcement::getDeleted, 0));
        if (count > 0) {
            return;
        }
        Announcement a = new Announcement();
        a.setTitle("欢迎使用溯知 · TraceQA");
        a.setContent("欢迎使用《数据挖掘》智能问答平台。输入问题即可获得基于知识图谱与向量检索的智能回答，支持语音输入与「猜你想问」智能追问。");
        a.setEnabled(1);
        announcementMapper.insert(a);
        log.info("已创建默认公告");
    }

    /** 幂等写入系统提示词 */
    private void savePromptIfAbsent(String scenario, String name, String content, String remark) {
        SystemPrompt exist = systemPromptMapper.selectOne(
                new LambdaQueryWrapper<SystemPrompt>().eq(SystemPrompt::getScenario, scenario));
        if (exist != null) {
            return;
        }
        SystemPrompt prompt = new SystemPrompt();
        prompt.setScenario(scenario);
        prompt.setName(name);
        prompt.setContent(content);
        prompt.setEnabled(1);
        prompt.setRemark(remark);
        systemPromptMapper.insert(prompt);
    }
}
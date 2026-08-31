package edu.zjut.traceqa.qaservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.zjut.traceqa.common.enums.ChatRole;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.common.model.po.ChatMessage;
import edu.zjut.traceqa.common.model.po.ChatSession;
import edu.zjut.traceqa.common.model.vo.ChatMessageVO;
import edu.zjut.traceqa.common.model.vo.ReferenceVO;
import edu.zjut.traceqa.common.model.vo.SessionVO;
import edu.zjut.traceqa.common.model.vo.ThinkingNodeVO;
import edu.zjut.traceqa.common.util.JsonUtils;
import edu.zjut.traceqa.qaservice.mapper.ChatMessageMapper;
import edu.zjut.traceqa.qaservice.mapper.ChatSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话服务。
 *
 * <p>负责会话与消息的增删改查、逻辑删除与 Markdown 导出。
 * 删除均为逻辑删除（deleted 标记），底层数据保留可审计。</p>
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int TITLE_MAX_LENGTH = 20;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final JsonUtils jsonUtils;

    public ChatService(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper, JsonUtils jsonUtils) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.jsonUtils = jsonUtils;
    }

    /**
     * 创建会话
     */
    public ChatSession createSession(Long userId, String title, Long knowledgeBaseId) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(title == null || title.isBlank() ? "新对话" : title);
        session.setKnowledgeBaseId(knowledgeBaseId);
        session.setPinned(0);
        session.setStatus(1);
        sessionMapper.insert(session);
        return session;
    }

    /**
     * 获取或创建会话
     */
    public ChatSession getOrCreateSession(Long userId, Long sessionId, Long knowledgeBaseId, String firstMessage) {
        if (sessionId != null) {
            ChatSession session = requireOwnedSession(userId, sessionId);
            if (session.getTitle() == null || session.getTitle().isBlank() || "新对话".equals(session.getTitle())) {
                session.setTitle(buildTitle(firstMessage));
                sessionMapper.updateById(session);
            }
            return session;
        }
        return createSession(userId, buildTitle(firstMessage), knowledgeBaseId);
    }

    /**
     * 查询会话列表
     */
    public List<SessionVO> listSessions(Long userId) {
        return sessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .orderByDesc(ChatSession::getPinned)
                        .orderByDesc(ChatSession::getUpdateTime))
                .stream().map(SessionVO::of).toList();
    }

    /**
     * 查询会话消息
     */
    public List<ChatMessageVO> listMessages(Long userId, Long sessionId) {
        requireOwnedSession(userId, sessionId);
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getId))
                .stream().map(this::toVO).toList();
    }

    /**
     * 逻辑删除会话
     */
    public void deleteSession(Long userId, Long sessionId) {
        requireOwnedSession(userId, sessionId);
        sessionMapper.deleteById(sessionId);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
        log.info("逻辑删除会话：{}", sessionId);
    }

    /**
     * 逻辑删除单条消息（问答对一并删除）
     */
    public void deleteMessage(Long userId, Long messageId) {
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        requireOwnedSession(userId, message.getSessionId());
        List<ChatMessage> siblings = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, message.getSessionId())
                .orderByAsc(ChatMessage::getId));
        Long pairId = findPairId(message, siblings);
        if (pairId != null) {
            messageMapper.deleteById(pairId);
        }
        messageMapper.deleteById(messageId);
    }

    private Long findPairId(ChatMessage message, List<ChatMessage> siblings) {
        int index = siblings.indexOf(message);
        if (index < 0) {
            return null;
        }
        if (ChatRole.USER.name().equals(message.getRole())) {
            for (int i = index + 1; i < siblings.size(); i++) {
                if (ChatRole.ASSISTANT.name().equals(siblings.get(i).getRole())) {
                    return siblings.get(i).getId();
                }
            }
        } else {
            for (int i = index - 1; i >= 0; i--) {
                if (ChatRole.USER.name().equals(siblings.get(i).getRole())) {
                    return siblings.get(i).getId();
                }
            }
        }
        return null;
    }

    /**
     * 置顶/取消置顶会话
     */
    public void togglePin(Long userId, Long sessionId, boolean pinned) {
        ChatSession session = requireOwnedSession(userId, sessionId);
        session.setPinned(pinned ? 1 : 0);
        sessionMapper.updateById(session);
    }

    /**
     * 保存用户消息
     */
    public void saveUserMessage(Long sessionId, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(ChatRole.USER.name());
        message.setContent(content);
        message.setStatus(1);
        messageMapper.insert(message);
    }

    /**
     * 保存 AI 消息
     */
    public ChatMessage saveAssistantMessage(Long sessionId, String content, List<ThinkingNodeVO> thinkingTrace,
                                            List<ReferenceVO> references, long latencyMs) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(ChatRole.ASSISTANT.name());
        message.setContent(content);
        message.setThinkingTrace(jsonUtils.toJson(thinkingTrace));
        message.setReferences(jsonUtils.toJson(references));
        message.setLatencyMs(latencyMs);
        message.setStatus(1);
        messageMapper.insert(message);
        return message;
    }

    /**
     * 导出会话为 Markdown
     */
    public String exportMarkdown(Long userId, Long sessionId) {
        ChatSession session = requireOwnedSession(userId, sessionId);
        List<ChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getId));
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(session.getTitle()).append("\n\n");
        sb.append("> 导出时间：").append(LocalDateTime.now()).append("\n\n");
        for (ChatMessage msg : messages) {
            String roleLabel = ChatRole.USER.name().equals(msg.getRole()) ? "🧑 用户" : "🤖 AI 助手";
            sb.append("---\n\n## ").append(roleLabel).append("\n\n").append(msg.getContent()).append("\n\n");
            List<ReferenceVO> refs = jsonUtils.parseList(msg.getReferences());
            if (refs != null && !refs.isEmpty()) {
                sb.append("> 参考来源：\n");
                for (ReferenceVO ref : refs) {
                    sb.append("> [").append(ref.getIndex()).append("] ").append(ref.getTitle()).append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 校验会话归属
     */
    public ChatSession requireOwnedSession(Long userId, Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该会话");
        }
        return session;
    }

    /**
     * 构建最近 N 轮对话历史（时间升序）
     */
    public String buildHistoryText(Long sessionId, int limit) {
        var page = messageMapper.selectPage(new Page<>(1, limit),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getId));
        List<ChatMessage> records = page.getRecords();
        if (records.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = records.size() - 1; i >= 0; i--) {
            ChatMessage m = records.get(i);
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            String role = ChatRole.USER.name().equals(m.getRole()) ? "用户" : "AI";
            sb.append(role).append("：").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }

    private ChatMessageVO toVO(ChatMessage message) {
        List<ThinkingNodeVO> thinking = jsonUtils.parseList(message.getThinkingTrace());
        List<ReferenceVO> references = jsonUtils.parseList(message.getReferences());
        return ChatMessageVO.of(message, thinking, references);
    }

    private String buildTitle(String message) {
        if (message == null || message.isBlank()) {
            return "新对话";
        }
        String collapsed = message.replaceAll("\\s+", " ").trim();
        if (collapsed.length() <= TITLE_MAX_LENGTH) {
            return collapsed;
        }
        return collapsed.substring(0, TITLE_MAX_LENGTH) + "…";
    }
}
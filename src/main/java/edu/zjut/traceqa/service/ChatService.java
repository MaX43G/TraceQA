package edu.zjut.traceqa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.zjut.traceqa.common.enums.ChatRole;
import edu.zjut.traceqa.common.enums.ErrorCode;
import edu.zjut.traceqa.common.exception.BizException;
import edu.zjut.traceqa.common.util.JsonUtils;
import edu.zjut.traceqa.dto.chat.ChatMessageVO;
import edu.zjut.traceqa.dto.chat.ReferenceVO;
import edu.zjut.traceqa.dto.chat.SessionVO;
import edu.zjut.traceqa.dto.chat.ThinkingNodeVO;
import edu.zjut.traceqa.entity.ChatMessage;
import edu.zjut.traceqa.entity.ChatSession;
import edu.zjut.traceqa.mapper.ChatMessageMapper;
import edu.zjut.traceqa.mapper.ChatSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对话服务。
 *
 * <p>负责会话与消息的增删查、逻辑删除与 Markdown 导出。所有删除均为逻辑删除
 * （{@code deleted} 标记），底层数据可审计。</p>
 */
@Slf4j
@Service
public class ChatService {

    private static final int TITLE_MAX_LENGTH = 20;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final JsonUtils jsonUtils;

    public ChatService(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper, JsonUtils jsonUtils) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.jsonUtils = jsonUtils;
    }

    /** 创建会话 */
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

    /** 获取或创建会话（首条消息时自动创建，标题取消息摘要） */
    public ChatSession getOrCreateSession(Long userId, Long sessionId, Long knowledgeBaseId, String firstMessage) {
        if (sessionId != null) {
            ChatSession session = requireOwnedSession(userId, sessionId);
            // 标题为空时用首条消息补全
            if (session.getTitle() == null || session.getTitle().isBlank() || "新对话".equals(session.getTitle())) {
                session.setTitle(buildTitle(firstMessage));
                sessionMapper.updateById(session);
            }
            return session;
        }
        return createSession(userId, buildTitle(firstMessage), knowledgeBaseId);
    }

    /** 查询用户全部会话（未删除） */
    public List<SessionVO> listSessions(Long userId) {
        return sessionMapper.selectList(
                        new LambdaQueryWrapper<ChatSession>()
                                .eq(ChatSession::getUserId, userId)
                                .orderByDesc(ChatSession::getPinned)
                                .orderByDesc(ChatSession::getUpdateTime))
                .stream().map(SessionVO::of).toList();
    }

    /** 查询会话消息列表 */
    public List<ChatMessageVO> listMessages(Long userId, Long sessionId) {
        requireOwnedSession(userId, sessionId);
        return messageMapper.selectList(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getSessionId, sessionId)
                                .orderByAsc(ChatMessage::getId))
                .stream().map(this::toVO).toList();
    }

    /** 逻辑删除会话（级联删除消息） */
    public void deleteSession(Long userId, Long sessionId) {
        requireOwnedSession(userId, sessionId);
        sessionMapper.deleteById(sessionId);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
        log.info("逻辑删除会话：{}", sessionId);
    }

    /**
     * 逻辑删除单条消息，并级联删除与之配对的相邻消息（一问一答成对删除）。
     */
    public void deleteMessage(Long userId, Long messageId) {
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在");
        }
        requireOwnedSession(userId, message.getSessionId());

        // 查找配对消息：删除用户提问时连带其后的 AI 回答；删除 AI 回答时连带其前的用户提问
        List<ChatMessage> siblings = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, message.getSessionId())
                        .orderByAsc(ChatMessage::getId));
        Long pairId = findPairId(message, siblings);
        if (pairId != null) {
            messageMapper.deleteById(pairId);
        }
        messageMapper.deleteById(messageId);
    }

    /** 在会话消息列表中寻找与目标消息配对的相邻消息 ID */
    private Long findPairId(ChatMessage message, List<ChatMessage> siblings) {
        int idx = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getId().equals(message.getId())) {
                idx = i;
                break;
            }
        }
        if (idx == -1) {
            return null;
        }
        // 用户消息 → 其后最近的 AI 回答
        if (ChatRole.USER.name().equals(message.getRole())) {
            for (int i = idx + 1; i < siblings.size(); i++) {
                if (ChatRole.ASSISTANT.name().equals(siblings.get(i).getRole())) {
                    return siblings.get(i).getId();
                }
            }
            return null;
        }
        // AI 回答 → 其前最近的用户提问
        for (int i = idx - 1; i >= 0; i--) {
            if (ChatRole.USER.name().equals(siblings.get(i).getRole())) {
                return siblings.get(i).getId();
            }
        }
        return null;
    }

    /** 置顶/取消置顶会话 */
    public void togglePin(Long userId, Long sessionId, boolean pinned) {
        ChatSession session = requireOwnedSession(userId, sessionId);
        session.setPinned(pinned ? 1 : 0);
        sessionMapper.updateById(session);
    }

    /** 保存用户消息 */
    public ChatMessage saveUserMessage(Long sessionId, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(ChatRole.USER.name());
        message.setContent(content);
        message.setStatus(1);
        messageMapper.insert(message);
        return message;
    }

    /** 保存 AI 消息（含思考链路与引用 JSON） */
    public ChatMessage saveAssistantMessage(Long sessionId, String content,
                                            List<ThinkingNodeVO> thinkingTrace,
                                            List<ReferenceVO> references,
                                            long latencyMs) {
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

    /** 导出会话为 Markdown */
    public String exportMarkdown(Long userId, Long sessionId) {
        ChatSession session = requireOwnedSession(userId, sessionId);
        List<ChatMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getId));
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(session.getTitle()).append("\n\n");
        sb.append("> 导出时间：").append(java.time.LocalDateTime.now()).append("\n\n");
        for (ChatMessage msg : messages) {
            String role = ChatRole.USER.name().equals(msg.getRole()) ? "🧑 用户" : "🤖 AI 助手";
            sb.append("---\n\n## ").append(role).append("\n\n")
                    .append(msg.getContent()).append("\n\n");
            List<ReferenceVO> refs = jsonUtils.parseList(msg.getReferences(), ReferenceVO.class);
            if (!refs.isEmpty()) {
                sb.append("> 参考来源：\n");
                for (ReferenceVO ref : refs) {
                    sb.append("> [").append(ref.index()).append("] ")
                            .append(ref.title()).append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /** 校验会话归属并返回 */
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

    /** 消息实体转 DTO（解析思考链路与引用） */
    private ChatMessageVO toVO(ChatMessage message) {
        List<ThinkingNodeVO> thinking = jsonUtils.parseList(message.getThinkingTrace(), ThinkingNodeVO.class);
        List<ReferenceVO> references = jsonUtils.parseList(message.getReferences(), ReferenceVO.class);
        return ChatMessageVO.of(message, thinking, references);
    }

    /** 由首条消息生成会话标题 */
    private String buildTitle(String message) {
        if (message == null || message.isBlank()) {
            return "新对话";
        }
        String oneLine = message.replaceAll("\\s+", " ").trim();
        return oneLine.length() > TITLE_MAX_LENGTH
                ? oneLine.substring(0, TITLE_MAX_LENGTH) + "…"
                : oneLine;
    }
}
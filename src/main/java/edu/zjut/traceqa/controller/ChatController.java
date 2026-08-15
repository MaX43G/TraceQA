package edu.zjut.traceqa.controller;

import edu.zjut.traceqa.agent.RagAgentOrchestrator;
import edu.zjut.traceqa.common.api.ApiResponse;
import edu.zjut.traceqa.common.auth.UserContext;
import edu.zjut.traceqa.dto.chat.ChatMessageVO;
import edu.zjut.traceqa.dto.chat.ChatStreamRequest;
import edu.zjut.traceqa.dto.chat.SessionCreateRequest;
import edu.zjut.traceqa.dto.chat.SessionVO;
import edu.zjut.traceqa.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 对话接口。
 *
 * <p>核心能力：通过 SSE 实时推送 Agent 思考过程与流式回答；
 * 同时提供会话管理、消息查询、逻辑删除与 Markdown 导出。</p>
 */
@Tag(name = "对话", description = "RAG 对话、会话管理与导出")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final RagAgentOrchestrator orchestrator;
    private final Executor ragExecutor;

    public ChatController(ChatService chatService, RagAgentOrchestrator orchestrator,
                          @org.springframework.beans.factory.annotation.Qualifier("ragExecutor") Executor ragExecutor) {
        this.chatService = chatService;
        this.orchestrator = orchestrator;
        this.ragExecutor = ragExecutor;
    }

    @Operation(summary = "流式对话（SSE：thinking/delta/references/done/error 事件）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatStreamRequest request) {
        Long userId = UserContext.getUserId();
        SseEmitter emitter = new SseEmitter(0L);
        // 取消标志：连接断开/超时/前端中断时置位，通知编排器停止生成
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> {
            cancelled.set(true);
            emitter.complete();
        });
        emitter.onTimeout(() -> {
            cancelled.set(true);
            emitter.complete();
        });
        emitter.onError(t -> {
            cancelled.set(true);
            emitter.complete();
        });
        // 异步编排：不阻塞请求线程，通过 SSE 实时推送
        ragExecutor.execute(() -> orchestrator.streamChat(userId, request, emitter, cancelled));
        return emitter;
    }

    @Operation(summary = "创建会话")
    @PostMapping("/sessions")
    public ApiResponse<SessionVO> createSession(@Valid @RequestBody SessionCreateRequest request) {
        var session = chatService.createSession(UserContext.getUserId(), request.title(), request.knowledgeBaseId());
        return ApiResponse.ok(SessionVO.of(session));
    }

    @Operation(summary = "查询会话列表")
    @GetMapping("/sessions")
    public ApiResponse<List<SessionVO>> listSessions() {
        return ApiResponse.ok(chatService.listSessions(UserContext.getUserId()));
    }

    @Operation(summary = "查询会话消息")
    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<ChatMessageVO>> listMessages(@PathVariable Long id) {
        return ApiResponse.ok(chatService.listMessages(UserContext.getUserId(), id));
    }

    @Operation(summary = "导出会话为 Markdown")
    @GetMapping(value = "/sessions/{id}/export", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public String exportMarkdown(@PathVariable Long id) {
        return chatService.exportMarkdown(UserContext.getUserId(), id);
    }

    @Operation(summary = "置顶/取消置顶会话")
    @PutMapping("/sessions/{id}/pin")
    public ApiResponse<Void> togglePin(@PathVariable Long id, @RequestParam boolean pinned) {
        chatService.togglePin(UserContext.getUserId(), id, pinned);
        return ApiResponse.ok();
    }

    @Operation(summary = "逻辑删除会话")
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id) {
        chatService.deleteSession(UserContext.getUserId(), id);
        return ApiResponse.ok();
    }

    @Operation(summary = "逻辑删除单条消息")
    @DeleteMapping("/messages/{id}")
    public ApiResponse<Void> deleteMessage(@PathVariable Long id) {
        chatService.deleteMessage(UserContext.getUserId(), id);
        return ApiResponse.ok();
    }
}
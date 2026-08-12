package com.smartsql.controller;

import com.smartsql.dto.Result;
import com.smartsql.entity.Message;
import com.smartsql.service.ChatService;
import com.smartsql.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 问答 Controller - 核心对话端点
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private MessageService messageService;

    /**
     * 查询会话消息列表
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<Message>> messages(@PathVariable Long sessionId) {
        return Result.ok(messageService.listBySession(sessionId));
    }

    /**
     * 流式问答（SSE）—— 核心端点
     *
     * 请求体：{ "question": "...", "commandType": "generate", "context": {...} }
     * 响应：text/event-stream
     */
    @PostMapping(value = "/sessions/{sessionId}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable Long sessionId, @RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        String commandType = (String) body.getOrDefault("commandType", "generate");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.get("context");

        // 更新会话名（如果是首条消息）
        chatService.updateSessionNameIfFirstMessage(sessionId, question);

        // SseEmitter 超时 5 分钟
        SseEmitter emitter = new SseEmitter(300_000L);

        // 调用 AI 引擎流
        Flux<String> aiStream = chatService.chatStream(sessionId, question, commandType, context);

        StringBuilder fullContent = new StringBuilder();
        StringBuilder thinkingContent = new StringBuilder();

        aiStream.subscribe(
                chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                        // 累积内容（用于后续保存，简化处理）
                        if (chunk.contains("\"type\":\"content\"")) {
                            // 简单提取内容（生产环境建议用 JSON 解析）
                            fullContent.append(chunk);
                        } else if (chunk.contains("\"type\":\"thinking\"")) {
                            thinkingContent.append(chunk);
                        }
                    } catch (Exception e) {
                        // 客户端断开会触发
                    }
                },
                error -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .data("{\"type\":\"error\",\"content\":\"" + error.getMessage() + "\"}"));
                        emitter.complete();
                    } catch (Exception ex) {
                        emitter.completeWithError(ex);
                    }
                },
                () -> {
                    try {
                        // 流结束，保存 AI 回复
                        chatService.saveAssistantMessage(sessionId,
                                fullContent.toString(),
                                thinkingContent.toString(),
                                commandType);
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    }
                }
        );

        return emitter;
    }

    /**
     * 更新消息反馈（点赞/点踩）
     */
    @PutMapping("/messages/{id}/feedback")
    public Result<?> feedback(@PathVariable Long id, @RequestParam String feedback) {
        messageService.updateFeedback(id, feedback);
        return Result.ok();
    }
}

package com.smartsql.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsql.dto.Result;
import com.smartsql.entity.Message;
import com.smartsql.service.ChatService;
import com.smartsql.service.MessageService;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestController
@RequestMapping("/api")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private ObjectMapper objectMapper;

    /** SSE 事件类型常量（修复题7：替代脆弱的字符串 contains 判断） */
    private static final String SSE_TYPE_CONTENT = "content";
    private static final String SSE_TYPE_THINKING = "thinking";
    private static final String SSE_TYPE_DONE = "done";

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
        // 浏览器测试发现：前端 context 可能是数组或对象，不做强转直接透传给 Python
        Object context = body.get("context");

        // 注意：首条消息自动命名已移至 ChatService.chatStream 内部
        // （在保存用户消息之后执行，保证消息数判断正确）

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
                        // 修复题7：解析 SSE JSON，只提取纯文本内容落库
                        // 原实现直接把 JSON 原始串 append 进数据库（非纯文本也非合法 JSON）
                        appendChunkContent(chunk, fullContent, thinkingContent, sessionId);
                    } catch (Exception e) {
                        // 客户端断开会触发，不阻塞其他 chunk
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
     * 解析 SSE chunk JSON，提取纯文本追加到目标缓冲
     *
     * chunk 形如：{"type":"content","content":"你好"} 或 {"type":"thinking","content":"..."}
     * 使用 JsonNode.path()（取不到返回 null 而非抛异常），解析失败仅告警不中断流
     */
    private void appendChunkContent(String chunk, StringBuilder fullContent,
                                    StringBuilder thinkingContent, Long sessionId) {
        try {
            JsonNode node = objectMapper.readTree(chunk);
            String type = node.path("type").asText();

            if (SSE_TYPE_CONTENT.equals(type)) {
                fullContent.append(node.path("content").asText());
            } else if (SSE_TYPE_THINKING.equals(type)) {
                thinkingContent.append(node.path("content").asText()).append("\n");
            } else if (SSE_TYPE_DONE.equals(type)) {
                // 作业题4修复：AI 真实耗时在流的 done 事件里（Python 侧已统计），
                // 在流内消费它——而不是用 @Around 切 chat() 方法（那只能测到 SSE 建连耗时）
                log.info("[问答日志] 会话={} AI回答耗时={}秒", sessionId, node.path("elapsed").asDouble());
            }
        } catch (Exception e) {
            log.warn("SSE chunk 解析失败: {}", chunk, e);
        }
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

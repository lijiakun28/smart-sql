package com.smartsql.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
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

import java.util.LinkedHashMap;
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
        // 重新回答标记：跳过重复保存用户消息（修复，一问多答语义）
        boolean isRegenerate = Boolean.TRUE.equals(body.get("isRegenerate"));

        // 校验前置：question 为空时不能继续——否则会落一条空消息，
        // 并在 updateSessionNameIfFirstMessage 里对 null 调 length() 抛 NPE。
        // 注意这里【不抛异常】：本端点 produces=text/event-stream，
        // 交给 @ExceptionHandler 返回 JSON 会触发内容协商冲突（前端只拿到难诊断的 406）。
        // 改为推一个 error 事件后立即结束流——前端已有 error 事件处理分支。
        if (question == null || question.isBlank()) {
            return errorEmitter("问题内容不能为空");
        }

        // 注意：首条消息自动命名已移至 ChatService.chatStream 内部
        // （在保存用户消息之后执行，保证消息数判断正确）

        // SseEmitter 超时 5 分钟
        SseEmitter emitter = new SseEmitter(300_000L);

        // 调用 AI 引擎流
        Flux<String> aiStream = chatService.chatStream(sessionId, question, commandType, context, isRegenerate);

        StringBuilder fullContent = new StringBuilder();
        StringBuilder thinkingContent = new StringBuilder();
        // 修复：保存标志，防止断开路径和正常完成路径重复保存
        final boolean[] saved = {false};

        aiStream.subscribe(
                new org.reactivestreams.Subscriber<String>() {
                    private org.reactivestreams.Subscription subscription;

                    @Override
                    public void onSubscribe(org.reactivestreams.Subscription s) {
                        this.subscription = s;
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(String chunk) {
                        try {
                            emitter.send(SseEmitter.event().data(chunk));
                            appendChunkContent(chunk, fullContent, thinkingContent, sessionId);
                        } catch (Exception e) {
                            // 修复：客户端断开（用户点停止）——
                            // ① 取消上游订阅，停止从 Python 拉流（不再浪费 AI token）
                            // ② 保存"用户实际看到的部分回答"（需求：展示到停止前已回答的内容）
                            if (subscription != null) {
                                subscription.cancel();
                            }
                            if (!saved[0] && fullContent.length() > 0) {
                                saved[0] = true;
                                chatService.saveAssistantMessage(sessionId,
                                        fullContent.toString(),
                                        thinkingContent.toString(),
                                        commandType);
                            }
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        try {
                            emitter.send(SseEmitter.event().data(toErrorFrame(clientMessageOf(error))));
                            emitter.complete();
                        } catch (Exception ex) {
                            emitter.completeWithError(ex);
                        }
                    }

                    @Override
                    public void onComplete() {
                        try {
                            // 流正常结束，保存完整 AI 回复
                            if (!saved[0]) {
                                saved[0] = true;
                                Message savedMsg = chatService.saveAssistantMessage(sessionId,
                                        fullContent.toString(),
                                        thinkingContent.toString(),
                                        commandType);
                                emitSaved(emitter, savedMsg.getId());
                            }
                            emitter.complete();
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        }
                    }
                }
        );

        return emitter;
    }

    /**
     * 构造"AI 回复已落库"事件帧，携带数据库 id
     *
     * 为什么需要这个事件：前端在流式过程中只能拼出消息内容，拿不到 id
     * （id 由数据库自增生成）。而点赞/点踩的接口签名是 /api/messages/{id}/feedback，
     * 没有这个 id 就永远点不动——这正是"点赞点踩从未真正接通"的根因。
     */
    private String toSavedFrame(Long messageId) throws JsonProcessingException {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "saved");
        frame.put("messageId", messageId);
        return objectMapper.writeValueAsString(frame);
    }

    /**
     * 尽力回传 messageId。
     * 客户端已断开时发送必定失败，但不影响已经落库的结果，所以只告警不抛出。
     */
    private void emitSaved(SseEmitter emitter, Long messageId) {
        try {
            emitter.send(SseEmitter.event().data(toSavedFrame(messageId)));
        } catch (Exception e) {
            log.warn("回传 messageId 失败（客户端可能已断开）: {}", messageId);
        }
    }

    /**
     * 构造 SSE 错误帧
     *
     * ⚠️ 必须走 ObjectMapper，不能手拼字符串：异常信息里只要出现一个 `"` 或换行，
     * 拼出来的就是非法 JSON。而前端 api/chat.js 对 JSON.parse 失败是**静默吞掉**的，
     * 结果是界面永远停在 loading——比直接报错更难排查。
     */
    private String toErrorFrame(String message) throws JsonProcessingException {
        Map<String, String> frame = new LinkedHashMap<>();
        frame.put("type", "error");
        frame.put("content", message == null ? "AI 服务异常" : message);
        return objectMapper.writeValueAsString(frame);
    }

    /**
     * 把异常翻译成可以安全展示给用户的消息
     *
     * 约定与 GlobalExceptionHandler 保持一致：
     * - IllegalArgumentException 是业务侧【有意】抛出的，消息本就是给用户看的
     *   （如 "会话不存在或已删除"），必须原样返回
     * - 其余异常一律通用文案。底层异常消息常携带内部拓扑——实测有
     *   "Connection refused: getsockopt: localhost/127.0.0.1:8000" 被原样推到前端；
     *   真实异常只写服务端日志
     */
    private String clientMessageOf(Throwable error) {
        if (error instanceof IllegalArgumentException) {
            return error.getMessage() != null ? error.getMessage() : "请求参数有误";
        }
        log.error("AI 引擎调用失败", error);
        return "AI 服务异常，请稍后重试";
    }

    /**
     * 立即推送一个 error 事件并结束的 SSE 流（用于请求校验失败等前置场景）
     */
    private SseEmitter errorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(300_000L);
        try {
            emitter.send(SseEmitter.event().data(toErrorFrame(message)));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
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
                // 修复：AI 真实耗时在流的 done 事件里（Python 侧已统计），
                // 在流内消费它——而不是用 @Around 切 chat() 方法（那只能测到 SSE 建连耗时）
                log.info("[问答日志] 会话={} AI回答耗时={}秒", sessionId, node.path("elapsed").asDouble());
            }
        } catch (Exception e) {
            log.warn("SSE chunk 解析失败: {}", chunk, e);
        }
    }

    /**
     * 更新消息反馈（点赞 / 点踩 / 取消）
     *
     * feedback 传 like 或 dislike；传空串表示取消之前的反馈
     * （需求 3.1.6.9：点击后颜色变深，用户也可取消点赞、点踩）。
     */
    @PutMapping("/messages/{id}/feedback")
    public Result<?> feedback(@PathVariable Long id, @RequestParam String feedback) {
        if (!feedback.isEmpty() && !"like".equals(feedback) && !"dislike".equals(feedback)) {
            throw new IllegalArgumentException("feedback 只能是 like 或 dislike");
        }
        messageService.updateFeedback(id, feedback.isEmpty() ? null : feedback);
        return Result.ok();
    }
}

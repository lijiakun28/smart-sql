package com.smartsql.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartsql.entity.Message;
import com.smartsql.entity.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 问答业务 Service
 * 协调会话管理、消息存储、AI 调用
 */
@Slf4j
@Service
public class ChatService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private AiProxyService aiProxyService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 流式问答
     *
     * @param sessionId   会话ID
     * @param question    用户问题
     * @param commandType 命令类型
     * @param context     上下文
     * @return SSE 流
     */
    public Flux<String> chatStream(Long sessionId, String question,
                                    String commandType, Object context, boolean isRegenerate) {
        // 0. 修复：校验会话有效性，防止消息被写入已删除的会话
        //    （message 表无外键约束，不做校验消息会静默落入 status=0 的会话）
        Session session = sessionService.getById(sessionId);
        if (session == null || session.getStatus() == null || session.getStatus() != 1) {
            return Flux.error(new IllegalArgumentException("会话不存在或已删除"));
        }

        // 1. 保存用户消息（重新回答时跳过——一问多答语义，避免重复落库）
        //
        // ⚠️ 这里刻意【不加 @Transactional】。本方法确实有两次写库（插消息 + 自动命名），
        // 但自动命名纯属装饰：若为它加上事务，命名一旦失败会把用户刚问的问题一起回滚，
        // 用户看到报错、问题凭空消失——比"会话名没改成"糟糕得多。
        // 事务该用在"要么全成要么全败"的场景，而不是"主操作 + 可丢弃的附加动作"。
        if (!isRegenerate) {
            Message userMsg = new Message();
            userMsg.setSessionId(sessionId);
            userMsg.setRole("user");
            userMsg.setContent(question);
            userMsg.setCommandType(commandType);
            userMsg.setContextJson(serializeContext(context));
            messageService.save(userMsg);

            // 2. 首条消息自动命名
            //    必须在保存用户消息之后调用：count==1 的判断依赖消息已入库
            //    （验证测试发现的顺序 bug：原 Controller 在保存前调用，count 恒为 0）
            updateSessionNameIfFirstMessage(sessionId, question);
        }

        // 3. 构建多轮对话历史（修复：SQL 层取最近6条，截断策略只在 Java 一处）
        List<Map<String, Object>> history = new java.util.ArrayList<>();
        for (Message msg : messageService.listRecentBySession(sessionId, 6)) {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("role", msg.getRole());
            item.put("content", msg.getContent());
            history.add(item);
        }

        // 4. 调用 AI 引擎，返回流
        return aiProxyService.chatStream(question, commandType, context, history);
    }

    /**
     * 上下文序列化为合法 JSON，用于落库到 message.context_json
     *
     * ⚠️ 不能用 context.toString()：context 经 Jackson 反序列化后是 LinkedHashMap/ArrayList，
     * toString() 产出的是 Java 的表示形式（{items=[{id=term_1, name=用户数}]}）而非合法 JSON，
     * 写进 context_json 后任何 JSON 解析器都读不了，等于上下文记录静默损坏。
     */
    private String serializeContext(Object context) {
        if (context == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            log.warn("上下文序列化失败，context_json 置空", e);
            return null;
        }
    }

    /**
     * 保存 AI 回复
     */
    public Message saveAssistantMessage(Long sessionId, String content,
                                         String thinkingProcess, String commandType) {
        Message msg = new Message();
        msg.setSessionId(sessionId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setThinkingProcess(thinkingProcess);
        msg.setCommandType(commandType);
        messageService.save(msg);
        return msg;
    }

    /**
     * 更新会话名称（取第一条用户消息前100字）
     *
     * 修复（两个问题）：
     * 1. 效率：原实现 sessionService.list() 全表拉取后内存过滤 → 改为主键查询
     * 2. bug：原以"名称等于默认值"判断首条消息，用户手动改回"新会话"会被误覆盖
     *    → 改为以消息数判断（刚保存第一条用户消息后 count==1）
     */
    public void updateSessionNameIfFirstMessage(Long sessionId, String question) {
        long messageCount = messageService.countBySession(sessionId);
        if (messageCount == 1) {
            String name = question.length() > 100 ? question.substring(0, 100) : question;
            sessionService.rename(sessionId, name);
        }
    }
}

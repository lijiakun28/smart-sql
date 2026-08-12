package com.smartsql.service;

import com.smartsql.entity.Message;
import com.smartsql.entity.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 问答业务 Service
 * 协调会话管理、消息存储、AI 调用
 */
@Service
public class ChatService {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private AiProxyService aiProxyService;

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
                                    String commandType, Map<String, Object> context) {
        // 1. 保存用户消息
        Message userMsg = new Message();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("user");
        userMsg.setContent(question);
        userMsg.setCommandType(commandType);
        userMsg.setContextJson(context != null ? context.toString() : null);
        messageService.save(userMsg);

        // 2. 更新会话时间（通过保存一条消息，MyBatis-Plus 自动更新 update_time）
        // 3. 调用 AI 引擎，返回流
        return aiProxyService.chatStream(question, commandType, context);
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
     */
    public void updateSessionNameIfFirstMessage(Long sessionId, String question) {
        Session session = sessionService.list().stream()
                .filter(s -> s.getId().equals(sessionId))
                .findFirst().orElse(null);
        if (session != null && "新会话".equals(session.getSessionName())) {
            String name = question.length() > 100 ? question.substring(0, 100) : question;
            sessionService.rename(sessionId, name);
        }
    }
}

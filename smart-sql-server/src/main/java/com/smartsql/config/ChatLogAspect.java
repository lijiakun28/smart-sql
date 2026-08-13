package com.smartsql.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 问答日志切面
 *
 * 需求文档要求：记录问答日志（对话内容、对话时间、对话用户），
 * 保存时长至少 3 个月，用于用户使用行为分析。
 *
 * 本切面记录问答请求入口日志（消息明细已落 message 表，日志表可后续扩展）。
 */
@Slf4j
@Aspect
@Component
public class ChatLogAspect {

    /**
     * 问答请求入口：记录会话、问题、命令类型
     */
    @Before("execution(* com.smartsql.controller.ChatController.chat(..)) && args(sessionId, body, ..)")
    public void logChatRequest(JoinPoint joinPoint, Long sessionId, Map<String, Object> body) {
        String question = String.valueOf(body.get("question"));
        // 日志脱敏：问题内容截断到 200 字符，避免超长文本刷爆日志
        String shortQuestion = question.length() > 200
                ? question.substring(0, 200) + "...(共" + question.length() + "字)"
                : question;
        log.info("[问答日志] 会话={}, 命令={}, 问题={}",
                sessionId, body.getOrDefault("commandType", "generate"), shortQuestion);
    }

    /**
     * 问答完成：记录会话ID（响应明细已由 ChatController 落库）
     */
    @AfterReturning("execution(* com.smartsql.controller.ChatController.chat(..))")
    public void logChatFinished(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] instanceof Long) {
            log.info("[问答日志] 会话={} 流式响应已建立", args[0]);
        }
    }
}

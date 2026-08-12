package com.smartsql.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI 引擎代理服务
 * 调用 Python AI 引擎的 REST API
 */
@Service
public class AiProxyService {

    @Value("${smart-sql.ai-engine-url}")
    private String aiEngineUrl;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public AiProxyService(ObjectMapper objectMapper) {
        this.webClient = WebClient.create();
        this.objectMapper = objectMapper;
    }

    /**
     * 流式调用 AI 引擎（SSE）
     *
     * @param question    用户问题
     * @param commandType 命令类型
     * @param context     上下文信息
     * @return Flux<String> 流式响应
     */
    public Flux<String> chatStream(String question, String commandType, Map<String, Object> context) {
        Map<String, Object> requestBody = Map.of(
                "question", question,
                "command_type", commandType != null ? commandType : "generate",
                "context", context != null ? context : Map.of()
        );

        return webClient.post()
                .uri(aiEngineUrl + "/ai/generate/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class);
    }

    /**
     * 非流式调用 AI 引擎
     */
    public String chatSync(String question, String commandType, Map<String, Object> context) {
        Map<String, Object> requestBody = Map.of(
                "question", question,
                "command_type", commandType != null ? commandType : "generate",
                "context", context != null ? context : Map.of()
        );

        return webClient.post()
                .uri(aiEngineUrl + "/ai/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * 同步知识库到 AI 引擎的向量库
     */
    public void syncKnowledge(Map<String, Object> syncRequest) {
        webClient.post()
                .uri(aiEngineUrl + "/ai/knowledge/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(syncRequest)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}

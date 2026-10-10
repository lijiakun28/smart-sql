package com.smartsql.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;

/**
 * AI 引擎代理服务
 * 调用 Python AI 引擎的 REST API
 */
@Slf4j
@Service
public class AiProxyService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public AiProxyService(ObjectMapper objectMapper,
                          @Value("${smart-sql.ai-engine-url}") String aiEngineUrl,
                          @Value("${smart-sql.internal-token:}") String internalToken) {
        // ⚠️ aiEngineUrl 必须用构造器参数注入：
        // @Value 字段注入发生在构造器执行之后，在构造器里读字段拿到的是 null，
        // 会导致 WebClient 无 baseUrl、请求打到 localhost:80（验证时踩过的坑）

        // 修复：WebClient 必须配置超时，否则 AI 服务挂起时线程池会被耗尽
        HttpClient httpClient = HttpClient.create()
                // 连接超时 5 秒：AI 服务不可达时快速失败
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                // 响应超时 120 秒：SSE 流式生成 SQL 需要较长时间，不能设太短
                .responseTimeout(Duration.ofSeconds(120));

        WebClient.Builder builder = WebClient.builder()
                .baseUrl(aiEngineUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient));

        // 统一附带内部令牌：AI 引擎侧 /ai/knowledge/sync 会校验 X-Internal-Token。
        // 用 defaultHeader 一次配好，避免每个调用点各写一遍。
        if (internalToken != null && !internalToken.isBlank()) {
            builder.defaultHeader("X-Internal-Token", internalToken);
        } else {
            log.warn("INTERNAL_API_TOKEN 未配置，知识库同步会被 AI 引擎拒绝"
                    + "（需 Java 与 AI 引擎两侧配置同名环境变量）");
        }

        this.webClient = builder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * 流式调用 AI 引擎（SSE）
     *
     * @param question    用户问题
     * @param commandType 命令类型
     * @param context     上下文信息
     * @param history     多轮对话历史 [{role, content}, ...]
     * @return Flux<String> 流式响应
     */
    public Flux<String> chatStream(String question, String commandType, Object context,
                                   java.util.List<Map<String, Object>> history) {
        Map<String, Object> requestBody = new java.util.LinkedHashMap<>();
        requestBody.put("question", question);
        requestBody.put("command_type", commandType != null ? commandType : "generate");
        requestBody.put("context", context != null ? context : Map.of());
        requestBody.put("history", history != null ? history : java.util.List.of());

        return webClient.post()
                .uri("/ai/generate/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class);
    }

    /**
     * 非流式调用 AI 引擎
     */
    public String chatSync(String question, String commandType, Object context) {
        Map<String, Object> requestBody = Map.of(
                "question", question,
                "command_type", commandType != null ? commandType : "generate",
                "context", context != null ? context : Map.of()
        );

        return webClient.post()
                .uri("/ai/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    /**
     * 同步知识库到 AI 引擎的向量库
     *
     * 修复：@Async 在独立线程池异步执行，不阻塞知识库 CRUD 的 HTTP 线程。
     * best-effort 语义保留：失败仅告警（由 KnowledgeService.syncToAi 的 try/catch 兜底），
     * 不会因为 AI 服务慢/挂而拖慢用户的知识库操作。
     */
    @Async("knowledgeSyncExecutor")
    public void syncKnowledge(Map<String, Object> syncRequest) {
        webClient.post()
                .uri("/ai/knowledge/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(syncRequest)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}

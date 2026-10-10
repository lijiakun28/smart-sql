package com.smartsql.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartsql.entity.Message;
import com.smartsql.entity.Session;
import com.smartsql.mapper.MessageMapper;
import com.smartsql.mapper.SessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话保留期清理
 *
 * 需求 3.1.5.4：历史会话仅保留 7 天。
 * 前端 HistorySessions 一直显示"仅保留7天内的对话"，但此前三端都没有任何清理逻辑
 * （全项目 grep @Scheduled 零命中）——那句话对用户是**虚假承诺**。这里补上真正的清理。
 */
@Slf4j
@Service
public class SessionCleanupService {

    @Autowired
    private SessionMapper sessionMapper;

    @Autowired
    private MessageMapper messageMapper;

    /** 会话保留天数（与前端文案保持一致，改这里也要改文案） */
    @Value("${smart-sql.session-retention-days:7}")
    private int retentionDays;

    /**
     * 每天凌晨清理超期会话。
     *
     * 覆盖已软删除的会话：它们对用户早已不可见，留着只是垃圾数据。
     * 单实例部署，无需分布式锁。
     */
    @Scheduled(cron = "${smart-sql.session-cleanup-cron:0 30 3 * * ?}")
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredSessions() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        List<Long> expiredIds = sessionMapper.selectList(
                        new LambdaQueryWrapper<Session>()
                                .select(Session::getId)
                                .lt(Session::getUpdateTime, cutoff))
                .stream()
                .map(Session::getId)
                .toList();

        if (expiredIds.isEmpty()) {
            log.info("[会话清理] 没有超过 {} 天的会话，跳过", retentionDays);
            return;
        }

        // ⚠️ 必须先删消息、再删会话：message 表【没有外键约束】，
        // 顺序反了就会留下一批再也没人引用的孤儿消息，越积越多。
        int messages = messageMapper.delete(
                new LambdaQueryWrapper<Message>().in(Message::getSessionId, expiredIds));
        int sessions = sessionMapper.delete(
                new LambdaQueryWrapper<Session>().in(Session::getId, expiredIds));

        log.info("[会话清理] 清理超过 {} 天的会话 {} 个、消息 {} 条", retentionDays, sessions, messages);
    }
}

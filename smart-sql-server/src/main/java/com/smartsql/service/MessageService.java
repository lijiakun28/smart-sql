package com.smartsql.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartsql.entity.Message;
import com.smartsql.mapper.MessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息管理 Service
 */
@Service
public class MessageService {

    @Autowired
    private MessageMapper messageMapper;

    /** 查询会话的所有消息 */
    public List<Message> listBySession(Long sessionId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sessionId)
               .orderByAsc(Message::getCreateTime);
        return messageMapper.selectList(wrapper);
    }

    /**
     * 查询会话最近 N 条消息（修复：SQL 层截断，避免全量拉取后内存丢弃）
     * 倒序取最新 N 条后反转恢复时间正序
     */
    public List<Message> listRecentBySession(Long sessionId, int limit) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sessionId)
               .orderByDesc(Message::getId)
               .last("LIMIT " + limit);
        List<Message> list = messageMapper.selectList(wrapper);
        java.util.Collections.reverse(list);
        return list;
    }

    /** 保存消息 */
    public Message save(Message message) {
        messageMapper.insert(message);
        return message;
    }

    /** 更新消息内容（如追加流式响应） */
    public boolean updateContent(Long id, String content, String thinkingProcess) {
        Message message = new Message();
        message.setId(id);
        message.setContent(content);
        message.setThinkingProcess(thinkingProcess);
        return messageMapper.updateById(message) > 0;
    }

    /**
     * 更新反馈（feedback 传 null 表示取消反馈）
     *
     * ⚠️ 必须用 UpdateWrapper 而不是 updateById：updateById 会忽略实体里的 null 字段，
     * 传 null 根本不会写进 SQL，"取消点赞"就变成静默无效。
     */
    public boolean updateFeedback(Long id, String feedback) {
        LambdaUpdateWrapper<Message> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Message::getId, id)
               .set(Message::getFeedback, feedback);
        return messageMapper.update(null, wrapper) > 0;
    }

    /** 统计会话消息数（修复题6：以消息数判断是否首条消息，而非会话名） */
    public long countBySession(Long sessionId) {
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Message::getSessionId, sessionId);
        return messageMapper.selectCount(wrapper);
    }
}

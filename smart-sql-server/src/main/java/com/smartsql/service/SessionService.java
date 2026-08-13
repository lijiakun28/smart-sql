package com.smartsql.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartsql.entity.Session;
import com.smartsql.mapper.SessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话管理 Service
 */
@Service
public class SessionService {

    @Autowired
    private SessionMapper sessionMapper;

    /** 创建会话 */
    public Session create(String sessionName) {
        Session session = new Session();
        session.setSessionName(sessionName != null ? sessionName : "新会话");
        session.setUserId("default");
        sessionMapper.insert(session);
        return session;
    }

    /** 按主键查询单个会话（修复题6：避免 list() 全表拉取后内存过滤） */
    public Session getById(Long id) {
        return sessionMapper.selectById(id);
    }

    /** 会话列表（按更新时间倒序） */
    public List<Session> list() {
        LambdaQueryWrapper<Session> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Session::getStatus, 1)
               .orderByDesc(Session::getUpdateTime);
        return sessionMapper.selectList(wrapper);
    }

    /** 模糊搜索会话（按名称） */
    public List<Session> search(String keyword) {
        LambdaQueryWrapper<Session> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Session::getStatus, 1)
               .like(Session::getSessionName, keyword)
               .orderByDesc(Session::getUpdateTime);
        return sessionMapper.selectList(wrapper);
    }

    /** 修改会话名称 */
    public boolean rename(Long id, String newName) {
        LambdaUpdateWrapper<Session> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Session::getId, id)
               .set(Session::getSessionName, newName);
        return sessionMapper.update(null, wrapper) > 0;
    }

    /** 删除会话（逻辑删除） */
    public boolean delete(Long id) {
        LambdaUpdateWrapper<Session> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Session::getId, id)
               .set(Session::getStatus, 0);
        return sessionMapper.update(null, wrapper) > 0;
    }
}

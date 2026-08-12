package com.smartsql.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartsql.entity.KnowledgeCaliber;
import com.smartsql.entity.KnowledgeTerm;
import com.smartsql.mapper.KnowledgeCaliberMapper;
import com.smartsql.mapper.KnowledgeTermMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 知识库管理 Service
 */
@Service
public class KnowledgeService {

    @Autowired
    private KnowledgeTermMapper termMapper;

    @Autowired
    private KnowledgeCaliberMapper caliberMapper;

    // ─── 业务术语库 ───

    public KnowledgeTerm createTerm(KnowledgeTerm term) {
        term.setStatus("new");
        termMapper.insert(term);
        return term;
    }

    public Map<String, Object> listTerms(String keyword, int page, int size) {
        LambdaQueryWrapper<KnowledgeTerm> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(KnowledgeTerm::getTermName, keyword);
        }
        wrapper.orderByDesc(KnowledgeTerm::getUpdateTime);

        Page<KnowledgeTerm> result = termMapper.selectPage(new Page<>(page, size), wrapper);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("records", result.getRecords());
        map.put("total", result.getTotal());
        map.put("page", result.getCurrent());
        map.put("size", result.getSize());
        return map;
    }

    public boolean updateTerm(Long id, KnowledgeTerm term) {
        term.setId(id);
        term.setStatus("modified");
        return termMapper.updateById(term) > 0;
    }

    public boolean deleteTerm(Long id) {
        return termMapper.deleteById(id) > 0;
    }

    public boolean changeTermStatus(Long id, String status) {
        LambdaUpdateWrapper<KnowledgeTerm> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(KnowledgeTerm::getId, id)
               .set(KnowledgeTerm::getStatus, status);
        return termMapper.update(null, wrapper) > 0;
    }

    public List<KnowledgeTerm> getActiveTerms() {
        LambdaQueryWrapper<KnowledgeTerm> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeTerm::getStatus, "active");
        return termMapper.selectList(wrapper);
    }

    // ─── 计算口径库 ───

    public KnowledgeCaliber createCaliber(KnowledgeCaliber caliber) {
        caliber.setStatus("new");
        caliberMapper.insert(caliber);
        return caliber;
    }

    public Map<String, Object> listCalibers(String keyword, int page, int size) {
        LambdaQueryWrapper<KnowledgeCaliber> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(KnowledgeCaliber::getCaliberName, keyword);
        }
        wrapper.orderByDesc(KnowledgeCaliber::getUpdateTime);

        Page<KnowledgeCaliber> result = caliberMapper.selectPage(new Page<>(page, size), wrapper);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("records", result.getRecords());
        map.put("total", result.getTotal());
        map.put("page", result.getCurrent());
        map.put("size", result.getSize());
        return map;
    }

    public boolean updateCaliber(Long id, KnowledgeCaliber caliber) {
        caliber.setId(id);
        caliber.setStatus("modified");
        return caliberMapper.updateById(caliber) > 0;
    }

    public boolean deleteCaliber(Long id) {
        return caliberMapper.deleteById(id) > 0;
    }

    public boolean changeCaliberStatus(Long id, String status) {
        LambdaUpdateWrapper<KnowledgeCaliber> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(KnowledgeCaliber::getId, id)
               .set(KnowledgeCaliber::getStatus, status);
        return caliberMapper.update(null, wrapper) > 0;
    }

    public List<KnowledgeCaliber> getActiveCalibers() {
        LambdaQueryWrapper<KnowledgeCaliber> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeCaliber::getStatus, "active");
        return caliberMapper.selectList(wrapper);
    }

    // ─── 上下文搜索（供问答时添加上下文使用） ───

    public List<Map<String, String>> searchKnowledge(String keyword) {
        List<Map<String, String>> results = new ArrayList<>();

        // 搜索业务术语
        LambdaQueryWrapper<KnowledgeTerm> termWrapper = new LambdaQueryWrapper<>();
        termWrapper.eq(KnowledgeTerm::getStatus, "active")
                   .like(KnowledgeTerm::getTermName, keyword)
                   .last("LIMIT 10");
        for (KnowledgeTerm term : termMapper.selectList(termWrapper)) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", "term_" + term.getId());
            item.put("type", "term");
            item.put("name", term.getTermName());
            item.put("description", term.getBusinessDesc());
            results.add(item);
        }

        // 搜索计算口径
        LambdaQueryWrapper<KnowledgeCaliber> caliberWrapper = new LambdaQueryWrapper<>();
        caliberWrapper.eq(KnowledgeCaliber::getStatus, "active")
                      .like(KnowledgeCaliber::getCaliberName, keyword)
                      .last("LIMIT 10");
        for (KnowledgeCaliber caliber : caliberMapper.selectList(caliberWrapper)) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", "caliber_" + caliber.getId());
            item.put("type", "caliber");
            item.put("name", caliber.getCaliberName());
            item.put("description", caliber.getBusinessDesc());
            item.put("sql", caliber.getSqlLogic());
            results.add(item);
        }

        return results;
    }
}

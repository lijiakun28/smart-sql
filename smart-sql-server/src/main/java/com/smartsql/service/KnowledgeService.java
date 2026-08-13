package com.smartsql.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartsql.entity.KnowledgeCaliber;
import com.smartsql.entity.KnowledgeTerm;
import com.smartsql.mapper.KnowledgeCaliberMapper;
import com.smartsql.mapper.KnowledgeTermMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 知识库管理 Service
 *
 * 知识库变更自动同步到 AI 引擎（作业题2的修复落地）：
 * MySQL 是唯一数据源，写操作成功后触发向量库同步；
 * 同步失败不影响主流程（best-effort + 告警日志），可通过手动脚本兜底。
 */
@Slf4j
@Service
public class KnowledgeService {

    @Autowired
    private KnowledgeTermMapper termMapper;

    @Autowired
    private KnowledgeCaliberMapper caliberMapper;

    @Autowired
    private AiProxyService aiProxyService;

    // ─── 业务术语库 ───

    public KnowledgeTerm createTerm(KnowledgeTerm term) {
        term.setStatus("new");
        termMapper.insert(term);
        // 注意：新建状态(new)不入向量库，由"生效"动作(changeTermStatus)触发入库
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
        boolean ok = termMapper.updateById(term) > 0;
        if (ok) {
            // 需求规定"只有生效的条目 AI 才能使用"，修改后状态为 modified，移出向量库
            syncTermToAi(term, "delete");
        }
        return ok;
    }

    public boolean deleteTerm(Long id) {
        boolean ok = termMapper.deleteById(id) > 0;
        if (ok) {
            KnowledgeTerm stub = new KnowledgeTerm();
            stub.setId(id);
            syncTermToAi(stub, "delete");
        }
        return ok;
    }

    public boolean changeTermStatus(Long id, String status) {
        LambdaUpdateWrapper<KnowledgeTerm> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(KnowledgeTerm::getId, id)
               .set(KnowledgeTerm::getStatus, status);
        boolean ok = termMapper.update(null, wrapper) > 0;
        if (ok) {
            // 只有生效状态的知识才对 AI 可见：active→入库(取完整记录), 其他→移出
            KnowledgeTerm current = termMapper.selectById(id);
            syncTermToAi(current, "active".equals(status) ? "upsert" : "delete");
        }
        return ok;
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
        // 新建状态(new)不入向量库，由"生效"动作触发入库（与术语库规则一致）
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
        boolean ok = caliberMapper.updateById(caliber) > 0;
        if (ok) {
            // 修改后状态为 modified，移出向量库（与术语库规则一致）
            syncCaliberToAi(caliber, "delete");
        }
        return ok;
    }

    public boolean deleteCaliber(Long id) {
        boolean ok = caliberMapper.deleteById(id) > 0;
        if (ok) {
            KnowledgeCaliber stub = new KnowledgeCaliber();
            stub.setId(id);
            syncCaliberToAi(stub, "delete");
        }
        return ok;
    }

    public boolean changeCaliberStatus(Long id, String status) {
        LambdaUpdateWrapper<KnowledgeCaliber> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(KnowledgeCaliber::getId, id)
               .set(KnowledgeCaliber::getStatus, status);
        boolean ok = caliberMapper.update(null, wrapper) > 0;
        if (ok) {
            // active→入库(取完整记录), 其他→移出
            KnowledgeCaliber current = caliberMapper.selectById(id);
            syncCaliberToAi(current, "active".equals(status) ? "upsert" : "delete");
        }
        return ok;
    }

    public List<KnowledgeCaliber> getActiveCalibers() {
        LambdaQueryWrapper<KnowledgeCaliber> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeCaliber::getStatus, "active");
        return caliberMapper.selectList(wrapper);
    }

    // ─── AI 引擎向量库同步（best-effort，失败不影响主流程） ───

    /** 同步业务术语到 AI 引擎 */
    private void syncTermToAi(KnowledgeTerm term, String action) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", term.getId());
        item.put("name", term.getTermName() != null ? term.getTermName() : "");
        item.put("description", term.getBusinessDesc() != null ? term.getBusinessDesc() : "");
        item.put("sql_logic", "");
        item.put("category", "term");
        syncToAi(item, action, "术语[" + term.getId() + "]");
    }

    /** 同步计算口径到 AI 引擎 */
    private void syncCaliberToAi(KnowledgeCaliber caliber, String action) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", caliber.getId());
        item.put("name", caliber.getCaliberName() != null ? caliber.getCaliberName() : "");
        item.put("description", caliber.getBusinessDesc() != null ? caliber.getBusinessDesc() : "");
        item.put("sql_logic", caliber.getSqlLogic() != null ? caliber.getSqlLogic() : "");
        item.put("category", "caliber");
        syncToAi(item, action, "口径[" + caliber.getId() + "]");
    }

    /** 调用 Python AI 引擎同步接口（异步提交，失败仅告警不抛出） */
    private void syncToAi(Map<String, Object> item, String action, String label) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("items", Collections.singletonList(item));
            request.put("action", action);
            // @Async 异步执行，此处仅提交任务；实际失败由 Spring 异步异常处理器记录日志
            aiProxyService.syncKnowledge(request);
            log.info("知识库同步 AI 引擎任务已提交: {} {}", action, label);
        } catch (Exception e) {
            log.warn("知识库同步 AI 引擎任务提交失败(不影响主流程,可手动跑 sync_knowledge.py 兜底): {} {} - {}",
                    action, label, e.getMessage());
        }
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

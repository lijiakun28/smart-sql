package com.smartsql.controller;

import com.smartsql.dto.Result;
import com.smartsql.entity.KnowledgeCaliber;
import com.smartsql.entity.KnowledgeTerm;
import com.smartsql.service.KnowledgeService;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理 Controller
 */
@Validated  // 修复：必须加 @Validated 才能让 @RequestParam 的 @Pattern 校验生效
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    // ─── 业务术语库 ───

    @GetMapping("/terms")
    public Result<Map<String, Object>> listTerms(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(knowledgeService.listTerms(keyword, page, size));
    }

    @PostMapping("/terms")
    public Result<KnowledgeTerm> createTerm(@RequestBody KnowledgeTerm term) {
        return Result.ok(knowledgeService.createTerm(term));
    }

    @PutMapping("/terms/{id}")
    public Result<?> updateTerm(@PathVariable Long id, @RequestBody KnowledgeTerm term) {
        knowledgeService.updateTerm(id, term);
        return Result.ok();
    }

    @DeleteMapping("/terms/{id}")
    public Result<?> deleteTerm(@PathVariable Long id) {
        knowledgeService.deleteTerm(id);
        return Result.ok();
    }

    @PutMapping("/terms/{id}/status")
    public Result<?> changeTermStatus(@PathVariable Long id,
            @RequestParam @Pattern(regexp = "active|inactive", message = "status 仅支持 active 或 inactive") String status) {
        knowledgeService.changeTermStatus(id, status);
        return Result.ok();
    }

    // ─── 计算口径库 ───

    @GetMapping("/calibers")
    public Result<Map<String, Object>> listCalibers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(knowledgeService.listCalibers(keyword, page, size));
    }

    @PostMapping("/calibers")
    public Result<KnowledgeCaliber> createCaliber(@RequestBody KnowledgeCaliber caliber) {
        return Result.ok(knowledgeService.createCaliber(caliber));
    }

    @PutMapping("/calibers/{id}")
    public Result<?> updateCaliber(@PathVariable Long id, @RequestBody KnowledgeCaliber caliber) {
        knowledgeService.updateCaliber(id, caliber);
        return Result.ok();
    }

    @DeleteMapping("/calibers/{id}")
    public Result<?> deleteCaliber(@PathVariable Long id) {
        knowledgeService.deleteCaliber(id);
        return Result.ok();
    }

    @PutMapping("/calibers/{id}/status")
    public Result<?> changeCaliberStatus(@PathVariable Long id,
            @RequestParam @Pattern(regexp = "active|inactive", message = "status 仅支持 active 或 inactive") String status) {
        knowledgeService.changeCaliberStatus(id, status);
        return Result.ok();
    }

    // ─── 上下文搜索 ───

    @GetMapping("/search")
    public Result<List<Map<String, String>>> search(@RequestParam String keyword) {
        return Result.ok(knowledgeService.searchKnowledge(keyword));
    }
}

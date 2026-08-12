package com.smartsql.controller;

import com.smartsql.dto.Result;
import com.smartsql.entity.Session;
import com.smartsql.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 会话管理 Controller
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    @Autowired
    private SessionService sessionService;

    /** 创建会话 */
    @PostMapping
    public Result<Session> create(@RequestParam(defaultValue = "新会话") String name) {
        return Result.ok(sessionService.create(name));
    }

    /** 会话列表 */
    @GetMapping
    public Result<List<Session>> list(@RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            return Result.ok(sessionService.search(keyword));
        }
        return Result.ok(sessionService.list());
    }

    /** 修改会话名称 */
    @PutMapping("/{id}/name")
    public Result<?> rename(@PathVariable Long id, @RequestParam String name) {
        sessionService.rename(id, name);
        return Result.ok();
    }

    /** 删除会话 */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        sessionService.delete(id);
        return Result.ok();
    }
}

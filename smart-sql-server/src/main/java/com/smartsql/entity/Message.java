package com.smartsql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 消息实体
 */
@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private String thinkingProcess;

    private String contextJson;

    private String commandType;

    private String feedback;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}

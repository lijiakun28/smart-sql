package com.smartsql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 业务术语库实体
 */
@Data
@TableName("knowledge_term")
public class KnowledgeTerm {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String termName;

    private String businessDesc;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

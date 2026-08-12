package com.smartsql.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 计算口径库实体
 */
@Data
@TableName("knowledge_caliber")
public class KnowledgeCaliber {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String caliberName;

    private String sqlLogic;

    private String businessDesc;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

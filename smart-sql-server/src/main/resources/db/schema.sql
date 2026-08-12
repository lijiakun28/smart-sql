-- =====================================================
-- 智能SQL - 数据库建表脚本
-- MySQL 8.0+
-- 使用方法: mysql -u root -p smart_sql < schema.sql
-- =====================================================

CREATE DATABASE IF NOT EXISTS smart_sql
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE smart_sql;

-- -----------------------------------------------------
-- 1. 会话表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS session (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_name VARCHAR(200)  NOT NULL DEFAULT 'New Session',
    user_id      VARCHAR(64)   NOT NULL DEFAULT 'default' COMMENT '预留多用户扩展',
    status       TINYINT       NOT NULL DEFAULT 1 COMMENT '1=活跃 0=已删除',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_status (user_id, status),
    INDEX idx_update_time (update_time)
) ENGINE=InnoDB COMMENT='会话表';

-- -----------------------------------------------------
-- 2. 消息表
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS message (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id       BIGINT       NOT NULL,
    role             VARCHAR(16)  NOT NULL COMMENT 'user | assistant',
    content          MEDIUMTEXT   NOT NULL COMMENT '消息正文',
    thinking_process MEDIUMTEXT   COMMENT 'AI思考过程（流式累积）',
    context_json     TEXT         COMMENT '上下文JSON（表名/代码片段等）',
    command_type     VARCHAR(32)  COMMENT '命令类型: generate|explain|find_table|find_code',
    feedback         VARCHAR(16)  COMMENT '用户反馈: like | dislike | null',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB COMMENT='消息表';

-- -----------------------------------------------------
-- 3. 业务术语库
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_term (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    term_name     VARCHAR(200)  NOT NULL COMMENT '术语名称',
    business_desc TEXT          COMMENT '业务描述',
    status        VARCHAR(16)   NOT NULL DEFAULT 'new' COMMENT 'new | active | inactive',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_term_name (term_name),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='业务术语库';

-- -----------------------------------------------------
-- 4. 计算口径库
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_caliber (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    caliber_name  VARCHAR(200)  NOT NULL COMMENT '口径名称',
    sql_logic     MEDIUMTEXT    COMMENT 'SQL逻辑',
    business_desc TEXT          COMMENT '业务描述',
    status        VARCHAR(16)   NOT NULL DEFAULT 'new' COMMENT 'new | active | inactive',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_caliber_name (caliber_name),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='计算口径库';

-- -----------------------------------------------------
-- 5. 知识库-表关联
-- -----------------------------------------------------
CREATE TABLE IF NOT EXISTS knowledge_table_rel (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id   BIGINT       NOT NULL COMMENT '关联的知识库条目ID',
    knowledge_type VARCHAR(16)  NOT NULL COMMENT 'term | caliber',
    table_en_name  VARCHAR(200) NOT NULL COMMENT '表英文名',
    table_cn_name  VARCHAR(200) COMMENT '表中文名',
    INDEX idx_knowledge (knowledge_id, knowledge_type)
) ENGINE=InnoDB COMMENT='知识库-表关联表';

-- =====================================================
-- 插入示例数据（用于 Day 3 测试 RAG 检索）
-- =====================================================

-- 示例业务术语
INSERT INTO knowledge_term (term_name, business_desc, status) VALUES
('5G套网用户', '指已经使用5G套餐的用户，包含订购了5G流量包和5G语音包的用户', 'active'),
('到达数', '统计周期内符合条件的用户数量，含新装和存量用户', 'active'),
('日活用户', '当日至少有一次使用记录的用户数，去重统计', 'active'),
('套餐订购', '用户订购指定套餐产品的行为记录，包括订购、变更、退订', 'active');

-- 示例计算口径
INSERT INTO knowledge_caliber (caliber_name, sql_logic, business_desc, status) VALUES
('当日5G套网到达数', 'SELECT COUNT(DISTINCT user_id) AS arrive_count\nFROM dwd_5g_user_info\nWHERE p_day = ''${bizdate}''\n  AND package_status = ''1''', '统计当日使用5G套餐的用户到达数，按用户去重', 'active'),
('近7日活跃用户数', 'SELECT p_day, COUNT(DISTINCT user_id) AS active_users\nFROM dwd_user_behavior_log\nWHERE p_day BETWEEN DATE_SUB(''${bizdate}'', INTERVAL 6 DAY) AND ''${bizdate}''\nGROUP BY p_day', '统计近7日每日活跃用户数', 'active');

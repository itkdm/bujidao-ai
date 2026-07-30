-- ACF 轻量管理模块初始化 SQL（MySQL）
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `acf_capability_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `capability_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '能力名称',
  `capability_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '1.0.0' COMMENT '能力版本',
  `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `description` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '描述',
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '分类',
  `risk_level` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '风险等级',
  `side_effect` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否有副作用',
  `confirmation_required` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否需要确认',
  `permission_mode` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '权限模式',
  `permissions_json` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '权限标识 JSON',
  `timeout_ms` int NOT NULL DEFAULT 30000 COMMENT '超时时间',
  `argument_type` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '入参 Java 类型',
  `return_type` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '返回 Java 类型',
  `input_schema_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '入参 Schema',
  `output_schema_json` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '出参 Schema',
  `definition_digest` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '定义摘要',
  `runtime_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '运行时状态：ACTIVE/MISSING',
  `last_scan_time` datetime NOT NULL COMMENT '最后扫描时间',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_acf_definition_tenant_name_version` (`tenant_id`, `capability_name`, `capability_version`) USING BTREE,
  KEY `idx_acf_definition_category` (`category`) USING BTREE,
  KEY `idx_acf_definition_status` (`runtime_status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ACF 能力定义';

CREATE TABLE IF NOT EXISTS `acf_invocation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `trace_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Trace ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户编号',
  `capability_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '能力名称',
  `capability_version` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '能力版本',
  `source` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '调用来源',
  `consumer_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '消费者类型',
  `consumer_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '消费者编号',
  `client_request_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端请求编号',
  `request_summary` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求摘要',
  `response_summary` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '响应摘要',
  `policy_summary` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '治理摘要',
  `runtime_summary` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '运行时摘要',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态',
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误码',
  `error_message` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息',
  `latency_ms` bigint NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_acf_invocation_trace` (`trace_id`) USING BTREE,
  KEY `idx_acf_invocation_capability` (`capability_name`, `capability_version`, `create_time`) USING BTREE,
  KEY `idx_acf_invocation_user` (`tenant_id`, `user_id`, `create_time`) USING BTREE,
  KEY `idx_acf_invocation_consumer` (`tenant_id`, `consumer_type`, `consumer_id`, `create_time`) USING BTREE,
  KEY `idx_acf_invocation_status` (`status`, `create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ACF 能力调用日志';

-- 兼容开发阶段曾导入过旧 ACF 表结构的本地库；全新库执行时不会产生变化。
SET @acf_schema = DATABASE();
SET @acf_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = @acf_schema AND table_name = 'acf_invocation_log' AND column_name = 'request_summary'),
  'SELECT 1',
  'ALTER TABLE `acf_invocation_log` ADD COLUMN `request_summary` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''请求摘要'' AFTER `client_request_id`'
);
PREPARE acf_stmt FROM @acf_sql;
EXECUTE acf_stmt;
DEALLOCATE PREPARE acf_stmt;

SET @acf_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = @acf_schema AND table_name = 'acf_invocation_log' AND column_name = 'runtime_summary'),
  'SELECT 1',
  'ALTER TABLE `acf_invocation_log` ADD COLUMN `runtime_summary` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''运行时摘要'' AFTER `policy_summary`'
);
PREPARE acf_stmt FROM @acf_sql;
EXECUTE acf_stmt;
DEALLOCATE PREPARE acf_stmt;

UPDATE `acf_invocation_log` SET `tenant_id` = 0 WHERE `tenant_id` IS NULL;
ALTER TABLE `acf_invocation_log`
  MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  MODIFY COLUMN `response_summary` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '响应摘要',
  MODIFY COLUMN `policy_summary` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '治理摘要',
  MODIFY COLUMN `error_message` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '错误信息';

INSERT INTO `system_menu`
(`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
VALUES
(881000, 'ACF 管理', '', 1, 99, 0, '/acf', 'ep:connection', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(881001, '能力目录', 'acf:capability:query', 2, 1, 881000, 'capability', 'ep:operation', 'acf/capability/index', 'AcfCapability', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(881002, '调用日志', 'acf:invocation-log:query', 2, 2, 881000, 'invocation-log', 'ep:document', 'acf/invocation-log/index', 'AcfInvocationLog', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'),
(881011, '同步能力', 'acf:capability:sync', 3, 1, 881001, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0')
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `permission` = VALUES(`permission`),
  `type` = VALUES(`type`),
  `sort` = VALUES(`sort`),
  `parent_id` = VALUES(`parent_id`),
  `path` = VALUES(`path`),
  `icon` = VALUES(`icon`),
  `component` = VALUES(`component`),
  `component_name` = VALUES(`component_name`),
  `status` = VALUES(`status`),
  `visible` = VALUES(`visible`),
  `keep_alive` = VALUES(`keep_alive`),
  `always_show` = VALUES(`always_show`),
  `updater` = VALUES(`updater`),
  `update_time` = VALUES(`update_time`),
  `deleted` = VALUES(`deleted`);

SET FOREIGN_KEY_CHECKS = 1;

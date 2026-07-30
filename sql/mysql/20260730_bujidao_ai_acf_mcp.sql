-- bujidao-ai ACF / MCP 增量 SQL（MySQL）
-- 适用对象：已经部署 ruoyi-vue-pro / 芋道源码 MySQL 基线库的项目。
-- 内容范围：
-- 1. ACF 能力目录表
-- 2. ACF 能力调用日志表
-- 3. ACF 高风险能力确认 challenge 表
-- 4. ACF 管理后台菜单与按钮权限
--
-- MCP OAuth 正式方案默认使用 Dynamic Client Registration，不需要预置固定 OAuth client。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for acf_capability_definition
-- ----------------------------
CREATE TABLE IF NOT EXISTS `acf_capability_definition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `capability_name` varchar(128) NOT NULL COMMENT '能力名称',
  `capability_version` varchar(32) NOT NULL DEFAULT '1.0.0' COMMENT '能力版本',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `description` varchar(1024) NOT NULL COMMENT '描述',
  `category` varchar(64) DEFAULT NULL COMMENT '分类',
  `risk_level` varchar(32) DEFAULT NULL COMMENT '风险等级',
  `side_effect` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否有副作用',
  `confirmation_required` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否需要确认',
  `permission_mode` varchar(32) DEFAULT NULL COMMENT '权限模式',
  `permissions_json` text COMMENT '权限标识 JSON',
  `timeout_ms` int NOT NULL DEFAULT 30000 COMMENT '超时时间，单位毫秒',
  `argument_type` varchar(256) DEFAULT NULL COMMENT '入参 Java 类型',
  `return_type` varchar(256) DEFAULT NULL COMMENT '返回 Java 类型',
  `input_schema_json` mediumtext COMMENT '入参 Schema JSON',
  `output_schema_json` mediumtext COMMENT '出参 Schema JSON',
  `definition_digest` varchar(128) DEFAULT NULL COMMENT '定义摘要',
  `runtime_status` varchar(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '运行时状态',
  `last_scan_time` datetime NOT NULL COMMENT '最后扫描时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_acf_definition_tenant_name_version` (`tenant_id`, `capability_name`, `capability_version`) USING BTREE,
  KEY `idx_acf_definition_category` (`category`) USING BTREE,
  KEY `idx_acf_definition_status` (`runtime_status`) USING BTREE,
  KEY `idx_acf_definition_scan_time` (`last_scan_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ACF 能力定义';

-- ----------------------------
-- Table structure for acf_invocation_log
-- ----------------------------
CREATE TABLE IF NOT EXISTS `acf_invocation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `trace_id` varchar(64) NOT NULL COMMENT 'Trace ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户编号',
  `capability_name` varchar(128) NOT NULL COMMENT '能力名称',
  `capability_version` varchar(32) DEFAULT NULL COMMENT '能力版本',
  `source` varchar(32) DEFAULT NULL COMMENT '调用来源',
  `consumer_type` varchar(32) DEFAULT NULL COMMENT '消费者类型',
  `consumer_id` varchar(128) DEFAULT NULL COMMENT '消费者编号',
  `client_request_id` varchar(128) DEFAULT NULL COMMENT '客户端请求编号',
  `request_summary` varchar(1024) DEFAULT NULL COMMENT '请求摘要',
  `response_summary` varchar(1024) DEFAULT NULL COMMENT '响应摘要',
  `policy_summary` varchar(1024) DEFAULT NULL COMMENT '治理摘要',
  `runtime_summary` varchar(1024) DEFAULT NULL COMMENT '运行时摘要',
  `status` varchar(32) NOT NULL COMMENT '状态',
  `error_code` varchar(64) DEFAULT NULL COMMENT '错误码',
  `error_message` varchar(1024) DEFAULT NULL COMMENT '错误信息',
  `latency_ms` bigint NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_acf_invocation_trace` (`trace_id`) USING BTREE,
  KEY `idx_acf_invocation_capability` (`tenant_id`, `capability_name`, `capability_version`, `create_time`) USING BTREE,
  KEY `idx_acf_invocation_user` (`tenant_id`, `user_id`, `create_time`) USING BTREE,
  KEY `idx_acf_invocation_consumer` (`tenant_id`, `consumer_type`, `consumer_id`, `create_time`) USING BTREE,
  KEY `idx_acf_invocation_status` (`tenant_id`, `status`, `create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ACF 能力调用日志';

-- ----------------------------
-- Table structure for acf_confirmation_challenge
-- ----------------------------
CREATE TABLE IF NOT EXISTS `acf_confirmation_challenge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `challenge_id` varchar(128) NOT NULL COMMENT '确认挑战编号',
  `capability_name` varchar(128) NOT NULL COMMENT '能力名称',
  `capability_version` varchar(32) NOT NULL COMMENT '能力版本',
  `risk_level` varchar(32) DEFAULT NULL COMMENT '风险等级',
  `user_id` bigint NOT NULL DEFAULT 0 COMMENT '用户编号',
  `source` varchar(32) DEFAULT NULL COMMENT '调用来源',
  `consumer_type` varchar(32) DEFAULT NULL COMMENT '消费者类型',
  `consumer_id` varchar(128) DEFAULT NULL COMMENT '消费者编号',
  `consumer_client_id` varchar(128) DEFAULT NULL COMMENT '消费者客户端编号',
  `client_request_id` varchar(128) DEFAULT NULL COMMENT '客户端请求编号',
  `idempotency_key` varchar(128) NOT NULL COMMENT '幂等键',
  `request_digest` varchar(128) NOT NULL COMMENT '请求摘要',
  `token_hash` varchar(128) DEFAULT NULL COMMENT '确认令牌哈希',
  `status` varchar(32) NOT NULL COMMENT '状态',
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  `confirmed_user_id` bigint DEFAULT NULL COMMENT '确认用户编号',
  `confirmed_time` datetime DEFAULT NULL COMMENT '确认时间',
  `confirm_remark` varchar(512) DEFAULT NULL COMMENT '确认备注',
  `used_trace_id` varchar(64) DEFAULT NULL COMMENT '消费调用 Trace ID',
  `used_time` datetime DEFAULT NULL COMMENT '消费时间',
  `creator` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_acf_confirm_challenge_id` (`challenge_id`) USING BTREE,
  UNIQUE KEY `uk_acf_confirm_token_hash` (`token_hash`) USING BTREE,
  KEY `idx_acf_confirm_scope` (`tenant_id`, `user_id`, `capability_name`, `capability_version`, `status`) USING BTREE,
  KEY `idx_acf_confirm_reusable` (`tenant_id`, `user_id`, `consumer_type`, `consumer_id`, `consumer_client_id`, `capability_name`, `capability_version`, `idempotency_key`, `status`, `expires_at`) USING BTREE,
  KEY `idx_acf_confirm_expires_at` (`expires_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ACF 确认挑战';

-- ----------------------------
-- ACF admin menus
-- ----------------------------
-- 菜单不使用固定 ID：优先复用已有 path / permission 对应记录，不存在时由 system_menu 自增主键生成。
SET @acf_parent_menu_id := (
  SELECT MIN(`id`) FROM `system_menu`
  WHERE `parent_id` = 0 AND `path` = '/acf'
);
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT 'ACF 管理', '', 1, 99, 0, '/acf', 'ep:connection', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @acf_parent_menu_id IS NULL;
SET @acf_parent_menu_id := IFNULL(@acf_parent_menu_id, LAST_INSERT_ID());
UPDATE `system_menu`
SET `name` = 'ACF 管理',
    `permission` = '',
    `type` = 1,
    `sort` = 99,
    `parent_id` = 0,
    `path` = '/acf',
    `icon` = 'ep:connection',
    `component` = '',
    `component_name` = '',
    `status` = 0,
    `visible` = b'1',
    `keep_alive` = b'1',
    `always_show` = b'1',
    `updater` = 'admin',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `id` = @acf_parent_menu_id;

SET @acf_capability_menu_id := (
  SELECT MIN(`id`) FROM `system_menu`
  WHERE `type` = 2 AND `permission` = 'acf:capability:query'
);
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '能力目录', 'acf:capability:query', 2, 1, @acf_parent_menu_id, 'capability', 'ep:operation', 'acf/capability/index', 'AcfCapability', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @acf_capability_menu_id IS NULL;
SET @acf_capability_menu_id := IFNULL(@acf_capability_menu_id, LAST_INSERT_ID());
UPDATE `system_menu`
SET `name` = '能力目录',
    `permission` = 'acf:capability:query',
    `type` = 2,
    `sort` = 1,
    `parent_id` = @acf_parent_menu_id,
    `path` = 'capability',
    `icon` = 'ep:operation',
    `component` = 'acf/capability/index',
    `component_name` = 'AcfCapability',
    `status` = 0,
    `visible` = b'1',
    `keep_alive` = b'1',
    `always_show` = b'1',
    `updater` = 'admin',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `id` = @acf_capability_menu_id;

SET @acf_invocation_log_menu_id := (
  SELECT MIN(`id`) FROM `system_menu`
  WHERE `type` = 2 AND `permission` = 'acf:invocation-log:query'
);
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '调用日志', 'acf:invocation-log:query', 2, 2, @acf_parent_menu_id, 'invocation-log', 'ep:document', 'acf/invocation-log/index', 'AcfInvocationLog', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @acf_invocation_log_menu_id IS NULL;
SET @acf_invocation_log_menu_id := IFNULL(@acf_invocation_log_menu_id, LAST_INSERT_ID());
UPDATE `system_menu`
SET `name` = '调用日志',
    `permission` = 'acf:invocation-log:query',
    `type` = 2,
    `sort` = 2,
    `parent_id` = @acf_parent_menu_id,
    `path` = 'invocation-log',
    `icon` = 'ep:document',
    `component` = 'acf/invocation-log/index',
    `component_name` = 'AcfInvocationLog',
    `status` = 0,
    `visible` = b'1',
    `keep_alive` = b'1',
    `always_show` = b'1',
    `updater` = 'admin',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `id` = @acf_invocation_log_menu_id;

SET @acf_sync_button_id := (
  SELECT MIN(`id`) FROM `system_menu`
  WHERE `type` = 3 AND `permission` = 'acf:capability:sync'
);
INSERT INTO `system_menu`
(`name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `create_time`, `updater`, `update_time`, `deleted`)
SELECT '同步能力', 'acf:capability:sync', 3, 1, @acf_capability_menu_id, '', '', '', '', 0, b'1', b'1', b'1', 'admin', NOW(), 'admin', NOW(), b'0'
WHERE @acf_sync_button_id IS NULL;
SET @acf_sync_button_id := IFNULL(@acf_sync_button_id, LAST_INSERT_ID());
UPDATE `system_menu`
SET `name` = '同步能力',
    `permission` = 'acf:capability:sync',
    `type` = 3,
    `sort` = 1,
    `parent_id` = @acf_capability_menu_id,
    `path` = '',
    `icon` = '',
    `component` = '',
    `component_name` = '',
    `status` = 0,
    `visible` = b'1',
    `keep_alive` = b'1',
    `always_show` = b'1',
    `updater` = 'admin',
    `update_time` = NOW(),
    `deleted` = b'0'
WHERE `id` = @acf_sync_button_id;

SET FOREIGN_KEY_CHECKS = 1;

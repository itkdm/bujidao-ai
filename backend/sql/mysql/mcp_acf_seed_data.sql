/*
 MCP / ACF 本地联调种子数据（MySQL）
 说明：
 1. application.yaml 里 yudao.mcp.server.required-scopes 配置为 mcp:access，
    而 ruoyi-vue-pro.sql 自带的 4 个 OAuth2 客户端都没有注册这个 scope，
    因此不导入本脚本时，任何令牌访问 /mcp 都会被 McpScopeAuthorizationFilter 判为 403。
 2. 本脚本只新增一个专用客户端 mcp-local-test，不修改 default 等既有客户端，
    避免影响管理后台的既有登录。
 3. 可重复执行（先按 client_id 物理删除再插入）。
 4. secret 是本地联调用的占位值，不是任何真实环境的凭据；
    部署到共享或生产环境时必须换成随机密钥，并通过配置中心/密钥管理下发，不要沿用本文件的值。
 5. ERP 能力还会校验 erp:product:query 等功能权限，这些菜单权限已包含在 ruoyi-vue-pro.sql 中，
    用超级管理员账号登录即可通过。
*/

-- 删除后重建，保证脚本可重复执行
DELETE FROM system_oauth2_client WHERE client_id = 'mcp-local-test';

INSERT INTO system_oauth2_client (
    client_id, secret, name, logo, description, status,
    access_token_validity_seconds, refresh_token_validity_seconds,
    redirect_uris, authorized_grant_types,
    scopes, auto_approve_scopes, authorities, resource_ids, additional_information,
    creator, updater, deleted
) VALUES (
    'mcp-local-test',
    'mcp-local-test-secret',
    'MCP 本地联调客户端',
    '',
    '仅用于本地 MCP / ACF 端到端联调，注册 mcp:access 授权范围',
    0, -- status：0 开启
    1800,
    2592000,
    '["http://127.0.0.1:48080"]',
    '["password","refresh_token","client_credentials"]',
    '["mcp:access"]',
    '["mcp:access"]',
    '["mcp:access"]',
    '[]',
    '{}',
    '1', '1', b'0'
);

-- 校验：应返回 1 行，且 scopes 含 mcp:access
SELECT client_id, status, scopes, auto_approve_scopes, authorized_grant_types
FROM system_oauth2_client
WHERE client_id = 'mcp-local-test' AND deleted = 0;

# SQL

这里存放 `bujidao-ai` 自己维护的数据库增量脚本、迁移说明和初始化补充数据。

上游芋道自带 SQL 保留在 `backend/sql/` 目录中。本项目自己的对外增量 SQL 只放在根目录 `sql/`，避免外部公司升级时分不清上游基线脚本和 `bujidao-ai` 正式增量脚本。

## 唯一对外入口

- `mysql/20260730_bujidao_ai_acf_mcp.sql`
  - ACF 能力目录表：`acf_capability_definition`
  - ACF 能力调用日志表：`acf_invocation_log`
  - ACF 高风险能力确认表：`acf_confirmation_challenge`
  - ACF 管理后台菜单：能力目录、调用日志、同步能力按钮

当前版本对外只维护这一份 MySQL 增量 SQL。不要再从 `backend/sql/mysql` 查找 `bujidao-ai` 自己的 ACF/MCP 初始化脚本；那里只保留上游或既有模块基线 SQL。

## 执行前提

目标数据库应已经导入并运行 ruoyi-vue-pro / 芋道源码的 MySQL 基线 SQL。

本脚本只补充 `bujidao-ai` 自己新增的 ACF/MCP 相关增量，不包含上游 ERP、系统管理、OAuth2、租户等基础表。

## 执行顺序

1. 确认目标库已完成上游 ruoyi-vue-pro 基线初始化或升级。
2. 执行 `mysql/20260730_bujidao_ai_acf_mcp.sql`。
3. 部署引入 `yudao-module-acf`、`yudao-spring-boot-starter-acf`、`yudao-spring-boot-starter-mcp` 以及需要暴露能力的业务模块。
4. 启用 MCP/ACF 配置后，在管理后台执行“同步能力”，把代码中的 `@AgentCapability` 扫描到能力目录。
5. 给需要管理 ACF 的后台角色分配“ACF 管理”菜单权限。脚本不会自动给任何角色授权。

## MCP OAuth 说明

正式 Remote MCP 认证默认使用 OAuth Dynamic Client Registration。Agent 客户端会按协议动态注册 public client，并写入上游已有的 `system_oauth2_client` 表。

因此，本目录不提供固定 `client_id` 的初始化数据。固定客户端只适合企业明确指定 redirect URI、并主动选择预置客户端的场景，不适合作为开源项目的默认增量 SQL。

开发验证、演示数据、一次性探针 SQL 不属于对外增量 SQL。若目标公司确实需要，应由接入方按自己的环境单独维护，不应混入本目录。

## 示例业务能力权限

本项目内置的 ERP ACF 能力只是示例业务能力，它复用上游 ERP 已有权限标识，例如 `erp:product:query`、`erp:sale-order:create`、`erp:purchase-order:update-status` 等。

如果目标公司没有使用 ERP 模块，不需要迁移这些 ERP 示例能力，也不需要导入 ERP 权限初始化数据。接入方应在自己的业务模块中新增 ACF 能力，并复用该业务原本 Web 端使用的菜单/按钮权限。MCP tools/list 和 tools/call 会按当前登录用户的权限过滤和校验能力。

如果目标公司已经使用上游 ERP 模块，并希望复用本项目 ERP 示例能力，通常只需要把原本 Web 端应有的 ERP 菜单/按钮权限分配给对应用户角色。

## 重复执行与回滚

脚本中的表结构使用 `CREATE TABLE IF NOT EXISTS`。菜单不使用固定 ID，会优先按顶级路径 `/acf`、页面/按钮权限标识复用已有记录；不存在时由 `system_menu` 自增主键动态生成，可在全新目标库上重复执行。

回滚时可按顶级路径 `/acf` 及 `acf:capability:*`、`acf:invocation-log:*` 权限标识删除菜单，并在确认无历史审计价值后删除 `acf_*` 三张表。生产环境删除调用日志和确认记录前应先备份。

## 安全要求

生产环境 SQL 不应包含本机数据库地址、真实 Token、密码、密钥或个人凭据。

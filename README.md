# bujidao-ai

`bujidao-ai` 是基于芋道源码 / ruoyi-vue-pro 的 AI 扩展项目。

项目目标是在成熟的企业级后台体系上，增量补充面向真实业务场景的 AI 能力。当前第一版重点聚焦：

- ACF 能力治理：把业务系统已有能力声明、治理并安全暴露给 AI / Agent。
- Remote MCP Server：让 Codex、WorkBuddy 等通用 MCP 客户端通过 OAuth 授权调用系统能力。

本项目不是 SaaS 服务，不提供统一的 MCP 地址。使用方需要自行部署后，使用自己的域名、用户、租户、权限和 OAuth 配置完成接入。

## 适合谁使用

- 已经在使用或准备使用芋道源码，希望在现有后台基础上增量接入 AI 能力的开发者。
- 想让企业内部 Agent / Coding Agent 安全调用业务系统能力的团队。
- 想参考企业级后台如何落地 ACF、Remote MCP、OAuth 授权、工具调用审计和业务能力治理的开发者。
- 基于 ruoyi-vue-pro 做二次开发，并希望持续跟进上游，同时保留 AI 扩展空间的项目。

## 当前状态

当前主线已完成第一版 ACF + MCP 基础能力：

- `yudao-spring-boot-starter-acf`：ACF 核心 starter。
- `yudao-spring-boot-starter-mcp`：Remote MCP Server starter。
- `yudao-module-acf`：ACF 管理、能力目录、调用日志、确认 challenge 等生产适配模块。
- `yudao-module-mcp`：MCP OAuth / Dynamic Client Registration / PKCE 适配模块。
- ERP 示例能力：覆盖商品、库存、客户、供应商、仓库、销售订单、采购订单和统计等典型场景。
- Vue3 管理后台页面：ACF 能力目录、调用日志、MCP OAuth 授权页。
- MySQL 正式增量 SQL：见 `sql/`。

RAG、Agent Runtime、知识库、长期记忆和更完整的 Agent 管理后台属于后续方向，不是当前第一版的主线交付范围。

## 能力概览

- 使用 `@AgentCapability` 在业务模块声明可被 Agent 调用的能力。
- 复用 ruoyi-vue-pro 已有用户、租户、角色、菜单按钮权限和 OAuth2 基础设施。
- MCP `tools/list` 按当前登录用户权限过滤，只展示用户可调用的能力。
- MCP `tools/call` 走 ACF 执行链，包含权限校验、幂等控制、调用审计、确认 challenge 等机制。
- 支持 OAuth 2.1 风格的 Authorization Code + PKCE 认证流程。
- 支持 Dynamic Client Registration，避免开源默认写死固定 `client_id`。
- 支持 `resources/list`、`resources/templates/list` 空列表兼容，便于通用 MCP 客户端连接。
- 默认不对 ACF 输出做脱敏，保持与 Web 端权限语义一致；如目标公司需要，可按能力或字段显式开启脱敏。

## 效果截图

WorkBuddy 通过 MCP 调用 `bujidao-ai` 的 ERP 示例能力，先完成客户、商品和库存的前置核验：

![WorkBuddy MCP 前置核验](assets/readme/workbuddy-mcp-precheck.jpg)

指定客户后，继续创建未审核的销售订单草稿：

![WorkBuddy MCP 创建销售订单草稿](assets/readme/workbuddy-mcp-order-created.jpg)

## 项目结构

- `backend/`: Java 后端工程，基于 ruoyi-vue-pro 后端。
- `backend/yudao-framework/yudao-spring-boot-starter-acf`: ACF 核心 starter。
- `backend/yudao-framework/yudao-spring-boot-starter-mcp`: MCP Server starter。
- `backend/yudao-module-acf`: ACF 管理与生产适配模块。
- `backend/yudao-module-mcp`: MCP OAuth 与应用层适配模块。
- `backend/yudao-module-erp/src/main/java/cn/iocoder/yudao/module/erp/capability`: ERP 示例能力。
- `frontend/`: Vue3 管理后台前端，基于 yudao-ui-admin-vue3。
- `frontend/src/views/acf`: ACF 管理页面。
- `frontend/src/views/mcp/sso`: MCP OAuth 授权页。
- `sql/`: `bujidao-ai` 自己维护的正式数据库增量脚本。

## 快速开始

后端要求 Java 17。常用本地打包启动方式：

```bash
cd backend
mvn package -DskipTests -pl yudao-server -am
java -jar yudao-server/target/yudao-server.jar --spring.profiles.active=local
```

前端要求 Node.js 和 pnpm 版本以 `frontend/package.json` 为准：

```bash
cd frontend
pnpm install
pnpm dev
```

本地运行前需要准备 MySQL、Redis、上游 ruoyi-vue-pro 基线数据，以及本项目 `sql/` 中声明的增量 SQL。数据库、Redis、域名、OAuth、MCP allowed hosts / origins 等配置应按部署环境自行填写。

## 已有项目接入

如果目标公司已经有自己的 ruoyi-vue-pro / 芋道源码项目，不建议直接覆盖整个仓库。推荐按增量方式接入：

1. 引入 ACF / MCP starter 和模块。
2. 执行 `sql/README.md` 中声明的正式增量 SQL。
3. 在目标业务模块中新增自己的 `capability` 包，通过 `@AgentCapability` 暴露业务能力。
4. 复用目标项目原本 Web 端的菜单按钮权限，控制 MCP tools 可见性和调用权限。
5. 按目标域名配置 MCP OAuth、Dynamic Client Registration、allowed hosts / origins。
6. 使用 Codex、WorkBuddy 或其他 MCP 客户端完成 OAuth 授权和工具调用验证。

详细接入步骤见：

- `CODING_AGENT_MCP_ACF_ADOPTION_GUIDE.md`：面向 Coding Agent 的完整接入执行手册。
- `sql/README.md`：正式增量 SQL 说明。
- `sql/mysql/20260730_bujidao_ai_acf_mcp.sql`：当前第一版 MySQL 增量 SQL。

ERP 能力只是本项目的示例业务能力。如果目标公司没有使用 ERP 模块，不需要迁移 ERP 示例能力，也不需要导入 ERP 权限数据，应在自己的业务模块中按实际场景新增能力。

## 生产部署提醒

- 生产环境必须配置真实域名的 `allowed-hosts`、`allowed-origins`、OAuth issuer 和 MCP endpoint。
- MCP 正式使用应走 OAuth 授权，不建议长期使用手动复制 Bearer Token 的方式。
- 不要绕过上游权限系统把所有能力暴露给所有登录用户。
- ACF 管理菜单 SQL 不会自动给任何角色授权，部署方需要在后台给合适角色分配权限。
- Dynamic Client Registration 会写入上游已有 `system_oauth2_client` 表；部署方应按企业安全要求限制 redirect URI 和客户端来源。
- 高风险写操作建议结合幂等键、确认 challenge 和调用审计使用；普通 MCP 客户端无法展示业务确认 UI 时，可按场景关闭 challenge。

## 上游关系和致谢

本项目基于以下开源项目持续同步：

- 后端: [YunaiV/ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro) `master-jdk17`
- 前端: [yudaocode/yudao-ui-admin-vue3](https://github.com/yudaocode/yudao-ui-admin-vue3) `master`

感谢芋道源码提供稳定、完整的开源基础。本项目会尽量保持与上游架构兼容，并在 AI 能力方向做增量扩展。

## License

本项目采用 [MIT License](LICENSE)。

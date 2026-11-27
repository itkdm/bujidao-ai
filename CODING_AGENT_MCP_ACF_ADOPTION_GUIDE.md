# Coding Agent MCP / ACF 接入执行手册

本文档面向 Coding Agent。目标是在其他公司已有的 ruoyi-vue-pro / 芋道源码项目基础上，增量引入 `bujidao-ai` 的 ACF、Remote MCP、OAuth 认证、管理页面、增量 SQL 和业务能力扩展。

执行时不要机械复制全部项目。先判断目标项目现状，再选择最小可用的增量接入方案。遇到关键信息不确定时，必须向用户确认。

## 总目标

目标项目完成后应具备：

1. 后端存在 ACF 能力声明、注册、执行、权限校验、幂等控制、确认 challenge、调用日志能力。
2. 后端存在 Remote MCP Server（MCP Java SDK 2.2.0），同一 `/mcp` 端点同时服务两个协议版本：modern（新规范 2026-07-28，`server/discover`、`tools/list`、`tools/call`，没有 `initialize` 握手，客户端先调 `server/discover` 拿协议版本与能力声明，再直接调工具）与 legacy（2025-11-25 stateless，有 `initialize` 握手，`tools/list`、`tools/call`；无 session、无 GET SSE 流）。`resources/list`、`resources/templates/list`、`prompts/*` 未注册（modern 腿未注册的方法返回 METHOD_NOT_FOUND；legacy 腿的 `resources/list` 返回空列表，`prompts/*` 同样未注册）。两个版本的分流规则见下面“双协议分流”一节。
3. MCP 认证复用 ruoyi-vue-pro 上游已有 OAuth2、用户、租户、权限体系，不要求目标公司二开 `yudao-module-system` OAuth 基础代码。
4. 前端存在 MCP 授权页和 ACF 管理页。若目标前端不是 Vue3 版本，则按同等接口和页面语义在目标前端栈中实现。
5. 数据库只执行公开仓库 `sql/` 目录声明的正式增量 SQL。
6. 业务模块按用户需求新增 ACF 能力，并通过当前登录用户 Web 端权限控制 tools 可见性和调用权限。

## 先判断用户工作区

进入用户提供的工作区后，按顺序判断：

1. 是否存在项目：
   - 如果没有任何项目，让用户确认是否直接拉取 `https://github.com/itkdm/bujidao-ai.git`。
   - 如果用户确认，克隆本项目后按本项目 README / AGENTS 运行。
   - 如果用户已有私有项目但路径未给出，先询问项目路径，不要猜。

2. 是否是 ruoyi-vue-pro / 芋道源码系项目：
   - 后端应有 Maven 多模块结构，例如 `yudao-server`、`yudao-framework`、`yudao-module-system`、`yudao-module-infra`。
   - Java 主线优先是 JDK17 / Spring Boot 3.x。若目标是 JDK8 老版本或 Spring Boot 2.x，先说明需要适配，不要直接复制。
   - 检查 `pom.xml`、包名、启动模块、已有租户/权限/OAuth2 模块是否接近上游。

3. 目标用户想引入哪些范围：
   - 只引入后端 MCP / ACF。
   - 引入后端和管理后台前端。
   - 只先做 ACF 能力，不开放 MCP。
   - 只先做 MCP 协议，不绑定 ACF，后续由用户自定义 Tool Provider。

4. 目标前端类型：
   - `yudao-ui-admin-vue3`：可以迁移本项目 Vue3 前端文件。
   - Vue2、Vben、uniapp 或其他管理端：不要直接复制 Vue3 页面；按接口语义实现等价页面。
   - 如果没有前端：MCP OAuth 浏览器授权无法完整闭环，必须询问用户是补一个最小授权页、接入已有门户，还是先完成后端能力与临时 Bearer Token 验证。

## 不要做的事

1. 不要从公开仓库正式 SQL 入口之外寻找或拼凑初始化 SQL；以 `sql/README.md` 声明的脚本为准。
2. 不要写死固定 `client_id`、固定 redirect URI 或开发机地址。
3. 不要为了 MCP 去大改 `yudao-module-system` 的 OAuth2 核心表结构和基础服务。
4. 不要绕过上游权限系统把所有 ACF 工具暴露给所有已登录用户。
5. 不要默认开启高风险 challenge 给 Codex、WorkBuddy 这类普通 MCP 客户端；这类客户端通常无法展示业务确认 UI。
6. 不要把示例 ERP 能力当成目标公司的业务需求。它只作为写法参考。

## 本项目可迁移清单

### 后端必需

复制或合并以下目录：

1. `backend/yudao-framework/yudao-spring-boot-starter-acf`
2. `backend/yudao-framework/yudao-spring-boot-starter-mcp`
3. `backend/yudao-module-acf`
4. `backend/yudao-module-mcp`

这些目录职责如下：

- `yudao-spring-boot-starter-acf`：ACF 核心，包含 `@AgentCapability`、能力注册、能力执行、权限契约、协议 schema、幂等与确认控制抽象。
- `yudao-spring-boot-starter-mcp`：通用 MCP Server starter，提供 MCP endpoint、安全过滤、协议适配、可插拔 Tool Provider，并内置可选 ACF Tool Provider。
- `yudao-module-acf`：ACF 官方生产适配模块，提供能力目录、调用日志、确认 challenge 持久化和最小后台 API。
- `yudao-module-mcp`：MCP 应用层 OAuth 适配模块，复用上游 `system_oauth2_client`、授权码、access token、refresh token、用户、租户和权限基础设施，补充 MCP OAuth / CIMD / PKCE / resource 校验。

### 后端按需

1. `backend/yudao-module-erp/src/main/java/cn/iocoder/yudao/module/erp/capability`
   - 只作为示例迁移。
   - 如果目标公司用 ERP 模块并希望先有一批典型工具，可参考复制后再按实际业务裁剪。
   - 如果目标公司业务模块不同，不要复制 ERP 能力；应在对应业务模块新增自己的 `capability` 包。

2. 业务模块 POM 中的 ACF starter 依赖。
   - 只有声明 `@AgentCapability` 的业务模块才需要依赖 `yudao-spring-boot-starter-acf`。

### 前端 Vue3 可迁移

如果目标前端是 `yudao-ui-admin-vue3`，迁移以下文件：

1. ACF 管理页面：
   - `frontend/src/api/acf/capability/index.ts`
   - `frontend/src/api/acf/invocationLog/index.ts`
   - `frontend/src/views/acf/capability/index.vue`
   - `frontend/src/views/acf/invocation-log/index.vue`

2. MCP OAuth 授权页面：
   - `frontend/src/api/login/mcpOAuth/index.ts`
   - `frontend/src/views/mcp/sso/index.vue`
   - 在 `frontend/src/router/modules/remaining.ts` 增加隐藏路由 `/mcp/sso`，组件名 `McpSSOLogin`。

3. 登录后回跳行为：
   - 检查目标前端登录成功后对 `redirect` 的处理。
   - 如果 OAuth / SSO 重定向 URL 包含完整 query，例如 `response_type`、`client_id`、`resource`、`code_challenge`，登录成功后必须使用 `window.location.href = redirect` 保留完整 URL。
   - 不要把完整 URL 当作普通路由对象拆开 push，否则 query 参数可能被破坏，MCP 授权页会出现“参数不完整”。

如果目标前端不是 Vue3：

- 复用接口语义，不复用 Vue3 文件。
- 必须实现 `/mcp/sso` 等价页面：读取 URL query，调用 `/admin-api/mcp/oauth2/authorize?clientId=...` 初始化，展示 scope，提交授权或拒绝，拿到后端返回的 redirect URL 后跳转。
- 必须提供 ACF 能力目录和调用日志页面，或让用户明确接受第一版没有后台控制面。

### SQL

只使用根目录正式增量 SQL：

- `sql/mysql/20260730_bujidao_ai_acf_mcp.sql`

这份 SQL 包含：

- `acf_capability_definition`
- `acf_invocation_log`
- `acf_confirmation_challenge`
- ACF 管理后台菜单和按钮权限

SQL 菜单插入不使用固定 ID，会根据 `/acf` 和权限标识动态查找或创建菜单。

正式 MCP OAuth 默认使用 CIMD（Client ID Metadata Document）。接入时不需要预置固定 OAuth client，也不向服务端注册客户端；Agent 客户端以 client_id URL 发布一份元数据文档，服务端拉取并校验后合成临时客户端（不写入上游已有 `system_oauth2_client` 表）。

最小元数据文档示例（由客户端托管在 client_id URL 上，须为 HTTPS；`allowPrivateAddresses=true` 时放行 http 的 client_id URL，仅本地开发 / 测试用，生产保持 false；文档内 `client_id` 必须与托管 URL 逐字节一致）：

```json
{
  "client_id": "https://client.example.com/mcp-client",
  "client_name": "Example MCP Client",
  "redirect_uris": ["https://client.example.com/callback"],
  "grant_types": ["authorization_code", "refresh_token"],
  "response_types": ["code"],
  "scope": "mcp:access",
  "token_endpoint_auth_method": "none"
}
```

其中 `client_id`、`client_name`、`redirect_uris` 必填；`grant_types` 须包含 `authorization_code`（可再加 `refresh_token`）；`token_endpoint_auth_method` 只支持 `none`，缺省时也默认为 `none`；`scope` 不填时默认 `mcp:access`。`redirect_uris` 须为 HTTPS（http 仅放行 loopback 回调：localhost、整个 127.0.0.0/8、`::1`，以及 `localRedirectHosts` 配置里显式允许的 host），自定义 URI scheme 的回调不放行。

## Maven 接入步骤

根据目标项目结构调整，不要盲目覆盖 POM。

1. 根后端 `pom.xml`
   - 增加模块：
     - `yudao-module-acf`
     - `yudao-module-mcp`
   - 如果目标根 POM 管理 `yudao-framework` 子模块，确认 framework 子模块会包含两个 starter。

2. `backend/yudao-framework/pom.xml`
   - 增加模块：
     - `yudao-spring-boot-starter-acf`
     - `yudao-spring-boot-starter-mcp`

3. `backend/yudao-dependencies/pom.xml`
   - 增加版本属性：
     - `mcp-java-sdk.version`，当前本项目使用 `2.2.0`。
   - dependencyManagement 增加：
     - `cn.iocoder.boot:yudao-spring-boot-starter-acf`
     - `cn.iocoder.boot:yudao-spring-boot-starter-mcp`
     - `cn.iocoder.boot:yudao-module-acf`
     - `cn.iocoder.boot:yudao-module-mcp`
     - `io.modelcontextprotocol.sdk:mcp-core`
     - `io.modelcontextprotocol.sdk:mcp-json-jackson2`

4. `backend/yudao-server/pom.xml`
   - 增加依赖：
     - `yudao-module-acf`
     - `yudao-module-mcp`
   - 如果目标项目要直接暴露某个业务模块能力，确保该业务模块本身已被 `yudao-server` 引入。

5. 业务模块 POM
   - 在需要声明 ACF 能力的业务模块中增加：
     - `yudao-spring-boot-starter-acf`
   - 示例：本项目 ERP 模块通过该依赖声明 `erp.*` 能力。

## YAML 配置接入

不要复制源项目中的环境私有配置，例如数据库、Redis、API Key、个人域名或开发机地址。只迁移 ACF/MCP 相关配置项，并按目标公司环境填写。

后端配置建议：

```yaml
yudao:
  mcp:
    server:
      enabled: true
      endpoint: /mcp
      name: ${YOUR_MCP_SERVER_NAME}
      version: 1.0.0
      instructions: ${YOUR_MCP_SERVER_INSTRUCTIONS}
      required-scopes:
        - mcp:access
      # 生产部署必须按实际域名配置，默认只允许 localhost / 127.0.0.1
      allowed-origins:
        - https://${YOUR_ADMIN_DOMAIN}
      allowed-hosts:
        - ${YOUR_API_DOMAIN}
      # MRTR 确认的 requestState 签名密钥：多实例部署必须配同一个值（标准 Base64，
      # 解码后至少 32 字节），否则 A 实例发出的确认在 B 实例打不开；单实例可不配
      # （每进程随机生成，启动日志有警告）。密钥只能来自环境变量或密钥管理服务，
      # 绝不要把真实值写进配置文件；轮换后旧 state 立即失效（进行中的确认需重调一次工具）。
      # request-state:
      #   shared-key: ${MCP_REQUEST_STATE_SHARED_KEY}
      # Legacy MCP 2025-11-25 兼容开关：默认开启（不配即 true），同一 /mcp 端点
      # 同时服务新旧两个协议版本；改成 false 后旧客户端的 legacy 请求一律被拒绝
      # （HTTP 400 + JSON-RPC -32022），modern 客户端不受影响。见“双协议分流”一节。
      # legacy:
      #   enabled: true
      # 如果后端在反向代理后无法正确推导外部地址，显式配置下面几项
      public-resource-uri: https://${YOUR_API_DOMAIN}/mcp
      authorization-server-issuer: https://${YOUR_API_DOMAIN}
      authorization-endpoint: https://${YOUR_ADMIN_DOMAIN}/mcp/sso
      token-endpoint: https://${YOUR_API_DOMAIN}/admin-api/mcp/oauth2/token
      revocation-endpoint: https://${YOUR_API_DOMAIN}/admin-api/mcp/oauth2/revoke
    acf:
      enabled: true
    oauth:
      enabled: true
      default-scopes:
        - mcp:access
      access-token-validity-seconds: 1800
      refresh-token-validity-seconds: 2592000
      cimd:
        fetch-timeout: 3s
        max-document-bytes: 5120
        cache-max-age: 24h
        cache-default-ttl: 1h
        cache-max-entries: 1000
        # 保持 false：改成 true 会同时放行 http scheme 的 client_id 与内网 / 回环地址，
        # 只允许本地开发 / 测试临时开启，生产禁止改 true
        allow-private-addresses: false
      rate-limit:
        enabled: true
        cimd-per-host-per-minute: 60
        cimd-global-per-minute: 600
        cimd-max-concurrent-fetches: 20
        authorize-per-ip-per-minute: 60
        token-per-ip-per-minute: 60
```

限流阈值口径：以上阈值均按 60 秒滑动窗口计，进程内计数，多实例部署下各实例各自限流、非全局共享；超限返回 429（OAuth 错误响应为 `temporarily_unavailable`，带 `Retry-After: 60`）。

还要确认上游已有配置：

- `yudao.web.admin-ui.url` 必须指向真实管理前端地址，否则 OAuth 授权入口会跳错。
- `yudao.web.admin-api.prefix` 如果目标项目改过，MCP OAuth endpoint 推导也要跟着确认。
- 反向代理场景必须保证 `Host`、`X-Forwarded-Proto`、`X-Forwarded-Host` 等头正确，或显式配置上面的 endpoint。

开发环境可以适度放宽，生产部署必须收紧：

- `allowed-hosts`
- `allowed-origins`
- `cimd.allow-private-addresses`（仅本地开发 / 测试临时开启，生产保持 false）
- redirect URI 的 https 要求（CIMD 客户端不放行自定义 scheme；http 仅放行 localhost / 127.0.0.1 回调）

## 双协议分流（modern 2026-07-28 + legacy 2025-11-25）

同一 `/mcp` 端点同时服务两个协议版本，分流器 `McpProtocolDispatcherServlet` 按请求特征路由（命中即分流，先后顺序如下）：

1. GET / DELETE 一律 legacy（legacy 的 GET 答 405，modern 的 GET/DELETE 同样 405；两条腿都不建 session）。
2. POST 带 `Mcp-Method` 头的一律 modern（这是 2026-07-28 的必备头）。
3. `MCP-Protocol-Version` 为 `2026-07-28` 的 modern，其他值 legacy（仅凭该头存在不能分流，2025-11-25 也要求该头，判的是值）。
4. 请求体 `_meta` 的协议版本（或顶层 `params.protocolVersion`）为 `2026-07-28` 的 modern；方法为 `server/discover` 的 modern。
5. 方法为 `initialize`（且无 modern 信号）的 legacy；其余 POST 默认 legacy（老客户端兼容优先，modern 客户端一定带 `Mcp-Method` 头，不会落到这里）。

两条腿的区别：

- modern（2026-07-28）：无 `initialize` 握手，先调 `server/discover`，再直接调工具；确认走 MRTR elicitation（见下）。
- legacy（2025-11-25）：stateless，有 `initialize` 握手；无 session、无 GET SSE 流（legacy 的 GET 直接 405）；`tools/list` 按当前用户权限过滤（与 modern 同一份可见性规则），`tools/call` 再做一次权限校验。

客户端走哪条腿：

- 新客户端（支持 2026-07-28，如 Codex）走 modern。
- 旧客户端（只支持 2025-11-25，如 WorkBuddy）走 legacy：发 `initialize` 握手，不带 `Mcp-Method` 头。

认证与元数据两条腿共用同一套：OAuth（CIMD / PKCE / Bearer token + `mcp:access` scope）、租户、限流、安全过滤器都在分流之前跑完；OAuth 元数据端点（`/.well-known/oauth-protected-resource/mcp`、`/.well-known/oauth-authorization-server`）不区分协议版本，不用为 legacy 另做说明或另配——它们描述的是 OAuth 资源与授权服务器，不是 MCP 协议版本。

Legacy 开关：`yudao.mcp.server.legacy.enabled`，默认 `true`（不配即开启）。改成 `false` 后，分流到 legacy 的请求在分流器里被明确拒绝，不会静默落到 modern，也不会 500：JSON-RPC 请求答 HTTP 400 + JSON-RPC 错误信封（`code -32022`，`id` 原样回显，`message` 说明 legacy 已关闭、请用 modern 客户端）；通知（无 `id`）按 JSON-RPC 语义无响应，答 HTTP 202；modern 请求完全不受影响。关掉开关只是关分流入口，legacy transport 与 stateless server 的 bean 仍会建出来（不产生 URL 映射、不被调用）。

何时下掉 legacy：每个请求分流器记一行日志（`MCP dispatch leg={} protocolVersion={} ... rule={}`），以后按该日志统计 modern 与 legacy 的流量：legacy 流量归零一段时间后再考虑关开关、最终下掉 legacy 代码；开关关闭后的拒绝也记为 `rule=legacy-disabled`，便于统计还有多少旧客户端在撞墙。

## OAuth 复用边界

MCP OAuth 正式方案复用上游系统能力：

- `system_oauth2_client`
- OAuth 授权码
- access token / refresh token
- 用户登录态
- 租户上下文
- 权限标识和角色菜单

新增的 `yudao-module-mcp` 是适配层：

- 提供 `/mcp/oauth2/authorize`
- 提供 `/mcp/oauth2/token`
- 提供 `/mcp/oauth2/revoke`
- 保存 MCP 授权请求和授权码扩展信息到 Redis
- 对 CIMD、PKCE、resource indicator、redirect URI 策略做 MCP 需要的校验

除非目标项目上游版本缺少必要 OAuth API，否则不要修改 `yudao-module-system`。如果确实缺少 API，先判断是升级上游、复制最小缺失 API，还是做适配接口，不要直接重构系统 OAuth 模块。

## ACF 业务能力添加流程

完成公共模块接入后，再向用户确认业务能力需求：

1. 询问用户要让 Agent 操作哪些业务域。
2. 区分只读能力和写操作能力。
3. 对每个能力确认：
   - 能力名称，例如 `crm.customer.search`
   - 标题和描述
   - 入参 DTO
   - 输出 DTO
   - 复用哪个现有 Service
   - 复用哪个 Web 端权限标识
   - 是否有副作用
   - 是否需要幂等键
   - 是否需要确认 challenge
   - 是否包含敏感字段，默认是否与 Web 端一致

推荐代码组织：

- 在业务模块下新增 `capability` 包。
- DTO 放到 `capability/dto`。
- provider 命名为 `{Domain}CapabilityProvider`。
- 方法上使用 `@AgentCapability`。
- 不要把 Controller 直接暴露为工具，不要绕过 Service 业务校验。

权限原则：

- ACF tools/list 只展示当前用户有权限调用的能力。
- ACF tools/call 再做一次权限校验。
- 权限标识复用现有 Web 端权限；本项目 ERP 示例里是 `erp:product:query` 这类标识，目标公司应换成自己业务模块已有的权限标识。
- 初版不要新增“租户套餐能力绑定表”这类额外可见性系统，除非用户明确有 SaaS 套餐需求。

写操作原则：

- 写操作或高风险能力必须支持幂等键。
- MCP 普通客户端通常无法展示业务确认 UI，因此 challenge 默认关闭更合理。
- 如果用户自建 Agent 系统并能展示确认 UI，再开启 challenge。

输出原则：

- MCP 边界输出应是稳定 DTO，不直接暴露 DO。
- `CapabilityResult` 返回值在 MCP `structuredContent` 中应展开为 `result.data`。
- `LocalDateTime` 等 Java 时间类型优先在协议 DTO 中输出 ISO 字符串，避免 JSON schema / mapper 兼容问题。

## 验证流程

### 后端构建

在目标后端根目录执行：

```bash
mvn clean package -DskipTests
```

如果只验证局部模块，必须带 `-am`：

```bash
mvn package -DskipTests -pl yudao-server -am
```

### 数据库

1. 确认目标库已具备上游基础表。
2. 执行 `sql/mysql/20260730_bujidao_ai_acf_mcp.sql`。
3. 检查三张 ACF 表存在。
4. 检查菜单中有 ACF 管理入口和按钮权限。
5. 给测试用户角色分配 ACF 菜单权限和对应业务权限。

### 后端启动

启动后检查：

1. `/admin-api/system/auth/captcha-image` 或目标项目健康接口正常。
2. `/.well-known/oauth-protected-resource/mcp` 可访问。
3. `/.well-known/oauth-authorization-server` 或 MCP metadata 中的 endpoint 指向正确域名。
4. `/mcp` 未带 token 返回 401，并带 `WWW-Authenticate` metadata。
5. 带无 scope token 返回 403。
6. 带 `mcp:access` token 可以调 `server/discover` 并看到工具列表（modern 腿，无 initialize 握手）；旧客户端也可以调 `initialize` 走 legacy 腿握手（见“双协议分流”一节）。

### 前端验证

1. 登录管理后台。
2. 能打开 ACF 能力目录和调用日志页面。
3. 能访问 `/mcp/sso?...` 授权页。
4. 如果未登录，先跳登录；登录后必须回到原始 `/mcp/sso` URL，query 参数不丢。
5. 同意授权后跳回 MCP 客户端 callback。

### MCP 客户端验证

用 Codex、WorkBuddy 或其他 Remote MCP 客户端配置：

- MCP URL：`https://${YOUR_API_DOMAIN}/mcp`
- 认证方式：OAuth / CIMD（client_id URL + 元数据文档）
- 不手动填写固定 token 作为生产方案

测试：

1. 点击认证后应打开浏览器。
2. 未登录时先登录，再进入 MCP 授权页。
3. 授权后客户端显示连接成功。
4. tools/list 只展示当前用户有权限的工具。
5. 只读工具可正常返回数据。
6. 写工具在缺少幂等键时应给出明确提示；客户端支持后应带幂等键调用。
7. ACF 调用日志能记录成功和失败。

需要确认的能力走 MRTR 标准确认，**只在 modern 腿（新规范 2026-07-28）可用**，不再有独立的确认工具：首轮调用返回 `input_required`，用表单 elicitation 请用户确认；用户接受后客户端带 `inputResponses` 重试，服务端换 token 再执行；拒绝后 challenge 作废，需重新调用。客户端必须声明 elicitation（form）能力才能完成确认，不支持的客户端会收到 `CONFIRMATION_UNAVAILABLE`；确认总开关默认关闭，未开启时能力直接执行或按旧逻辑返回确认错误。**生产目前没有任何确认启用的能力**，以上只是防御性说明。

Legacy 客户端（2025-11-25，如 WorkBuddy）调需要确认的能力时，收到的不是确认表单、也不是静默执行，而是明确的错误：`CONFIRMATION_UNAVAILABLE` 错误结果（`isError=true`，正文说明 legacy 腿无法完成确认、请用 modern 客户端确认后重试）。不要在 legacy 侧找确认工具——`acf.confirm` 工具已删除，不会在 legacy 的 `tools/list` 里出现；legacy 腿的 `InputRequiredResult` 也会被转成同样的 `isError` 错误结果，能力本身不会执行。

## 部署提醒

生产部署时必须让用户确认：

1. MCP 对外地址：`https://${YOUR_API_DOMAIN}/mcp`
2. 管理前端地址：`https://${YOUR_ADMIN_DOMAIN}`
3. OAuth 授权页地址：`https://${YOUR_ADMIN_DOMAIN}/mcp/sso`
4. token endpoint：`https://${YOUR_API_DOMAIN}/admin-api/mcp/oauth2/token`
5. HTTPS 证书和反向代理头是否正确。
6. `allowed-hosts`、`allowed-origins`、redirect URI 策略是否按生产域名收紧。
7. access token 和 refresh token 生命周期是否符合企业安全要求。
8. 用户角色是否已经分配 ACF 管理权限和业务工具权限。
9. 是否需要审计日志保留周期、日志脱敏或导出策略。

## 如果目标项目不是完全匹配

### 后端版本偏旧

如果目标是 JDK8 / Spring Boot 2.x：

- 不要直接复制。
- 先评估 Jakarta / javax、Spring Security、MyBatis Plus、Servlet API 差异。
- 优先建议用户升级到 JDK17 分支或接受一次兼容适配任务。

### 前端不是 Vue3

只迁移接口契约和页面行为：

- ACF 能力目录接口：`/admin-api/acf/capability/page`
- ACF 能力详情接口：`/admin-api/acf/capability/get`
- ACF 同步能力接口：`/admin-api/acf/capability/sync`
- ACF 调用日志接口：`/admin-api/acf/invocation-log/page`
- MCP 授权初始化接口：`/admin-api/mcp/oauth2/authorize?clientId=...`
- MCP 授权提交接口：`/admin-api/mcp/oauth2/authorize`

按目标前端框架实现即可。

### 只要后端不做前端

必须提醒用户：

- Remote MCP OAuth 需要浏览器授权页。
- 没有前端页面时，只能先做临时 Bearer Token 验证或实现一个最小授权页。
- 生产不能长期依赖手动复制短期 access token。

## 完成标准

在目标公司项目中，只有同时满足以下条件才算完成：

1. 公共模块、POM、配置、SQL 已接入。
2. 后端可打包启动。
3. ACF 菜单和 API 可访问。
4. MCP OAuth 可通过真实客户端完成端到端认证。
5. tools/list 按当前用户权限过滤。
6. tools/call 按当前用户权限校验。
7. 至少一个业务只读能力通过真实数据验证。
8. 至少一个写操作能力验证幂等键行为。
9. 部署文档中写明 MCP URL、授权 URL、域名、反向代理、scope、token 生命周期和权限分配。
10. 未引入开发机凭据、固定测试 client、临时 SQL 或绕过权限的临时代码。

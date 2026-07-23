# AGENTS.md

## 目的

本文件用于给 AI 编程 Agent 提供在 `bujidao-ai` 仓库中工作的项目上下文、工程约束和安全规则。

`README.md` 面向普通用户和开源访问者，保持简洁；`AGENTS.md` 面向 AI 编程 Agent，记录构建命令、目录边界、架构方向、验证方式和协作规范。

## 项目概览

`bujidao-ai` 是基于芋道源码 / ruoyi-vue-pro 的 AI 扩展项目。

本项目不是把芋道源码改名后重新发布，也不是从零实现一套新的后台框架。项目目标是在兼容芋道企业级后台基础能力的前提下，逐步补充面向真实业务场景的 AI 能力，例如 RAG、Agent 运行时、MCP 集成、ACF 风格的能力治理、工具调用、执行追踪和 AI 管理后台。

## 目录说明

- `backend/`: Java 后端工程，通过 `git subtree` 从 `YunaiV/ruoyi-vue-pro` 引入。
- `frontend/`: Vue 管理后台前端，通过 `git subtree` 从 `yudaocode/yudao-ui-admin-vue3` 引入。
- `docs/`: `bujidao-ai` 自己的项目文档、设计说明和上游同步说明。
- `sql/`: `bujidao-ai` 自己维护的增量 SQL、迁移说明和补充数据。

根目录的本文件适用于整个仓库。未来如果某个子目录新增了自己的 `AGENTS.md`，在该子目录内工作时应优先遵循更近的说明文件。

## 上游关系

本项目关联的上游仓库：

- 后端：`https://github.com/YunaiV/ruoyi-vue-pro.git`，主线同步 `master-jdk17`
- 前端：`https://github.com/yudaocode/yudao-ui-admin-vue3.git`，主线同步 `master`

本地远程通常应保持为：

```bash
origin             git@github.com:itkdm/bujidao-ai.git
upstream-backend   https://github.com/YunaiV/ruoyi-vue-pro.git
upstream-frontend  https://github.com/yudaocode/yudao-ui-admin-vue3.git
```

`upstream-*` 远程只用于拉取上游代码，禁止向上游远程推送。

本仓库使用 `git subtree` 管理上游前后端代码，不使用 Git submodule。除非用户明确决定调整仓库策略，否则不要把 subtree 改成 submodule。

当前后端基线是 `master-jdk17`，不是 JDK8 版本的 `master`。除非用户重新做出架构决策，否则不要把后端切回 `master`。

`master-jdk25` / Spring Boot 4.x 可作为未来实验方向评估，不作为当前主线默认同步分支。

上游同步命令和 GitHub 网络不稳定时的浅拉取方案见 `docs/upstream-sync.md`。

## 架构方向

优先采用“增量 AI 扩展”的方式，尽量保持与芋道上游架构兼容。

新增后端 AI 能力时：

- 优先放在 `backend/yudao-module-*` 模块体系下，例如后续可新增 `yudao-module-rag`、`yudao-module-agent`。
- 对上游公共模块的改动要小、明确、可解释，并在必要时写入文档。
- 尽量沿用芋道已有的分层、命名和模块组织方式，除非有清晰的项目理由需要调整。
- Agent、RAG 等模块使用业务能力时，优先通过 ACF 风格的能力治理入口接入。
- 不要绕开能力治理，直接把任意业务 Service 方法临时注册成工具。

设计 Agent 相关能力时：

- 保持 `conversation`、`execution`、`tool`、`knowledge`、`memory`、`trace` 等概念边界清楚。
- MCP 是集成协议和互操作边界，不应成为整个架构的中心。
- RAG、Agent 运行时、业务能力治理是相关但不同的领域，不要混成一个抽象。

## 后端开发规则

后端当前沿用 ruoyi-vue-pro / 芋道结构：

- Java 版本：Java 17，以 `backend/pom.xml` 为准。
- 技术栈：Spring Boot 3.5.x。
- 构建工具：Maven。
- 主启动模块：`backend/yudao-server`。
- 公共框架代码：`backend/yudao-framework`。
- 业务模块：`backend/yudao-module-*`。

修改后端代码前：

- 先查看现有模块的实现模式，不要凭空新增一套风格。
- 新代码放到职责最接近的模块中。
- 只有存在真实复用需求，或现有架构明确需要扩展点时，才新增共享框架抽象。
- 功能开发时避免对上游代码做大范围纯风格重构。
- 不要提交生成文件、日志、构建产物和本地运行文件。

常用后端命令：

```bash
cd backend
mvn clean package -DskipTests
```

做局部模块验证时，优先使用 Maven 的 `-pl` 和 `-am` 参数缩小验证范围。

## 前端开发规则

前端当前沿用芋道 Vue 3 管理后台结构：

- Vue 3
- Vite
- TypeScript
- Element Plus
- pnpm

修改前端代码前：

- 复用已有 API、路由、Store、组件和页面组织方式。
- UI 改动应保持管理后台的工作台风格，不要做成营销页或展示页。
- 不要新增 UI 框架，除非用户明确同意。
- 不要提交 `node_modules/`、`dist/`、缓存目录或本地运行产物。

常用前端命令：

```bash
cd frontend
pnpm install
pnpm dev
pnpm lint
pnpm ts:check
pnpm build:local
```

前端要求 Node.js `>= 20.19.0`、pnpm `>= 8.6.0`，以 `frontend/package.json` 为准。

## 本地运行规则

本地启动前后端时，优先确认端口和进程来源。不要只按端口杀进程，先确认命令行确实属于当前要替换的本地项目。

```powershell
Get-NetTCPConnection -LocalPort 48080,80 -State Listen -ErrorAction SilentlyContinue
Get-CimInstance Win32_Process -Filter "ProcessId=<pid>" | Select-Object ProcessId,CommandLine
```

详细本地开发流程见 `docs/local-dev.md`。

后端默认端口是 `48080`。仓库内的 `backend/yudao-server/src/main/resources/application-local.yaml` 应保持为可提交的安全配置。真实本机配置放到根目录 `runtime-config/application-local.yaml`，该目录已被 `.gitignore` 忽略。如果为了本机运行修改了数据库、Redis、对象存储、三方应用等配置：

- 将这类修改视为本地运行改动，不要提交或推送。
- 汇报或排查时必须脱敏，不要把密码、Token、Secret、私钥原文写进对话或日志摘要。
- 结束前用 `git status --short --branch` 明确列出本地改动，尤其是包含本地凭据的配置文件。

后端本地启动常用流程：

```bash
cd backend
mvn package -DskipTests -pl yudao-server -am
```

在仓库根目录使用 PowerShell 后台启动：

```powershell
$repo = (Resolve-Path '.').Path
$logs = Join-Path $repo 'runtime-logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$java = 'D:\jdk\jdk17\jdk-17.0.12\bin\java.exe'
$jar = Join-Path $repo 'backend\yudao-server\target\yudao-server.jar'
$config = 'optional:file:' + $repo.Replace('\', '/') + '/runtime-config/'
Start-Process -WindowStyle Hidden -FilePath $java `
  -ArgumentList @('-jar',('"{0}"' -f $jar),'--spring.profiles.active=local',("--spring.config.additional-location=$config")) `
  -RedirectStandardOutput (Join-Path $logs 'yudao-server.out.log') `
  -RedirectStandardError (Join-Path $logs 'yudao-server.err.log')
```

如果本机 JDK17 路径不同，先以 `backend/pom.xml` 的 Java 版本为准调整 `$java`，不要改 Maven 项目的 Java 基线。

前端开发服务默认使用 `80`，本地请求地址优先查看 `frontend/.env.local`。前端本地启动常用流程：

```bash
cd frontend
pnpm install
pnpm dev
```

如果需要通过脚本或 Agent 后台启动并显式指定 Vite 端口，优先直接调用 Vite，避免把额外的 `--` 当作字面量参数传入：

```bash
node node_modules/vite/bin/vite.js --mode env.local --host 0.0.0.0 --port 80
```

启动后不要只看进程存在，应同时确认监听端口、启动日志和 HTTP 接口。后端启动验证可优先请求 `http://localhost:48080/admin-api/system/auth/captcha-image`，前端启动验证可优先请求 `http://localhost/`。

不要把一次性的本地环境迁移步骤写成长期规则；`AGENTS.md` 只沉淀可复用的项目约定、运行流程和安全边界。

## 文档规则

- `README.md` 保持简洁，面向用户介绍项目定位。
- Agent 工作规则写在 `AGENTS.md`。
- 详细设计、架构决策、同步说明放在 `docs/`。
- 本项目自己的数据库增量脚本和迁移说明放在 `sql/`。
- 项目文档优先使用中文，除非所在文件已经明确采用英文。

如果一次改动引入了长期有效的架构决策，应把决策写入 `docs/`，不要只留在代码注释或对话记录里。

## 安全规则

- 禁止提交真实 API Key、Token、密码、私钥、Cookie 或个人凭据。
- `.env.local`、本地日志、运行时生成文件、临时导出文件默认不应提交。
- 如果上游 SQL 或示例数据包含会触发 GitHub push protection 的云厂商示例 Key，应在推送前替换为清晰的占位符。
- 不要为了本地演示方便而削弱认证、租户隔离、权限校验、审计日志或审批流程。
- 如果改动涉及 SQL、配置文件或环境变量文件，推送前建议做一次简单的密钥形态扫描。

建议的快速扫描命令：

```bash
git grep -n -E "AKID[A-Za-z0-9]{20,}|LTAI[A-Za-z0-9]{16,}|AKLT[A-Za-z0-9]{20,}|BEGIN (RSA|OPENSSH|EC|DSA) PRIVATE KEY"
```

## Git 工作流

- 编辑前和结束前都要查看 `git status --short --branch`。
- 不要覆盖或回滚用户改动，除非用户明确要求。
- 不要提交或推送，除非用户明确要求。
- 提交应聚焦，提交信息尽量使用简洁的 conventional-style 格式。
- 对 subtree 引入的上游代码，优先保持兼容，不做单纯为了风格统一的大规模改写。

## 验证要求

根据改动范围选择验证方式：

- 修改后端 Java 或 Maven 配置：运行相关 Maven 构建或模块验证。
- 修改前端 TypeScript、Vue、样式或依赖配置：运行相关 pnpm lint、类型检查或构建命令。
- 仅修改文档：不需要构建，但应检查 Markdown 内容、链接和表达是否合理。
- 同步上游代码：如果依赖和环境可用，至少运行后端打包和前端构建。

如果因为依赖、服务、数据库或凭据缺失导致无法运行验证命令，应在最终回复中明确说明。

## Agent 工作规则

- 提出架构变更前，先阅读已有代码和文档。
- 优先遵循当前仓库约定，不轻易引入新模式。
- 除非用户要求大改，否则保持改动小而清晰。
- 如果发现架构方向冲突，要显式说明，不要静默选择新方向。
- 如果需求略有歧义，但本地上下文足以做出安全假设，可以继续执行，并在回复中说明假设。
- 如果一次改动同时涉及上游导入代码和 `bujidao-ai` 自己的扩展代码，应在实现和总结中清楚区分两类改动。

# bujidao-ai

`bujidao-ai` 是基于芋道源码 / ruoyi-vue-pro 的 AI 扩展项目。

项目目标是在成熟的企业级后台体系上，补充面向真实业务场景的 AI 能力。

本项目不是从零实现新的后台框架，而是在芋道工程基础上持续沉淀 AI 方向的扩展模块和实践方案。

## 适合谁使用

- 已经在使用或准备使用芋道源码，希望在现有后台基础上接入 AI 能力的开发者
- 想了解企业级后台如何落地 RAG、Agent、MCP等能力的团队
- 需要持续跟进上游，同时保留 AI 扩展空间的二次开发项目的用户

## 项目结构

- `backend/`: 后端工程
- `frontend/`: 管理后台前端
- `sql/`: 扩展 SQL

## 当前重点能力

- ACF 能力治理基础：通过 `@AgentCapability` 声明业务能力，复用原有权限、租户和业务 Service。
- Remote MCP Server：支持通用 MCP 客户端通过 OAuth + PKCE 授权后调用系统能力。
- ERP 示例能力：覆盖商品、库存、客户、供应商、仓库、销售/采购订单和统计等典型场景。
- 轻量 ACF 管理模块：提供能力目录、运行状态、调用审计、确认和幂等相关基础设施。

本项目不是 SaaS 服务，不提供统一的 MCP 地址。使用方应自行部署后，使用自己的域名、用户、租户、权限和 OAuth 配置完成接入。

## 上游关系和致谢

本项目基于以下开源项目持续同步：

- 后端: [YunaiV/ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro) `master-jdk17`
- 前端: [yudaocode/yudao-ui-admin-vue3](https://github.com/yudaocode/yudao-ui-admin-vue3) `master`

感谢芋道源码提供稳定、完整的开源基础。本项目会尽量保持与上游架构兼容，并在 AI 能力方向做增量扩展。

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
- `docs/`: 项目文档
- `sql/`: 扩展 SQL

## 开发文档

- [本地开发环境](docs/local-dev.md)
- [上游同步说明](docs/upstream-sync.md)

## 上游关系和致谢

本项目基于以下开源项目持续同步：

- 后端: [YunaiV/ruoyi-vue-pro](https://github.com/YunaiV/ruoyi-vue-pro) `master-jdk17`
- 前端: [yudaocode/yudao-ui-admin-vue3](https://github.com/yudaocode/yudao-ui-admin-vue3) `master`

感谢芋道源码提供稳定、完整的开源基础。本项目会尽量保持与上游架构兼容，并在 AI 能力方向做增量扩展。

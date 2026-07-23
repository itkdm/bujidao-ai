# bujidao-ai

`bujidao-ai` 是面向芋道 / ruoyi-vue-pro 生态的 AI 扩展项目。

项目会在芋道源码的前后端基础上，逐步沉淀 AI 相关能力，例如 ACF 能力接入、RAG 独立模块、Agent 模块、AI 管理后台能力等。

## 目录结构

- `backend/`: 后端工程，来源于 `YunaiV/ruoyi-vue-pro`
- `frontend/`: 管理后台前端工程，来源于 `yudaocode/yudao-ui-admin-vue3`
- `docs/`: 本项目自己的设计、同步和扩展文档
- `sql/`: 本项目自己的增量 SQL、迁移脚本和说明

## Git 远程

- `origin`: 本项目自己的 GitHub 仓库，`git@github.com:itkdm/bujidao-ai.git`
- `upstream-backend`: 芋道后端上游，`https://github.com/YunaiV/ruoyi-vue-pro.git`
- `upstream-frontend`: 芋道前端上游，`https://github.com/yudaocode/yudao-ui-admin-vue3.git`

## 同步策略

本仓库使用 `git subtree` 引入芋道前后端代码。

这样可以把前后端代码放在同一个开源仓库中，同时保留后续定期同步上游的能力。相比 submodule，这种方式对普通使用者更友好，克隆本仓库后不需要额外初始化子模块。

具体同步命令见 [docs/upstream-sync.md](docs/upstream-sync.md)。

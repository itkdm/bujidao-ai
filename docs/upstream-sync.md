# 上游同步说明

本项目以 `git subtree` 的方式同步芋道前后端上游。

## 当前远程

```bash
git remote -v
```

预期包含：

```text
origin             git@github.com:itkdm/bujidao-ai.git
upstream-backend   https://github.com/YunaiV/ruoyi-vue-pro.git
upstream-frontend  https://github.com/yudaocode/yudao-ui-admin-vue3.git
```

`upstream-*` 只用于拉取，不向上游推送。

当前项目主线同步的上游分支：

- 后端：`upstream-backend/master-jdk17`
- 前端：`upstream-frontend/master`

后端不使用 JDK8 版本的 `master` 作为主线。`master-jdk25` 可作为未来实验方向评估，不作为当前默认同步分支。

## 首次导入

如果网络稳定，可以直接执行：

```bash
git subtree add --prefix=backend upstream-backend master-jdk17 --squash
git subtree add --prefix=frontend upstream-frontend master --squash
```

如果拉取 GitHub 大仓库时遇到 `RPC failed`、`early EOF` 等网络中断，可以先浅拉取最新分支，再从本地远程跟踪分支导入：

```bash
git -c http.version=HTTP/1.1 fetch --depth=1 upstream-backend master-jdk17
git subtree add --prefix=backend upstream-backend/master-jdk17 --squash

git -c http.version=HTTP/1.1 fetch --depth=1 upstream-frontend master
git subtree add --prefix=frontend upstream-frontend/master --squash
```

## 月度同步

同步前先确认本地工作区干净：

```bash
git status --short
```

拉取后端上游：

```bash
git -c http.version=HTTP/1.1 fetch --depth=1 upstream-backend master-jdk17
git subtree pull --prefix=backend upstream-backend/master-jdk17 --squash
```

拉取前端上游：

```bash
git -c http.version=HTTP/1.1 fetch --depth=1 upstream-frontend master
git subtree pull --prefix=frontend upstream-frontend/master --squash
```

同步后建议至少执行：

```bash
cd backend
mvn clean package -DskipTests

cd ../frontend
pnpm install
pnpm build:local
```

确认无误后推送到自己的远程：

```bash
git push origin main
```

## 扩展建议

- AI 后端模块优先放在 `backend/yudao-module-*` 体系下，例如后续可新增 `yudao-module-rag`、`yudao-module-agent`。
- ACF 能力应继续作为业务能力治理层，Agent、RAG 等模块通过 ACF 使用业务能力，不绕开 ACF 直接绑定业务服务方法。
- 本项目自己的说明、设计决策和同步记录放到 `docs/`，避免散落在上游目录中。
- 本项目自己的数据库增量脚本放到 `sql/`，并在文档中标注适用版本。

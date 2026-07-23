# 本地开发环境

本文记录 `bujidao-ai` 本地运行前后端的常用流程。

## 环境要求

- JDK 17，以 `backend/pom.xml` 为准。
- Maven，使用本机已配置的 Maven 环境即可。
- Node.js `>= 20.19.0`，以 `frontend/package.json` 为准。
- pnpm `>= 8.6.0`。
- 本地 MySQL 和 Redis。

## 本地配置

仓库内的 `backend/yudao-server/src/main/resources/application-local.yaml` 应保持为可提交的安全配置，不要写入真实密码、Token、Secret 或个人凭据。

真实本机配置放到根目录的 `runtime-config/application-local.yaml`。该目录已被 `.gitignore` 忽略，不会进入提交。

首次准备本地配置时，可以从仓库配置复制一份：

```powershell
New-Item -ItemType Directory -Force runtime-config
Copy-Item backend/yudao-server/src/main/resources/application-local.yaml runtime-config/application-local.yaml
```

然后只修改 `runtime-config/application-local.yaml` 中的本地数据库、Redis、对象存储、三方应用等连接项。

确认本地配置不会被 Git 跟踪：

```powershell
git check-ignore -v -- runtime-config/application-local.yaml
git status --short --branch
```

## 启动后端

后端默认端口是 `48080`。启动前先确认端口占用和进程来源：

```powershell
Get-NetTCPConnection -LocalPort 48080 -State Listen -ErrorAction SilentlyContinue
Get-CimInstance Win32_Process -Filter "ProcessId=<pid>" | Select-Object ProcessId,CommandLine
```

打包后端：

```powershell
cd backend
mvn package -DskipTests -pl yudao-server -am
cd ..
```

在仓库根目录后台启动：

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

如果本机 JDK17 路径不同，只调整 `$java`，不要改 Maven 项目的 Java 基线。

验证后端：

```powershell
Get-NetTCPConnection -LocalPort 48080 -State Listen -ErrorAction SilentlyContinue
Get-Content runtime-logs/yudao-server.out.log -Tail 80
Invoke-WebRequest http://localhost:48080/admin-api/system/auth/captcha-image -UseBasicParsing
```

## 启动前端

前端开发服务默认使用 `80`，本地请求地址优先查看 `frontend/.env.local`。

```powershell
cd frontend
pnpm install
pnpm dev
```

如果需要由 Agent 后台启动并显式指定端口，优先直接调用 Vite：

```powershell
$front = Join-Path (Resolve-Path '.').Path 'frontend'
$out = Join-Path $front 'vite-dev.out.log'
$err = Join-Path $front 'vite-dev.err.log'
$vite = Join-Path $front 'node_modules\vite\bin\vite.js'

Start-Process -WindowStyle Hidden -FilePath 'node.exe' `
  -ArgumentList @(('"{0}"' -f $vite),'--mode','env.local','--host','0.0.0.0','--port','80') `
  -WorkingDirectory $front `
  -RedirectStandardOutput $out `
  -RedirectStandardError $err
```

验证前端：

```powershell
Get-NetTCPConnection -LocalPort 80 -State Listen -ErrorAction SilentlyContinue
Invoke-WebRequest http://localhost/ -UseBasicParsing
```

## 提交前检查

本地配置和日志不应进入提交。提交前至少确认：

```powershell
git status --short --branch
git diff --check
```

如果改过配置、SQL 或环境变量文件，建议额外做一次密钥形态扫描：

```powershell
git grep -n -E "AKID[A-Za-z0-9]{20,}|LTAI[A-Za-z0-9]{16,}|AKLT[A-Za-z0-9]{20,}|BEGIN (RSA|OPENSSH|EC|DSA) PRIVATE KEY"
```

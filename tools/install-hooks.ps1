# Install project git hooks into .git/hooks.
# Run from repo root:  powershell -ExecutionPolicy Bypass -File tools/install-hooks.ps1

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..')
$hooksSrc = Join-Path $repoRoot 'tools\hooks'
$hooksDst = Join-Path $repoRoot '.git\hooks'

if (-not (Test-Path $hooksDst)) {
  Write-Error ".git/hooks not found at $hooksDst"
  exit 1
}

$hook = 'commit-msg'
$src = Join-Path $hooksSrc $hook
$dst = Join-Path $hooksDst $hook

if (-not (Test-Path $src)) {
  Write-Error "Source hook not found: $src"
  exit 1
}

Copy-Item -Path $src -Destination $dst -Force

# Make executable for Git's bash on Windows.
$git = Get-Command git -ErrorAction SilentlyContinue
$bash = Join-Path (Split-Path $git.Source) '..\bin\sh.exe'
if (Test-Path $bash) {
  & $bash -c "chmod +x '$(($dst -replace '\\','/'))'"
}

Write-Host "Installed $hook hook to $dst" -ForegroundColor Green

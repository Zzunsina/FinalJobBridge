param([int]$Port = 80)

$ErrorActionPreference = 'Stop'

Push-Location (Join-Path $PSScriptRoot 'employment-frontend')
try {
    if (-not (Test-Path -LiteralPath 'node_modules\vite')) {
        & (Join-Path $PSScriptRoot 'tools\bin\npm.cmd') ci
        if ($LASTEXITCODE -ne 0) { throw '의존성 설치에 실패했습니다.' }
    }
    & (Join-Path $PSScriptRoot 'tools\bin\npm.cmd') run dev -- --port $Port
} finally {
    Pop-Location
}

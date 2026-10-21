param([int]$Port = 80)

$ErrorActionPreference = 'Stop'

Push-Location (Join-Path (Split-Path -Parent $PSScriptRoot) 'JobBridge-Frontend')
try {
    if (-not (Test-Path -LiteralPath 'node_modules\vite')) {
        & (Join-Path $PSScriptRoot 'tools\bin\npm.cmd') ci
        if ($LASTEXITCODE -ne 0) { throw '?섏〈???ㅼ튂???ㅽ뙣?덉뒿?덈떎.' }
    }
    & (Join-Path $PSScriptRoot 'tools\bin\npm.cmd') run dev -- --port $Port
} finally {
    Pop-Location
}


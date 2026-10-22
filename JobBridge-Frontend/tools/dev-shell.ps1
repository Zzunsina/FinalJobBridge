$env:PATH = (Join-Path (Split-Path -Parent $PSScriptRoot) 'tools\bin') + ';' + $env:PATH
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)

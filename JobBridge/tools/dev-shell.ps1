# Make the project-local real npm CLI available in this terminal.
$taskProjectRoot = Split-Path -Parent $PSScriptRoot

$env:PATH = (Join-Path $taskProjectRoot 'tools\bin') + ';' + $env:PATH

Set-Location -LiteralPath $taskProjectRoot

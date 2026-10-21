param([Parameter(ValueFromRemainingArguments = $true)][string[]]$NpmArguments)

$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskRuntime = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies'
$taskNodeCommand = Get-Command node -ErrorAction SilentlyContinue
$taskNode = if ($taskNodeCommand) { $taskNodeCommand.Source } else { Join-Path $taskRuntime 'node\bin\node.exe' }
$taskNpmCli = Join-Path $taskRoot '.tools\npm-runtime\package\bin\npm-cli.js'
if (-not (Test-Path -LiteralPath $taskNpmCli)) {
    throw 'Project npm runtime missing. Install Node.js with npm, then use npm from a new terminal.'
}
$env:PATH = (Join-Path $PSScriptRoot 'bin') + ';' + (Split-Path -Parent $taskNode) + ';' + $env:PATH
& $taskNode $taskNpmCli @NpmArguments
exit $LASTEXITCODE

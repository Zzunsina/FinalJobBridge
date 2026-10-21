param([int]$Port = 8080, [ValidateSet('default','dev','mariadb')][string]$Profile = 'default')
& (Join-Path (Split-Path -Parent $PSScriptRoot) 'start-backend.ps1') -Port $Port -Profile $Profile
exit $LASTEXITCODE

param(
    [int]$Port = 8080,
    [ValidateSet('default', 'dev', 'mariadb')]
    [string]$Profile = 'default'
)

$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot

$taskPortProbe = New-Object System.Net.Sockets.TcpClient
try {
    $taskConnection = $taskPortProbe.ConnectAsync('127.0.0.1', $Port)
    $taskConnected = $false
    try { $taskConnected = $taskConnection.Wait(500) -and $taskPortProbe.Connected } catch { }
    if ($taskConnected) {
        throw "Port $Port is already in use. If IntelliJ is running the backend, start only the Frontend task in VS Code. Stop the existing backend before starting another one."
    }
} finally {
    $taskPortProbe.Dispose()
}

if (-not $env:JAVA_HOME) {
    $taskJdk = Get-ChildItem -LiteralPath 'C:\Program Files\Java' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'jdk-17*' } |
        Select-Object -First 1
    if ($taskJdk) { $env:JAVA_HOME = $taskJdk.FullName }
}

$env:GRADLE_USER_HOME = Join-Path $taskRoot '.tools\gradle'
if ($Profile -eq 'dev' -and -not $env:DEV_SEED_PASSWORD) {
    $env:DEV_SEED_PASSWORD = 'JobBridge123!'
}

# Keep the local signing key across restarts. Never print it or add it to Git.
if (-not $env:JWT_SECRET) {
    $taskLocalDirectory = Join-Path $taskRoot '.tools'
    New-Item -ItemType Directory -Path $taskLocalDirectory -Force | Out-Null
    $taskSecretPath = Join-Path $taskLocalDirectory 'local-jwt-secret.txt'
    if (-not (Test-Path -LiteralPath $taskSecretPath)) {
        $taskBytes = New-Object byte[] 48
        $taskRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $taskRandom.GetBytes($taskBytes) } finally { $taskRandom.Dispose() }
        [System.IO.File]::WriteAllText($taskSecretPath, [Convert]::ToBase64String($taskBytes))
    }
    $env:JWT_SECRET = [System.IO.File]::ReadAllText($taskSecretPath).Trim()
}

Push-Location (Join-Path $taskRoot 'employment-backend')
try {
    Write-Host "Starting backend: profile=$Profile, port=$Port"
    & .\gradlew.bat bootRun "--args=--spring.profiles.active=$Profile --server.port=$Port" --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Backend startup failed. See the error above.' }
} finally {
    Pop-Location
}

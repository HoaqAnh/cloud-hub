$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

Set-Location -LiteralPath $root
$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) {
    $envFile = Join-Path $root '.env.example'
}

$lineNumber = 0
foreach ($line in [System.IO.File]::ReadLines($envFile)) {
    $lineNumber++
    $entry = $line.Trim()
    if (-not $entry -or $entry.StartsWith('#')) {
        continue
    }
    if ($entry -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        throw "Invalid env entry at ${envFile}:$lineNumber"
    }

    $name = $Matches[1]
    $value = $Matches[2].Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
        ($value.StartsWith("'") -and $value.EndsWith("'"))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    [System.Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

docker compose --env-file $envFile -f infra/docker-compose.yml up -d

$shell = if ($PSVersionTable.PSEdition -eq 'Core') {
    Join-Path $PSHOME 'pwsh.exe'
} else {
    Join-Path $PSHOME 'powershell.exe'
}

$previousProfiles = $env:SPRING_PROFILES_INCLUDE
$env:SPRING_PROFILES_INCLUDE = (@($previousProfiles, 'local-http-log') | Where-Object { $_ }) -join ','
try {
    Start-Process -FilePath $shell -ArgumentList '-NoExit', '-Command', "Set-Location -LiteralPath '$root\apps\backend'; .\mvnw.cmd spring-boot:run"
} finally {
    $env:SPRING_PROFILES_INCLUDE = $previousProfiles
}
Start-Process -FilePath $shell -ArgumentList '-NoExit', '-Command', "Set-Location -LiteralPath '$root\apps\frontend'; pnpm dev"

Write-Host "Frontend: http://localhost:5173"
$backendPort = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { '8080' }
Write-Host "Backend:  http://localhost:$backendPort"

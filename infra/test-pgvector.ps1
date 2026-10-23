param()

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$containerName = 'studyhub-pgvector-test-' + [Guid]::NewGuid().ToString('N')
$testPassword = [Guid]::NewGuid().ToString('N')
$previousUrl = $env:PGVECTOR_TEST_URL
$previousUser = $env:PGVECTOR_TEST_USER
$previousPassword = $env:PGVECTOR_TEST_PASSWORD
$testExitCode = 1

try {
    docker run --detach --rm --name $containerName `
        --env POSTGRES_DB=studyhub_test --env POSTGRES_USER=studyhub_test `
        --env "POSTGRES_PASSWORD=$testPassword" --publish '127.0.0.1::5432' `
        pgvector/pgvector:0.8.7-pg17 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot start the pgvector test container.' }

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        docker exec $containerName pg_isready -h 127.0.0.1 -U studyhub_test -d studyhub_test *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'PostgreSQL did not become ready.' }

    $containerInfo = docker inspect $containerName | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the test container.' }
    $testPort = $containerInfo[0].NetworkSettings.Ports.'5432/tcp'[0].HostPort
    $env:PGVECTOR_TEST_URL = "jdbc:postgresql://127.0.0.1:$testPort/studyhub_test"
    $env:PGVECTOR_TEST_USER = 'studyhub_test'
    $env:PGVECTOR_TEST_PASSWORD = $testPassword

    Push-Location (Join-Path $projectRoot 'backend')
    try {
        # Windows PowerShell surfaces native stderr (including JVM warnings) as errors.
        # Maven's exit code, rather than the presence of stderr, determines success.
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd -Ppgvector-it verify
        $testExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = 'Stop'
        Pop-Location
    }
} finally {
    docker rm --force --volumes $containerName | Out-Null
    $env:PGVECTOR_TEST_URL = $previousUrl
    $env:PGVECTOR_TEST_USER = $previousUser
    $env:PGVECTOR_TEST_PASSWORD = $previousPassword
}

exit $testExitCode

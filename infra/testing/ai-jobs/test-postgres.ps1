param()

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$containerName = 'studyhub-ai-jobs-test-' + [Guid]::NewGuid().ToString('N')
$testPassword = [Guid]::NewGuid().ToString('N')
$previousUrl = $env:PGVECTOR_TEST_URL
$previousUser = $env:PGVECTOR_TEST_USER
$previousPassword = $env:PGVECTOR_TEST_PASSWORD
$createdContainer = $false
$previousSimulationUrl = $env:AI_SIMULATION_URL
$simulationProcess = $null
$simulationLog = Join-Path ([System.IO.Path]::GetTempPath()) ('studyhub-ai-jobs-' + [Guid]::NewGuid().ToString('N') + '.ndjson')
$simulationError = $simulationLog + '.err'
$testExitCode = 1
$startedAt = Get-Date
$integrationTests = @('AIJobMigrationIT', 'AIJobConcurrencyIT', 'DocumentIngestionMigrationIT', 'DocumentIngestionApiIT')

try {
    if (-not $env:AI_SIMULATION_URL) {
        $postman = Get-Command postman -ErrorAction Stop
        $simulationProcess = Start-Process -FilePath $postman.Source -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
            -ArgumentList @('simulation', 'run', 'infra/testing/docling/extraction.sim.yaml', '--port', 'auto', '--no-history', '--output', 'ndjson') `
            -RedirectStandardOutput $simulationLog -RedirectStandardError $simulationError
        for ($attempt = 0; $attempt -lt 60; $attempt++) {
            if (Test-Path -LiteralPath $simulationLog) {
                foreach ($line in (Get-Content -LiteralPath $simulationLog)) {
                    if ($line.StartsWith('{')) {
                        $event = $line | ConvertFrom-Json
                        if ($event.event -eq 'listening') { $env:AI_SIMULATION_URL = $event.url; break }
                    }
                }
            }
            if ($env:AI_SIMULATION_URL) { break }
            if ($simulationProcess.HasExited) { throw "Postman simulation exited: $(Get-Content -LiteralPath $simulationError -Raw)" }
            Start-Sleep -Seconds 1
        }
        if (-not $env:AI_SIMULATION_URL) { throw 'Postman simulation did not become ready.' }
    }
    if ($env:PGVECTOR_TEST_URL) {
        if (-not $env:PGVECTOR_TEST_USER -or -not $env:PGVECTOR_TEST_PASSWORD) {
            throw 'An isolated TEST database requires all three PGVECTOR_TEST_* variables.'
        }
    } else {
        if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker or a dedicated TEST PostgreSQL database is required.' }
        docker info *> $null
        if ($LASTEXITCODE -ne 0) { throw 'Docker is unavailable. Start Docker or supply a dedicated TEST database.' }
        docker run --detach --rm --name $containerName --env POSTGRES_DB=studyhub_test --env POSTGRES_USER=studyhub_test `
            --env "POSTGRES_PASSWORD=$testPassword" --publish '127.0.0.1::5432' pgvector/pgvector:0.8.7-pg17 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Cannot start the disposable pgvector container.' }
        $createdContainer = $true
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
    }
    Push-Location (Join-Path $projectRoot 'backend')
    try {
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd '-Dtest=AIJob*Test' "-Dit.test=$($integrationTests -join ',')" -Ppgvector-it verify
        $testExitCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($testExitCode -eq 0) {
            foreach ($test in $integrationTests) {
                $report = Get-Item "target/failsafe-reports/TEST-com.studyhub.$(if ($test.StartsWith('AIJob')) { 'ai.job' } else { 'document' }).$test.xml" -ErrorAction Stop
                if ($report.LastWriteTime -lt $startedAt) { throw "Stale report for $test" }
                [xml]$result = Get-Content $report.FullName
                $suite = $result.testsuite
                if ([int]$suite.tests -lt 1 -or [int]$suite.skipped -gt 0 -or [int]$suite.failures -gt 0 -or [int]$suite.errors -gt 0) {
                    throw "Integration test did not pass without skips: $test"
                }
            }
        }
    } finally { $ErrorActionPreference = 'Stop'; Pop-Location }
} finally {
    if ($createdContainer) { docker rm --force --volumes $containerName | Out-Null }
    if ($simulationProcess -and -not $simulationProcess.HasExited) {
        Stop-Process -Id $simulationProcess.Id -ErrorAction SilentlyContinue
    }
    $env:AI_SIMULATION_URL = $previousSimulationUrl
    $env:PGVECTOR_TEST_URL = $previousUrl
    $env:PGVECTOR_TEST_USER = $previousUser
    $env:PGVECTOR_TEST_PASSWORD = $previousPassword
}
exit $testExitCode

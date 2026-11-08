$ErrorActionPreference = 'Stop'
$smokeRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$smokeTarget = Join-Path $smokeRoot 'backend\target'
$smokeJar = Join-Path $smokeTarget 'alloca-backend-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $smokeJar)) { throw 'Run backend mvnw verify first.' }
foreach ($smokePort in @(15436, 18083)) {
    if (Get-NetTCPConnection -LocalPort $smokePort -State Listen -ErrorAction SilentlyContinue) {
        throw "Test port $smokePort is already in use. No existing service was changed."
    }
}
$smokeContainer = $null
$smokeProcess = $null
$smokeOldBase = $env:ALLOCA_SMOKE_BASE_URL
try {
    # No volumes and an ephemeral container: application/Supabase databases are never used.
    $smokeContainer = (& docker run --rm -d --label com.alloca.test=booking-live-smoke -p 127.0.0.1:15436:5432 -e POSTGRES_USER=smoke -e POSTGRES_DB=smoke -e POSTGRES_PASSWORD=only-smoke-password postgres:16-alpine).Trim()
    if ($LASTEXITCODE -ne 0 -or $smokeContainer -notmatch '^[0-9a-f]{64}$') { throw 'Could not start isolated PostgreSQL.' }
    $smokeDeadline = (Get-Date).AddSeconds(45)
    do {
        & docker exec $smokeContainer pg_isready -U smoke -d smoke 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $smokeDeadline)
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL startup timed out.' }

    $smokeLog = Join-Path $smokeTarget 'booking-live-server.log'
    $smokeErrors = Join-Path $smokeTarget 'booking-live-server-errors.log'
    $smokeArgs = @('-jar', $smokeJar,
        '--spring.config.location=optional:classpath:booking-smoke-no-config.properties',
        '--spring.datasource.url=jdbc:postgresql://127.0.0.1:15436/smoke', '--spring.datasource.username=smoke', '--spring.datasource.password=only-smoke-password',
        '--spring.jpa.hibernate.ddl-auto=validate', '--spring.jpa.open-in-view=false', '--spring.jpa.properties.hibernate.jdbc.time_zone=UTC',
        '--spring.jackson.deserialization.fail-on-unknown-properties=true', '--JWT_SECRET=isolated-booking-live-smoke-secret-at-least-32-bytes',
        '--CORS_ALLOWED_ORIGINS=http://localhost:5173', '--server.address=127.0.0.1', '--server.port=18083')
    $smokeProcess = Start-Process -FilePath 'java' -ArgumentList $smokeArgs -WorkingDirectory $smokeRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $smokeLog -RedirectStandardError $smokeErrors
    $smokeDeadline = (Get-Date).AddSeconds(60)
    do {
        if ($smokeProcess.HasExited) { throw "Test server exited. Check $smokeLog and $smokeErrors." }
        if ((Get-Content -LiteralPath $smokeLog -Raw -ErrorAction SilentlyContinue) -match 'Started AllocaApplication') { break }
        Start-Sleep -Milliseconds 300
    } while ((Get-Date) -lt $smokeDeadline)
    if ((Get-Date) -ge $smokeDeadline) { throw 'Test server startup timed out.' }

    $smokeSql = @'
create extension if not exists pgcrypto;
insert into users(id,username,email,password_hash,full_name,role) values
('00000000-0000-0000-0000-000000000101','booking-smoke-owner','smoke@example.invalid',crypt('Only-Smoke-123!',gen_salt('bf',12)),'Smoke Owner','EMPLOYEE');
insert into zones(id,name,floor,building) values ('00000000-0000-0000-0000-000000000201','Smoke zone','1','Test');
insert into resources(id,name,type,capacity,status,zone_id) values ('00000000-0000-0000-0000-000000000301','Live smoke room','ROOM',4,'AVAILABLE','00000000-0000-0000-0000-000000000201');
insert into resource_schedules(id,resource_id,day_of_week,start_time,end_time)
select gen_random_uuid(),'00000000-0000-0000-0000-000000000301',day,'08:00','17:00' from generate_series(1,7) day;
'@
    & docker exec $smokeContainer psql -v ON_ERROR_STOP=1 -U smoke -d smoke -c $smokeSql | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Failed to seed isolated fixture.' }
    $env:ALLOCA_SMOKE_BASE_URL = 'http://127.0.0.1:18083'
    Push-Location (Join-Path $smokeRoot 'frontend')
    try {
        & node tests/booking-live-smoke.ts
        if ($LASTEXITCODE -ne 0) { throw 'Frontend live API smoke test failed.' }
    } finally { Pop-Location }
} finally {
    $env:ALLOCA_SMOKE_BASE_URL = $smokeOldBase
    if ($smokeProcess -and -not $smokeProcess.HasExited) { Stop-Process -Id $smokeProcess.Id }
    # Only the exact container ID created above is stopped; --rm removes its disposable data.
    if ($smokeContainer -and $smokeContainer -match '^[0-9a-f]{64}$') { & docker stop --time 5 $smokeContainer | Out-Null }
}

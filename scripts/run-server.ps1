param([switch]$Demo,[int]$Port=8080,[switch]$Build)
$ErrorActionPreference='Stop'
$nearRoot=Split-Path $PSScriptRoot -Parent
Push-Location (Join-Path $nearRoot 'server')
try {
    if($Build -or !(Test-Path 'target/nearcircle-server-1.0.0.jar')) {
        & mvn '-Dmaven.repo.local=.m2' package -B -ntp
        if($LASTEXITCODE -ne 0){throw 'Server build failed.'}
    }
    $nearArgs=@('-jar','target/nearcircle-server-1.0.0.jar',"--server.port=$Port")
    if($Demo){$nearArgs+='--spring.profiles.active=demo'}
    & java @nearArgs
    if($LASTEXITCODE -ne 0){throw 'Server exited with an error.'}
} finally {Pop-Location}

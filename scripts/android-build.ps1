param(
    [string[]]$Tasks = @('testDebugUnitTest', 'assembleDebug', 'lintDebug'),
    [string]$SdkRoot = $env:ANDROID_HOME
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (!$SdkRoot -and (Test-Path (Join-Path $projectRoot 'work/android-sdk/platforms/android-35/android.jar'))) {
    $SdkRoot = Join-Path $projectRoot 'work/android-sdk'
}
if (!$SdkRoot) { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
if (!(Test-Path (Join-Path $SdkRoot 'platforms/android-35/android.jar'))) {
    throw "Install Android SDK Platform 35 first. SDK path: $SdkRoot"
}
if (!$env:JAVA_HOME -or !(Test-Path (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    throw 'Set JAVA_HOME to a JDK 17 installation.'
}
$env:ANDROID_HOME = $SdkRoot
$env:GRADLE_USER_HOME = Join-Path $projectRoot 'work/gradle-home'
Push-Location (Join-Path $projectRoot 'android')
try {
    & ./gradlew.bat @Tasks --console=plain --no-daemon
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE." }
} finally { Pop-Location }

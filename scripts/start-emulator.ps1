param([string]$SdkRoot = $env:ANDROID_HOME, [int]$Port = 5554)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (!$SdkRoot) { $SdkRoot = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$emulator = Join-Path $SdkRoot 'emulator/emulator.exe'
$systemImage = Join-Path $projectRoot 'work/android-sdk/system-images/android-35/default/x86_64'
if (!(Test-Path (Join-Path $systemImage 'system.img'))) {
    throw 'API 35 image missing. Extract the official x86_64-35_r02.zip to work/android-sdk/system-images/android-35/default (see docs/tooling.md).'
}
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:ANDROID_AVD_HOME = Join-Path $projectRoot 'work/avd'
$env:ANDROID_EMULATOR_HOME = Join-Path $projectRoot 'work/emulator-home'
$avdPath = Join-Path $env:ANDROID_AVD_HOME 'NearCircle35.avd'
New-Item -ItemType Directory -Force $avdPath,$env:ANDROID_EMULATOR_HOME | Out-Null
@"
avd.ini.encoding=UTF-8
path=$avdPath
target=android-35
"@ | Set-Content (Join-Path $env:ANDROID_AVD_HOME 'NearCircle35.ini') -Encoding utf8
@"
AvdId=NearCircle35
avd.ini.encoding=UTF-8
avd.name=NearCircle35
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.ramSize=2048
hw.lcd.width=1080
hw.lcd.height=1920
hw.lcd.density=420
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader_indirect
hw.mainKeys=no
hw.battery=yes
hw.audioInput=no
hw.camera.back=none
hw.camera.front=none
disk.dataPartition.size=1G
image.sysdir.1=$systemImage\
tag.id=default
tag.display=Default Android System Image
PlayStore.enabled=false
showDeviceFrame=no
fastboot.forceColdBoot=yes
"@ | Set-Content (Join-Path $avdPath 'config.ini') -Encoding utf8
$logDir = Join-Path $projectRoot 'work'
$process = Start-Process -FilePath $emulator -ArgumentList @('-avd','NearCircle35','-port',"$Port",'-partition-size','1536','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-no-metrics','-gpu','swiftshader_indirect') -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logDir 'emulator.stdout.log') -RedirectStandardError (Join-Path $logDir 'emulator.stderr.log')
Write-Output "Emulator PID=$($process.Id), adb serial=emulator-$Port"
Write-Output 'Boot progress: adb -s emulator-5554 shell getprop sys.boot_completed'

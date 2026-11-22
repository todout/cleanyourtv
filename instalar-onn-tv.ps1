param(
    [Parameter(Mandatory = $true)]
    [string]$Target,
    [string]$ApkPath = "$PSScriptRoot\launcher\app\build\outputs\apk\debug\app-debug.apk",
    [string]$AdbPath = "adb"
)

if (-not (Test-Path $ApkPath)) {
    Write-Error "No se encontró el APK en: $ApkPath"
    exit 1
}

& $AdbPath connect $Target
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $AdbPath -s $Target install -r $ApkPath
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$CfgPath = "$PSScriptRoot\config.json"
if (Test-Path $CfgPath) {
    & $AdbPath -s $Target shell "mkdir -p /sdcard/Android/data/com.gathin.tvhome/files"
    & $AdbPath -s $Target push $CfgPath "/sdcard/Android/data/com.gathin.tvhome/files/config.json"
}

& $AdbPath -s $Target shell pm disable-user --user 0 com.spocky.projengmenu
& $AdbPath -s $Target shell pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS
& $AdbPath -s $Target shell cmd package set-home-activity com.gathin.tvhome/.MainActivity
& $AdbPath -s $Target shell cmd role add-role-holder android.app.role.HOME com.gathin.tvhome

$hasLauncherX = & $AdbPath -s $Target shell pm path com.google.android.apps.tv.launcherx
if ($hasLauncherX -match "package:") {
    & $AdbPath -s $Target shell pm enable com.google.android.apps.tv.launcherx
    & $AdbPath -s $Target shell am force-stop com.gathin.tvhome
    & $AdbPath -s $Target shell am start -n com.gathin.tvhome/.MainActivity
    & $AdbPath -s $Target shell "settings put secure enabled_accessibility_services ''"
    Start-Sleep -Seconds 1
    & $AdbPath -s $Target shell "settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.HomeKeyService && settings put secure accessibility_enabled 1"
} else {
    & $AdbPath -s $Target shell pm disable-user --user 0 com.google.android.tvlauncher
    & $AdbPath -s $Target shell am force-stop com.gathin.tvhome
    & $AdbPath -s $Target shell am start -n com.gathin.tvhome/.MainActivity
}

Write-Host "CleanYourTV instalado y configurado como Inicio en $Target" -ForegroundColor Green

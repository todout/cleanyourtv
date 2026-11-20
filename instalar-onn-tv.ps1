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

& $AdbPath -s $Target shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
& $AdbPath -s $Target shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
& $AdbPath -s $Target shell cmd package set-home-activity com.gathin.tvhome/.MainActivity
& $AdbPath -s $Target shell cmd role add-role-holder android.app.role.HOME com.gathin.tvhome
& $AdbPath -s $Target shell am start -n com.gathin.tvhome/.MainActivity

Write-Host "CleanYourTV instalado y configurado como Inicio en $Target" -ForegroundColor Green

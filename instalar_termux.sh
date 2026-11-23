#!/usr/bin/env bash
# ==============================================================================
# Instalador de CleanYourTV Onn TV desde Termux (Android) o Linux/Mac
# Uso:
#   curl -sL https://raw.githubusercontent.com/todout/cleanyourtv/main/instalar_termux.sh | bash -s -- IP_TV:5555
# ==============================================================================
set -e

APK_URL="https://github.com/todout/cleanyourtv/releases/latest/download/CleanYourTV-OnnTV.apk"
CONFIG_URL="https://raw.githubusercontent.com/todout/cleanyourtv/main/config.json"
TMP_APK="/tmp/CleanYourTV-OnnTV.apk"
TMP_CFG="/tmp/cleanyourtv-config.json"
if [ -d "$HOME" ]; then
    TMP_APK="$HOME/CleanYourTV-OnnTV.apk"
    TMP_CFG="$HOME/cleanyourtv-config.json"
fi

if ! command -v adb >/dev/null 2>&1; then
    echo "[ERROR] adb no está instalado. En Termux ejecutá: pkg install android-tools curl"
    exit 1
fi

TARGET="$1"
if [ -z "$TARGET" ]; then
    read -rp "Ingresá la IP (o IP:puerto) del Onn TV (ej: 192.168.1.50): " TARGET
fi

if [ -z "$TARGET" ]; then
    echo "[ERROR] No se indicó IP."
    exit 1
fi

# Agregar :5555 si no incluye puerto
if [[ "$TARGET" != *:* ]]; then
    TARGET="${TARGET}:5555"
fi

adb_sh() {
    adb -s "$TARGET" shell "$@" </dev/null
}

echo "==> Conectando a $TARGET ..."
adb connect "$TARGET" </dev/null

echo "==> Descargando CleanYourTV-OnnTV.apk ..."
curl -fL "$APK_URL" -o "$TMP_APK"

echo "==> Instalando APK ..."
adb -s "$TARGET" install -r "$TMP_APK" </dev/null

# Seguridad: verificar que CleanYourTV quedó instalado antes de deshabilitar el launcher oficial
if ! adb_sh "pm path com.gathin.tvhome" | grep -q "package:"; then
    echo "[ERROR] CleanYourTV no figura instalado. No se deshabilitó el launcher oficial."
    exit 1
fi

echo "==> Aplicando configuración visual (config.json) ..."
curl -fL "$CONFIG_URL" -o "$TMP_CFG"
adb_sh "mkdir -p /sdcard/Android/data/com.gathin.tvhome/files"
adb -s "$TARGET" push "$TMP_CFG" "/sdcard/Android/data/com.gathin.tvhome/files/config.json" </dev/null

echo "==> Configurando CleanYourTV como Inicio ..."
adb_sh "pm disable-user --user 0 com.spocky.projengmenu" || true
adb_sh "pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS" || true
adb_sh "cmd package set-home-activity com.gathin.tvhome/.MainActivity" || true
adb_sh "cmd role add-role-holder android.app.role.HOME com.gathin.tvhome" || true

if adb_sh "pm path com.google.android.apps.tv.launcherx" | grep -q "package:"; then
    # Google TV (Onn TV): mantener launcherx habilitado para que funcione Google Assistant (micrófono)
    adb_sh "pm enable com.google.android.apps.tv.launcherx" || true
    adb_sh "rm -f /sdcard/Android/data/com.gathin.tvhome/files/wallpapers_meta.json" || true
    adb_sh "am force-stop com.gathin.tvhome"
    adb_sh "am start -n com.gathin.tvhome/.MainActivity"
    sleep 1
    adb_sh "settings put secure enabled_accessibility_services ''" || true
    sleep 1
    adb_sh "settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.HomeKeyService && settings put secure accessibility_enabled 1" || true
    sleep 2
    # Verificar que HomeKeyService haya quedado enlazado (Bound services) y no vacío ni crasheado
    if ! adb_sh "dumpsys accessibility" | grep -q "Bound services:{Service\["; then
        adb_sh "settings put secure enabled_accessibility_services ''" || true
        sleep 1
        adb_sh "settings put secure enabled_accessibility_services com.gathin.tvhome/com.gathin.tvhome.HomeKeyService && settings put secure accessibility_enabled 1" || true
        sleep 2
    fi
    echo "==> Verificando servicio de accesibilidad (HomeKeyService) ..."
    adb_sh "dumpsys accessibility" | grep -E "Bound services|Enabled services|Crashed services" || true
else
    # Android TV (ej. Mi Box S): deshabilitar tvlauncher
    adb_sh "pm disable-user --user 0 com.google.android.tvlauncher" || true
    adb_sh "rm -f /sdcard/Android/data/com.gathin.tvhome/files/wallpapers_meta.json" || true
    adb_sh "am force-stop com.gathin.tvhome"
    adb_sh "am start -n com.gathin.tvhome/.MainActivity"
fi

rm -f "$TMP_APK" "$TMP_CFG"
echo ""
echo "✓ Listo. CleanYourTV quedó instalado y configurado como Inicio en $TARGET."

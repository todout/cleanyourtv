#!/usr/bin/env bash
# ==============================================================================
# Instalador de CleanYourTV Onn TV desde Termux (Android) o Linux/Mac
# Uso:
#   curl -sL https://raw.githubusercontent.com/todout/cleanyourtv/main/instalar_termux.sh | bash -s -- IP_TV:5555
# ==============================================================================
set -e

APK_URL="https://github.com/todout/cleanyourtv/releases/latest/download/CleanYourTV-OnnTV.apk"
TMP_APK="/tmp/CleanYourTV-OnnTV.apk"
[ -d "$HOME" ] && TMP_APK="$HOME/CleanYourTV-OnnTV.apk"

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

echo "==> Conectando a $TARGET ..."
adb connect "$TARGET"

echo "==> Descargando CleanYourTV-OnnTV.apk ..."
curl -fL "$APK_URL" -o "$TMP_APK"

echo "==> Instalando APK ..."
adb -s "$TARGET" install -r "$TMP_APK"

# Seguridad: verificar que CleanYourTV quedó instalado antes de deshabilitar el launcher oficial
if ! adb -s "$TARGET" shell "pm path com.gathin.tvhome" | grep -q "package:"; then
    echo "[ERROR] CleanYourTV no figura instalado. No se deshabilitó el launcher oficial."
    exit 1
fi

echo "==> Configurando CleanYourTV como Inicio y deshabilitando launcher oficial + setupwraith ..."
adb -s "$TARGET" shell "pm disable-user --user 0 com.google.android.apps.tv.launcherx" || true
adb -s "$TARGET" shell "pm disable-user --user 0 com.google.android.tungsten.setupwraith" || true
adb -s "$TARGET" shell "cmd package set-home-activity com.gathin.tvhome/.MainActivity" || true
adb -s "$TARGET" shell "cmd role add-role-holder android.app.role.HOME com.gathin.tvhome" || true

echo "==> Iniciando CleanYourTV ..."
adb -s "$TARGET" shell "am start -n com.gathin.tvhome/.MainActivity"

rm -f "$TMP_APK"
echo ""
echo "✓ Listo. CleanYourTV quedó instalado y configurado como Inicio en $TARGET."

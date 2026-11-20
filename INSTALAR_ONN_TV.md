# CleanYourTV Personalizado para Onn TV

Fork personalizado de [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) con:
- Fondos remotos automáticos desde [`fondos-projectivy`](https://github.com/todout/fondos-projectivy/tree/wallpapers) (`wallpapers.json`).
- Widget de clima y pronóstico en la parte superior derecha.
- Grilla de apps con recorte superior (`clipToPadding="true"`) para que al bajar no tape el texto del fondo ni el clima.

---

## Instalación rápida desde el celular Android (Termux)

Con **Termux** (`pkg install android-tools curl`) en la misma red Wi-Fi que el Onn TV:

```bash
curl -sL https://raw.githubusercontent.com/todout/cleanyourtv/main/instalar_termux.sh | bash -s -- 192.168.0.XXX
```

El script descarga el último APK de Releases, lo instala, verifica que `com.gathin.tvhome` esté instalado, deshabilita el launcher oficial y `setupwraith` (para evitar la pantalla negra), fija CleanYourTV como Inicio y lo abre automáticamente.

---

## Instalación rápida en otro Onn TV (con PC / Notebook en el mismo Wi-Fi)

1. En el **Onn TV**, activá **Opciones para desarrolladores > Depuración USB / Depuración inalámbrica** (o ADB por red en el puerto `5555`).
2. Descargá el APK desde **Releases** (`CleanYourTV-OnnTV.apk`) o compilalo con `.\gradlew.bat assembleDebug`.
3. Ejecutá el script de PowerShell incluido (cambiando la IP por la del Onn TV):

```powershell
.\instalar-onn-tv.ps1 -Target "192.168.0.XXX:5555"
```

O manualmente por `adb`:

```bash
adb connect 192.168.0.XXX:5555
adb -s 192.168.0.XXX:5555 install -r CleanYourTV-OnnTV.apk
adb -s 192.168.0.XXX:5555 shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb -s 192.168.0.XXX:5555 shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
adb -s 192.168.0.XXX:5555 shell cmd package set-home-activity com.gathin.tvhome/.MainActivity
adb -s 192.168.0.XXX:5555 shell cmd role add-role-holder android.app.role.HOME com.gathin.tvhome
adb -s 192.168.0.XXX:5555 shell am start -n com.gathin.tvhome/.MainActivity
```

> **Importante:** Deshabilitar `com.google.android.tungsten.setupwraith` evita que el botón Inicio quede en pantalla negra si Android intenta abrir `RecoveryActivity`.

## Instalación directa desde la TV (sin PC)
1. Abrí **Downloader** en el Onn TV y entrá a:
   `https://github.com/todout/cleanyourtv/releases/latest/download/CleanYourTV-OnnTV.apk`
2. Instalá el APK.
3. Para que el botón **Inicio** abra siempre CleanYourTV, configurá CleanYourTV como launcher predeterminado (o deshabilitá el launcher stock por ADB desde la notebook/celular).

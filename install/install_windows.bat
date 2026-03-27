@echo off

if not exist "platform-tools/adb" (
    echo Downloading and unzipping Android Platform Tools. Please wait...
    powershell.exe -nologo -noprofile -command "(New-Object Net.WebClient).DownloadFile('https://dl.google.com/android/repository/platform-tools-latest-windows.zip', 'platform-tools-latest-windows.zip')"
    powershell.exe -nologo -noprofile -command "& { $shell = New-Object -COM Shell.Application; $target = $shell.NameSpace('%cd%'); $zip = $shell.NameSpace('%cd%\platform-tools-latest-windows.zip'); $target.CopyHere($zip.Items(), 16); }"
)

if not exist "distraquit-latest.apk" (
    echo Downloading latest DistraQuit APK...
    powershell.exe -nologo -noprofile -command "(New-Object Net.WebClient).DownloadFile('https://github.com/flxapps/DistraQuit/releases/latest/download/app-release.apk', 'distraquit-latest.apk')"
)

echo Starting ADB
echo If your device asks you to allow USB Debugging, press "Accept".
%cd%\platform-tools\adb.exe start-server

echo Installing DistraQuit on your device...
%cd%\platform-tools\adb.exe install -r -t distraquit-latest.apk

echo Granting Permissions
%cd%\platform-tools\adb.exe shell pm grant com.flx_apps.distraquit android.permission.WRITE_SECURE_SETTINGS
%cd%\platform-tools\adb.exe shell "dpm set-device-owner com.flx_apps.distraquit/.system_integration.DistraQuitDeviceAdminReceiver"

echo Starting App
%cd%\platform-tools\adb.exe shell monkey -p com.flx_apps.distraquit 1

echo Done.
pause

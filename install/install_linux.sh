#!/bin/sh

if [ ! -f "platform-tools/adb" ]; then
    echo "Downloading and unzipping Android Platform Tools. Please wait..."
    wget -O platform-tools-latest-linux.zip https://dl.google.com/android/repository/platform-tools-latest-linux.zip
    unzip platform-tools-latest-linux.zip 
fi

if [ ! -f "distraquit-latest.apk" ]; then
    echo "Downloading latest DistraQuit APK"
    wget -O distraquit-latest.apk https://github.com/flxapps/DistraQuit/releases/latest/download/app-release.apk
fi

echo "Installing Detox Droid on your device"
platform-tools/adb install -r -t distraquit-latest.apk

echo "Granting Permissions"
platform-tools/adb shell pm grant com.flx_apps.distraquit android.permission.WRITE_SECURE_SETTINGS
platform-tools/adb shell "dpm set-device-owner com.flx_apps.distraquit/.system_integration.DistraQuitDeviceAdminReceiver"

echo "Starting App"
platform-tools/adb shell monkey -p com.flx_apps.distraquit 1

echo "Done."

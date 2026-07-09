@echo off
setlocal

set "ROOT=%~dp0"
set "GRADLEW=%ROOT%gradlew.bat"
set "ADB=C:\Users\82285\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set "APK=%ROOT%androidApp\build\outputs\apk\debug\androidApp-debug.apk"
set "PKG=com.leejlredstar.redefinencm.kmp"

if not exist "%ADB%" set "ADB=adb"
if not exist "%GRADLEW%" set "GRADLEW=gradlew.bat"

echo [1/5] Building debug APK...
pushd "%ROOT%"
call "%GRADLEW%" :androidApp:assembleDebug
set "BUILD_EXIT=%ERRORLEVEL%"
popd
if not "%BUILD_EXIT%"=="0" (
    echo Debug build failed.
    pause
    exit /b %BUILD_EXIT%
)

echo [2/5] Waiting for device (USB debugging must be enabled)...
"%ADB%" wait-for-device

echo [3/5] Installing %APK%
"%ADB%" install -r -d "%APK%"
if errorlevel 1 (
    echo Install failed. If it reports a signature conflict, run: adb uninstall %PKG%
    pause
    exit /b 1
)

echo [4/5] Launching app...
"%ADB%" shell am start -n %PKG%/.MainActivity

echo [5/5] Streaming logcat for %PKG% (Ctrl+C to stop)...
rem PID lookup runs inside the device shell. logcat is filtered to the
rem currently running process for the current application package name.
"%ADB%" logcat -c
"%ADB%" shell "while ! pidof -s %PKG% >/dev/null 2>&1; do sleep 1; done; logcat -v color --pid=$(pidof -s %PKG%)"

pause

# Verify the app's *packaged APK* instead of only the source manifest.
# Example:
# powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\check-wear-apk.ps1
param([string]$SdkRoot = "D:\AndroidSDK")

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$manifestPath = Join-Path $root "app\src\main\AndroidManifest.xml"
$apkPath = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$androidUri = "http://schemas.android.com/apk/res/android"

if (!(Test-Path $manifestPath)) { throw "AndroidManifest.xml not found: $manifestPath" }
[xml]$doc = Get-Content -Path $manifestPath -Raw -Encoding UTF8
$ns = New-Object System.Xml.XmlNamespaceManager($doc.NameTable)
$ns.AddNamespace("android", $androidUri)
$feature = $doc.SelectSingleNode("/manifest/uses-feature[@android:name='android.hardware.type.watch']", $ns)
if ($null -eq $feature) { throw "Source manifest has no required watch feature." }
if ($feature.GetAttribute("required", $androidUri) -eq "false") {
    throw "Watch feature is declared optional; it must be required."
}
Write-Host "[OK] Source Manifest requires android.hardware.type.watch"

if (!(Test-Path $apkPath)) {
    throw "No debug APK: $apkPath. Build > Build APK(s) in Android Studio first."
}
if (!(Test-Path $SdkRoot)) {
    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) { $SdkRoot = $env:ANDROID_HOME }
    elseif ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) { $SdkRoot = $env:ANDROID_SDK_ROOT }
    else { throw "SDK not found. Pass -SdkRoot D:\AndroidSDK or your actual SDK path." }
}
$aapt = Get-ChildItem -Path (Join-Path $SdkRoot "build-tools") -Recurse -Filter "aapt.exe" -File -ErrorAction Stop |
    Sort-Object FullName -Descending | Select-Object -First 1
if ($null -eq $aapt) { throw "aapt.exe missing. Install Android SDK Build-Tools." }
$badging = & $aapt.FullName dump badging $apkPath
if ($LASTEXITCODE -ne 0) { throw "aapt could not read the debug APK." }

if (@($badging | Where-Object { $_ -match "^uses-feature: name='android\.hardware\.type\.watch'" }).Count -eq 0) {
    throw "FAIL: packaged APK does not require android.hardware.type.watch. Check Merged Manifest."
}
if (@($badging | Where-Object { $_ -match "^launchable-activity: name='com\.eastsea\.badahannun\.MainActivity'" }).Count -eq 0) {
    throw "FAIL: debug APK launcher activity missing."
}
Write-Host "[OK] The packaged APK requires Wear OS and has a launcher Activity."
Write-Host "[INFO] If Android Studio still says non-watch APK, select the shared Wear OS - 바다한눈 Run configuration and resync."

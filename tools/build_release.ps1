# Build & Package Script for Orbital Android
# Usage:
#   .\tools\build_release.ps1 -Type release
#   .\tools\build_release.ps1 -Type debug
#   .\tools\build_release.ps1 -Type bundle  (for Google Play Store AAB)

param (
    [ValidateSet("release", "debug", "bundle")]
    [string]$Type = "release"
)

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " 🚀 Building Orbital Android ($Type) " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

if ($Type -eq "release") {
    Write-Host "Compiling Release APK..." -ForegroundColor Yellow
    .\gradlew.bat assembleRelease
    
    $apkPath = "app\build\outputs\apk\release\app-release-unsigned.apk"
    if (-not (Test-Path $apkPath)) {
        $apkPath = "app\build\outputs\apk\release\app-release.apk"
    }

    if (Test-Path $apkPath) {
        $item = Get-Item $apkPath
        $sha = (Get-FileHash $apkPath -Algorithm SHA256).Hash
        Write-Host "`n✅ Build Successful!" -ForegroundColor Green
        Write-Host "APK Location: $($item.FullName)" -ForegroundColor White
        Write-Host "File Size   : $($item.Length) bytes ($([math]::Round($item.Length / 1MB, 2)) MB)" -ForegroundColor White
        Write-Host "SHA256 Hash : $sha" -ForegroundColor White
        Write-Host "`nNext Step: Upload this APK to your GitHub Release tag (https://github.com/jaypal1046/Orbital/releases)" -ForegroundColor Yellow
    }
} elseif ($Type -eq "debug") {
    Write-Host "Compiling Debug APK..." -ForegroundColor Yellow
    .\gradlew.bat assembleDebug
    $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path $apkPath) {
        Write-Host "`n✅ Debug APK Ready: $apkPath" -ForegroundColor Green
    }
} elseif ($Type -eq "bundle") {
    Write-Host "Compiling Google Play Store Bundle (AAB)..." -ForegroundColor Yellow
    .\gradlew.bat bundleRelease
    $aabPath = "app\build\outputs\bundle\release\app-release.aab"
    if (Test-Path $aabPath) {
        Write-Host "`n✅ Play Store AAB Ready: $aabPath" -ForegroundColor Green
    }
}

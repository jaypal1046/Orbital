# Shorebird-Style Instant Hot-Patch Publisher for Orbital
# Usage:
#   .\tools\publish_patch.ps1 -Description "Updated routing rules and prompt"
#   .\tools\publish_patch.ps1 -Description "Hotfix for model auto-router" -Push

param (
    [string]$Description = "Instant configuration & prompt update",
    [switch]$Push = $false
)

$versionJsonPath = "version.json"
$otaConfigPath = "ota\ota_config.json"

if (-not (Test-Path $versionJsonPath)) {
    Write-Error "version.json not found in current directory!"
    exit 1
}

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " ⚡ Orbital Instant OTA Hot-Patch Creator" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Read version.json
$versionData = Get-Content $versionJsonPath -Raw | ConvertFrom-Json
$currentPatch = 0
if ($versionData.otaPatch -and $versionData.otaPatch.patchVersion) {
    $currentPatch = [int]$versionData.otaPatch.patchVersion
}

$nextPatch = $currentPatch + 1
Write-Host "Bumping OTA Patch: #$currentPatch -> #$nextPatch" -ForegroundColor Yellow

# Update ota_config.json
if (Test-Path $otaConfigPath) {
    $otaData = Get-Content $otaConfigPath -Raw | ConvertFrom-Json
    $otaData.patchVersion = $nextPatch
    $otaData.description = $Description
    $otaData.timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $otaData | ConvertTo-Json -Depth 10 | Set-Content $otaConfigPath
    Write-Host "✅ Updated $otaConfigPath (Patch #$nextPatch)" -ForegroundColor Green
}

# Update version.json
$versionData.otaPatch.patchVersion = $nextPatch
$versionData.otaPatch.patchDescription = $Description
$versionData | ConvertTo-Json -Depth 10 | Set-Content $versionJsonPath
Write-Host "✅ Updated $versionJsonPath (Patch #$nextPatch)" -ForegroundColor Green

Write-Host "`n📝 Patch #$nextPatch is ready!" -ForegroundColor Cyan
Write-Host "Description: $Description" -ForegroundColor White

if ($Push) {
    Write-Host "`nCommitting and pushing to GitHub..." -ForegroundColor Yellow
    git add version.json ota\ota_config.json
    git commit -m "chore(ota): bump instant hot-patch to #$nextPatch - $Description"
    git push origin main
    Write-Host "🚀 Hot-patch #$nextPatch deployed live to GitHub! All active devices will sync automatically." -ForegroundColor Green
} else {
    Write-Host "`nTo publish this patch to GitHub, run:" -ForegroundColor Yellow
    Write-Host "git add version.json ota/ota_config.json" -ForegroundColor White
    Write-Host "git commit -m `"chore(ota): bump patch to #$nextPatch`"" -ForegroundColor White
    Write-Host "git push origin main" -ForegroundColor White
}

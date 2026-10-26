# Push favourites lineup assets (lineup.json, character PNGs, scenes/*.jpg) to the launcher on the TV.
#
# Usage: tools\push_favourites.ps1 [-TvHost 192.168.1.50] [-AssetsDir tv-assets\export]
param(
    [string]$AssetsDir = "tv-assets\export",
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost

# Resolve AssetsDir relative to repo root or current location
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not (Test-Path $AssetsDir)) {
    $AssetsDir = Join-Path $repoRoot $AssetsDir
}
if (-not (Test-Path $AssetsDir)) { throw "assets directory not found: $AssetsDir" }
$AssetsDir = (Resolve-Path $AssetsDir).Path

Set-Location $PSScriptRoot
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'com.gathin.tvhome'
$dir = "/sdcard/Android/data/$pkg/files"
$uri = "content://$pkg.theme"
$tmp = '/data/local/tmp/cyt-fav'

function Stage($name) { "$tmp-$($name -replace '/', '-')" }
function Put($name) {
    $t = Stage $name
    "mkdir -p `$(dirname $dir/$name) 2>/dev/null; rm -f $dir/$name 2>/dev/null; { o=`$(content write --uri $uri/$name < $t 2>&1) && case `"`$o`" in *'Unsupported operation'*) false ;; esac; } || cp $t $dir/$name 2>/dev/null; rm -f $t"
}

Write-Host "Creating remote directories..."
& $py -I tv.py "mkdir -p $dir/favourites/scenes $dir/favourites/posters /data/local/tmp 2>/dev/null" | Out-Null

# 1. Push lineup.json and titles.json
$lineupJson = Join-Path $AssetsDir "lineup.json"
if (Test-Path $lineupJson) {
    Write-Host "Pushing lineup.json..."
    & $py -I tv.py --push $lineupJson (Stage "favourites/lineup.json") | Out-Null
    & $py -I tv.py (Put "favourites/lineup.json") | Out-Null
} else {
    throw "no lineup.json found in $AssetsDir"
}

$titlesJson = Join-Path $AssetsDir "titles.json"
if (Test-Path $titlesJson) {
    Write-Host "Pushing titles.json..."
    & $py -I tv.py --push $titlesJson (Stage "favourites/titles.json") | Out-Null
    & $py -I tv.py (Put "favourites/titles.json") | Out-Null
}

# 2. Push character PNGs
Get-ChildItem $AssetsDir -Filter "*.png" | ForEach-Object {
    Write-Host "Pushing character $($_.Name)..."
    & $py -I tv.py --push $_.FullName (Stage "favourites/$($_.Name)") | Out-Null
    & $py -I tv.py (Put "favourites/$($_.Name)") | Out-Null
}

# 3. Push scene JPGs
$scenesDir = Join-Path $AssetsDir "scenes"
if (Test-Path $scenesDir -PathType Container) {
    Get-ChildItem $scenesDir -Filter "*.jpg" | ForEach-Object {
        Write-Host "Pushing scene $($_.Name)..."
        & $py -I tv.py --push $_.FullName (Stage "favourites/scenes/$($_.Name)") | Out-Null
        & $py -I tv.py (Put "favourites/scenes/$($_.Name)") | Out-Null
    }
}

# 4. Push posters
$postersDir = Join-Path $repoRoot "tv-assets\posters"
if (Test-Path $postersDir -PathType Container) {
    Get-ChildItem $postersDir -Filter "*.jpg" | ForEach-Object {
        Write-Host "Pushing poster $($_.Name)..."
        & $py -I tv.py --push $_.FullName (Stage "favourites/posters/$($_.Name)") | Out-Null
        & $py -I tv.py (Put "favourites/posters/$($_.Name)") | Out-Null
    }
}

# 5. Push my-favourites.psd
$psdFile = Join-Path $repoRoot "tv-assets\my-favourites.psd"
if (Test-Path $psdFile) {
    Write-Host "Pushing my-favourites.psd..."
    & $py -I tv.py --push $psdFile (Stage "favourites/my-favourites.psd") | Out-Null
    & $py -I tv.py (Put "favourites/my-favourites.psd") | Out-Null
}

Write-Host "All favourites assets pushed successfully to $dir/favourites/"

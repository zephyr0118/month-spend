$ErrorActionPreference = 'Stop'

function Write-Section {
    param([string]$Text)
    Write-Host ''
    Write-Host ('=' * 68) -ForegroundColor DarkCyan
    Write-Host ('  ' + $Text) -ForegroundColor Cyan
    Write-Host ('=' * 68) -ForegroundColor DarkCyan
}

function Test-PrivateIPv4 {
    param([string]$Address)

    try {
        $bytes = ([System.Net.IPAddress]::Parse($Address)).GetAddressBytes()
    }
    catch {
        return $false
    }

    if ($bytes.Count -ne 4) { return $false }
    if ($bytes[0] -eq 10) { return $true }
    if (($bytes[0] -eq 172) -and ($bytes[1] -ge 16) -and ($bytes[1] -le 31)) { return $true }
    if (($bytes[0] -eq 192) -and ($bytes[1] -eq 168)) { return $true }
    return $false
}

function Get-BestLanIPv4 {
    $candidates = @()

    try {
        $configs = Get-NetIPConfiguration -ErrorAction Stop | Where-Object {
            ($null -ne $_.NetAdapter) -and ($_.NetAdapter.Status -eq 'Up') -and ($null -ne $_.IPv4Address)
        }

        foreach ($config in $configs) {
            foreach ($address in @($config.IPv4Address)) {
                $ip = [string]$address.IPAddress
                if (-not (Test-PrivateIPv4 -Address $ip)) { continue }

                $alias = [string]$config.InterfaceAlias
                $score = 0

                if ($null -ne $config.IPv4DefaultGateway) { $score += 100 }
                if ($alias -match 'Wi-Fi|WiFi|WLAN') {
                    $score += 80
                }
                elseif ($alias -match 'Ethernet') {
                    $score += 50
                }

                if ($alias -match 'vEthernet|WSL|VMware|VirtualBox|Hyper-V|Tailscale|ZeroTier|VPN|Clash') {
                    $score -= 150
                }

                $candidates += New-Object PSObject -Property @{
                    Address = $ip
                    Alias = $alias
                    Score = $score
                }
            }
        }
    }
    catch {
        # Fall through to the .NET based fallback below.
    }

    if ($candidates.Count -eq 0) {
        try {
            $addresses = [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName())
            foreach ($address in $addresses) {
                if ($address.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) { continue }

                $ip = $address.IPAddressToString
                if (Test-PrivateIPv4 -Address $ip) {
                    $candidates += New-Object PSObject -Property @{
                        Address = $ip
                        Alias = 'Local network'
                        Score = 0
                    }
                }
            }
        }
        catch {
        }
    }

    return @($candidates | Sort-Object -Property Score -Descending | Select-Object -First 1)
}

function Write-HttpHeader {
    param(
        [System.IO.Stream]$Stream,
        [string]$Status,
        [string]$ContentType,
        [long]$ContentLength,
        [string[]]$ExtraHeaders
    )

    if ($null -eq $ExtraHeaders) { $ExtraHeaders = @() }

    $header = 'HTTP/1.1 ' + $Status + "`r`n"
    $header += 'Content-Type: ' + $ContentType + "`r`n"
    $header += 'Content-Length: ' + $ContentLength + "`r`n"
    $header += "Cache-Control: no-store, no-cache, must-revalidate`r`n"
    $header += "Connection: close`r`n"

    foreach ($item in $ExtraHeaders) {
        $header += $item + "`r`n"
    }

    $header += "`r`n"
    $bytes = [System.Text.Encoding]::ASCII.GetBytes($header)
    $Stream.Write($bytes, 0, $bytes.Length)
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot 'gradlew.bat'
$sourceApk = Join-Path $repoRoot 'app\build\outputs\apk\daily\app-daily.apk'
$distDir = Join-Path $repoRoot 'dist'
$distApk = Join-Path $distDir 'yueji-latest.apk'
$buildGradle = Join-Path $repoRoot 'app\build.gradle.kts'

Write-Section 'YueJi: build + install + LAN download'
Write-Host ('Project: ' + $repoRoot)

if (-not (Test-Path -LiteralPath $gradle)) {
    throw ('gradlew.bat not found: ' + $gradle)
}

Write-Section 'Step 1/3 - Build latest Daily APK'
Write-Host 'Running :app:clean :app:assembleDaily ...'

Push-Location $repoRoot
try {
    & $gradle ':app:clean' ':app:assembleDaily'
    if ($LASTEXITCODE -ne 0) {
        throw ('Gradle failed with exit code ' + $LASTEXITCODE)
    }
}
finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $sourceApk)) {
    throw ('APK not found after build: ' + $sourceApk)
}

if (-not (Test-Path -LiteralPath $distDir)) {
    New-Item -ItemType Directory -Path $distDir | Out-Null
}

Copy-Item -LiteralPath $sourceApk -Destination $distApk -Force

$versionName = 'unknown'
$versionCode = 'unknown'
if (Test-Path -LiteralPath $buildGradle) {
    $gradleText = [System.IO.File]::ReadAllText($buildGradle, [System.Text.Encoding]::UTF8)

    $nameMatch = [System.Text.RegularExpressions.Regex]::Match($gradleText, 'versionName\s*=\s*"([^"]+)"')
    if ($nameMatch.Success) { $versionName = $nameMatch.Groups[1].Value }

    $codeMatch = [System.Text.RegularExpressions.Regex]::Match($gradleText, 'versionCode\s*=\s*(\d+)')
    if ($codeMatch.Success) { $versionCode = $codeMatch.Groups[1].Value }
}

$fileInfo = Get-Item -LiteralPath $distApk
$fileSizeMb = [Math]::Round(($fileInfo.Length / 1MB), 2)
$sha256 = (Get-FileHash -LiteralPath $distApk -Algorithm SHA256).Hash
$buildTime = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'

Write-Host ''
Write-Host 'Build succeeded.' -ForegroundColor Green
Write-Host ('Version: ' + $versionName + ' (versionCode ' + $versionCode + ')')
Write-Host ('APK: ' + $distApk)
Write-Host ('Size: ' + $fileSizeMb + ' MB')
Write-Host ('SHA-256: ' + $sha256)

Write-Section 'Step 2/3 - Try ADB overwrite install'
$adbCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
if ($null -eq $adbCommand) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
}

if ($null -ne $adbCommand) {
    $deviceLines = @(& $adbCommand.Source devices 2>$null)
    $devices = @()

    foreach ($line in $deviceLines) {
        $match = [System.Text.RegularExpressions.Regex]::Match([string]$line, '^([^\s]+)\s+device$')
        if ($match.Success) {
            $devices += $match.Groups[1].Value
        }
    }

    if ($devices.Count -eq 1) {
        Write-Host ('ADB device: ' + $devices[0])
        Write-Host 'Installing with adb install -r ...'
        & $adbCommand.Source '-s' $devices[0] 'install' '-r' $distApk

        if ($LASTEXITCODE -eq 0) {
            Write-Host 'ADB overwrite install succeeded.' -ForegroundColor Green
        }
        else {
            Write-Host 'ADB install failed. LAN download will still be available.' -ForegroundColor Yellow
        }
    }
    elseif ($devices.Count -gt 1) {
        Write-Host ('Multiple ADB devices found (' + $devices.Count + '). Automatic install skipped.') -ForegroundColor Yellow
    }
    else {
        Write-Host 'No authorized ADB device found. Automatic install skipped.' -ForegroundColor DarkGray
    }
}
else {
    Write-Host 'ADB is not in PATH. Automatic install skipped.' -ForegroundColor DarkGray
}

Write-Section 'Step 3/3 - Start LAN download server'
$lanList = @(Get-BestLanIPv4)
if ($lanList.Count -eq 0) {
    throw 'No usable private IPv4 address found. Connect this PC to Wi-Fi or Ethernet first.'
}
$lan = $lanList[0]

$listener = $null
$port = 8765
while (($port -le 8785) -and ($null -eq $listener)) {
    try {
        $candidate = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Any, $port)
        $candidate.Start()
        $listener = $candidate
    }
    catch [System.Net.Sockets.SocketException] {
        $port++
    }
}

if ($null -eq $listener) {
    throw 'Ports 8765-8785 are all unavailable.'
}

$url = 'http://' + $lan.Address + ':' + $port + '/'
$apkUrl = 'http://' + $lan.Address + ':' + $port + '/yueji-latest.apk'

$html = '<!doctype html>' +
'<html><head><meta charset="utf-8">' +
'<meta name="viewport" content="width=device-width,initial-scale=1">' +
'<title>YueJi APK</title>' +
'<style>' +
'body{font-family:system-ui,-apple-system,Segoe UI,Arial,sans-serif;margin:0;background:#f6f3ed;color:#2b2a27}' +
'main{max-width:680px;margin:0 auto;padding:32px 20px}' +
'.card{background:#fff;border-radius:20px;padding:24px;box-shadow:0 8px 30px rgba(0,0,0,.08)}' +
'.meta{color:#6f6a61;line-height:1.8}' +
'.btn{display:block;margin:24px 0 12px;padding:16px;text-align:center;background:#8a641f;color:#fff;text-decoration:none;border-radius:14px;font-weight:700;font-size:18px}' +
'.tip{font-size:14px;line-height:1.7;color:#6f6a61}' +
'.hash{word-break:break-all;font-family:monospace;font-size:12px;background:#f5f5f5;padding:10px;border-radius:10px}' +
'</style></head><body><main><div class="card">' +
'<h1>YueJi latest Daily APK</h1>' +
'<div class="meta">Version: ' + $versionName + ' (versionCode ' + $versionCode + ')<br>' +
'Build time: ' + $buildTime + '<br>File size: ' + $fileSizeMb + ' MB</div>' +
'<a class="btn" href="/yueji-latest.apk">Download latest APK</a>' +
'<p class="tip">Keep the phone and PC on the same Wi-Fi/LAN. Install this APK over the existing Daily build. Do not uninstall the old app first if you want to keep local app data.</p>' +
'<div class="hash">SHA-256: ' + $sha256 + '</div>' +
'</div></main></body></html>'

$htmlBytes = [System.Text.Encoding]::UTF8.GetBytes($html)

Write-Host ('Network adapter: ' + $lan.Alias)
Write-Host ''
Write-Host 'Open this address on your phone:' -ForegroundColor Cyan
Write-Host $url -ForegroundColor Green
Write-Host ''
Write-Host 'Direct APK URL:' -ForegroundColor Cyan
Write-Host $apkUrl -ForegroundColor Green
Write-Host ''
Write-Host 'Keep this window open while downloading.' -ForegroundColor Yellow
Write-Host 'If Windows Firewall asks, allow access on Private networks.' -ForegroundColor Yellow
Write-Host 'Press Ctrl+C to stop the server.' -ForegroundColor Yellow
Write-Host ''

try {
    while ($true) {
        $client = $listener.AcceptTcpClient()
        try {
            $stream = $client.GetStream()
            $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::ASCII, $false, 1024, $true)
            $requestLine = $reader.ReadLine()

            if ([string]::IsNullOrWhiteSpace($requestLine)) {
                continue
            }

            while ($true) {
                $headerLine = $reader.ReadLine()
                if ([string]::IsNullOrEmpty($headerLine)) { break }
            }

            $parts = $requestLine.Split(' ')
            if ($parts.Count -lt 2) { continue }

            $method = $parts[0].ToUpperInvariant()
            $target = ($parts[1] -split '\?')[0]
            $path = [System.Uri]::UnescapeDataString($target)
            $remote = [string]$client.Client.RemoteEndPoint

            if (($method -ne 'GET') -and ($method -ne 'HEAD')) {
                $body = [System.Text.Encoding]::UTF8.GetBytes('Only GET and HEAD are supported.')
                Write-HttpHeader -Stream $stream -Status '405 Method Not Allowed' -ContentType 'text/plain; charset=utf-8' -ContentLength $body.Length
                if ($method -ne 'HEAD') { $stream.Write($body, 0, $body.Length) }
            }
            elseif (($path -eq '/') -or ($path -eq '/index.html')) {
                Write-Host ('[' + (Get-Date -Format 'HH:mm:ss') + '] ' + $remote + ' opened download page')
                Write-HttpHeader -Stream $stream -Status '200 OK' -ContentType 'text/html; charset=utf-8' -ContentLength $htmlBytes.Length
                if ($method -ne 'HEAD') { $stream.Write($htmlBytes, 0, $htmlBytes.Length) }
            }
            elseif (($path -eq '/yueji-latest.apk') -or ($path -eq '/app.apk')) {
                Write-Host ('[' + (Get-Date -Format 'HH:mm:ss') + '] ' + $remote + ' downloading APK') -ForegroundColor Cyan
                $apkLength = (Get-Item -LiteralPath $distApk).Length
                $extra = @('Content-Disposition: attachment; filename="yueji-latest.apk"')
                Write-HttpHeader -Stream $stream -Status '200 OK' -ContentType 'application/vnd.android.package-archive' -ContentLength $apkLength -ExtraHeaders $extra

                if ($method -ne 'HEAD') {
                    $fileStream = [System.IO.File]::OpenRead($distApk)
                    try {
                        $fileStream.CopyTo($stream)
                    }
                    finally {
                        $fileStream.Dispose()
                    }
                }
            }
            elseif ($path -eq '/favicon.ico') {
                Write-HttpHeader -Stream $stream -Status '204 No Content' -ContentType 'image/x-icon' -ContentLength 0
            }
            else {
                $body = [System.Text.Encoding]::UTF8.GetBytes('404 Not Found')
                Write-HttpHeader -Stream $stream -Status '404 Not Found' -ContentType 'text/plain; charset=utf-8' -ContentLength $body.Length
                if ($method -ne 'HEAD') { $stream.Write($body, 0, $body.Length) }
            }

            $stream.Flush()
        }
        catch {
            Write-Host ('Request error: ' + $_.Exception.Message) -ForegroundColor DarkYellow
        }
        finally {
            $client.Close()
        }
    }
}
finally {
    if ($null -ne $listener) { $listener.Stop() }
    Write-Host ''
    Write-Host 'LAN download server stopped.'
}

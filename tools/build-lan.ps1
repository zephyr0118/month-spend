$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function Write-Title([string]$Text) {
    Write-Host ''
    Write-Host ('=' * 68) -ForegroundColor DarkCyan
    Write-Host ('  ' + $Text) -ForegroundColor Cyan
    Write-Host ('=' * 68) -ForegroundColor DarkCyan
}

function Test-PrivateIPv4([string]$Address) {
    try {
        $bytes = ([System.Net.IPAddress]::Parse($Address)).GetAddressBytes()
    } catch {
        return $false
    }

    if ($bytes.Count -ne 4) { return $false }
    if ($bytes[0] -eq 10) { return $true }
    if ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) { return $true }
    if ($bytes[0] -eq 192 -and $bytes[1] -eq 168) { return $true }
    return $false
}

function Get-BestLanIPv4 {
    $candidates = @()

    try {
        $configs = Get-NetIPConfiguration -ErrorAction Stop | Where-Object {
            $_.NetAdapter -and $_.NetAdapter.Status -eq 'Up' -and $_.IPv4Address
        }

        foreach ($config in $configs) {
            foreach ($address in @($config.IPv4Address)) {
                $ip = $address.IPAddress
                if (-not (Test-PrivateIPv4 $ip)) { continue }

                $alias = [string]$config.InterfaceAlias
                $score = 0
                if ($config.IPv4DefaultGateway) { $score += 100 }
                if ($alias -match 'Wi-?Fi|WLAN|无线') { $score += 80 }
                elseif ($alias -match 'Ethernet|以太网') { $score += 50 }
                if ($alias -match 'vEthernet|WSL|VMware|VirtualBox|Hyper-V|Tailscale|ZeroTier|VPN|Clash|虚拟') { $score -= 150 }

                $candidates += [PSCustomObject]@{
                    Address = $ip
                    Alias = $alias
                    Score = $score
                }
            }
        }
    } catch {
        # 某些精简系统可能没有 NetTCPIP 模块，下面使用 .NET 兜底。
    }

    if ($candidates.Count -eq 0) {
        try {
            $addresses = [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName())
            foreach ($address in $addresses) {
                if ($address.AddressFamily -eq [System.Net.Sockets.AddressFamily]::InterNetwork) {
                    $ip = $address.IPAddressToString
                    if (Test-PrivateIPv4 $ip) {
                        $candidates += [PSCustomObject]@{
                            Address = $ip
                            Alias = '本机网络'
                            Score = 0
                        }
                    }
                }
            }
        } catch {
        }
    }

    return $candidates | Sort-Object Score -Descending | Select-Object -First 1
}

function Write-HttpHeader {
    param(
        [System.IO.Stream]$Stream,
        [string]$Status,
        [string]$ContentType,
        [long]$ContentLength,
        [string[]]$ExtraHeaders = @()
    )

    $header = "HTTP/1.1 $Status`r`n"
    $header += "Content-Type: $ContentType`r`n"
    $header += "Content-Length: $ContentLength`r`n"
    $header += "Cache-Control: no-store, no-cache, must-revalidate`r`n"
    $header += "Connection: close`r`n"
    foreach ($item in $ExtraHeaders) {
        $header += "$item`r`n"
    }
    $header += "`r`n"

    $headerBytes = [System.Text.Encoding]::ASCII.GetBytes($header)
    $Stream.Write($headerBytes, 0, $headerBytes.Length)
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot 'gradlew.bat'
$sourceApk = Join-Path $repoRoot 'app\build\outputs\apk\daily\app-daily.apk'
$distDir = Join-Path $repoRoot 'dist'
$distApk = Join-Path $distDir 'yueji-latest.apk'
$buildGradle = Join-Path $repoRoot 'app\build.gradle.kts'

Write-Title '月迹：一键重新打包 + 覆盖安装 + 局域网下载'
Write-Host "项目目录：$repoRoot"

if (-not (Test-Path $gradle)) {
    throw "找不到 gradlew.bat：$gradle"
}

Write-Title '第 1 步：重新打包最新 Daily APK'
Write-Host '正在执行干净构建：:app:clean :app:assembleDaily'
Write-Host 'Daily 版本沿用 com.yueji.finance.debug 包名与调试签名，可覆盖现有日用版并保留数据。'
Write-Host ''

Push-Location $repoRoot
try {
    & $gradle ':app:clean' ':app:assembleDaily'
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle 构建失败，退出码：$LASTEXITCODE"
    }
} finally {
    Pop-Location
}

if (-not (Test-Path $sourceApk)) {
    throw "构建完成但未找到 APK：$sourceApk"
}

if (-not (Test-Path $distDir)) {
    New-Item -ItemType Directory -Path $distDir | Out-Null
}
Copy-Item $sourceApk $distApk -Force

$versionName = '未知'
$versionCode = '未知'
if (Test-Path $buildGradle) {
    $gradleText = Get-Content $buildGradle -Raw -Encoding UTF8
    if ($gradleText -match 'versionName\s*=\s*"([^"]+)"') { $versionName = $Matches[1] }
    if ($gradleText -match 'versionCode\s*=\s*(\d+)') { $versionCode = $Matches[1] }
}

$fileInfo = Get-Item $distApk
$fileSizeMb = [Math]::Round($fileInfo.Length / 1MB, 2)
$sha256 = (Get-FileHash $distApk -Algorithm SHA256).Hash
$buildTime = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'

Write-Host ''
Write-Host '打包成功。' -ForegroundColor Green
Write-Host "版本：$versionName（versionCode $versionCode）"
Write-Host "文件：$distApk"
Write-Host "大小：$fileSizeMb MB"
Write-Host "SHA-256：$sha256"

Write-Title '第 2 步：检测 ADB 覆盖安装'
$adb = Get-Command adb -ErrorAction SilentlyContinue
if ($adb) {
    $devices = @(
        & adb devices 2>$null |
            Select-Object -Skip 1 |
            ForEach-Object {
                if ($_ -match '^([^\s]+)\s+device$') { $Matches[1] }
            }
    )

    if ($devices.Count -eq 1) {
        Write-Host "检测到设备：$($devices[0])"
        Write-Host '正在执行 adb install -r 覆盖安装……'
        & adb -s $devices[0] install -r $distApk
        if ($LASTEXITCODE -eq 0) {
            Write-Host 'ADB 覆盖安装成功，原应用数据会保留。' -ForegroundColor Green
        } else {
            Write-Host 'ADB 覆盖安装未成功，但 APK 已正常生成，仍可使用下面的局域网地址下载安装。' -ForegroundColor Yellow
        }
    } elseif ($devices.Count -gt 1) {
        Write-Host "检测到 $($devices.Count) 台 ADB 设备，为避免装错设备，本次跳过自动安装。" -ForegroundColor Yellow
    } else {
        Write-Host '未检测到已授权的 ADB 设备，跳过电脑直装。' -ForegroundColor DarkGray
    }
} else {
    Write-Host '系统 PATH 中没有 adb，跳过电脑直装；不影响局域网下载。' -ForegroundColor DarkGray
}

Write-Title '第 3 步：启动局域网下载地址'
$lan = Get-BestLanIPv4
if (-not $lan) {
    throw '没有检测到可用的局域网 IPv4 地址。请确认电脑已连接 Wi-Fi 或有线局域网。'
}

$listener = $null
$port = 8765
while ($port -le 8785) {
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $port)
        $listener.Start()
        break
    } catch [System.Net.Sockets.SocketException] {
        $listener = $null
        $port++
    }
}

if (-not $listener) {
    throw '8765—8785 端口均被占用，无法启动局域网下载服务。'
}

$url = "http://$($lan.Address):$port/"
$apkUrl = "http://$($lan.Address):$port/yueji-latest.apk"

$html = @"
<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>月迹 APK 下载</title>
<style>
body{font-family:system-ui,-apple-system,"Microsoft YaHei",sans-serif;margin:0;background:#f6f3ed;color:#2b2a27}
main{max-width:680px;margin:0 auto;padding:32px 20px}
.card{background:#fff;border-radius:20px;padding:24px;box-shadow:0 8px 30px rgba(0,0,0,.08)}
h1{margin-top:0}.meta{color:#6f6a61;line-height:1.8}.btn{display:block;margin:24px 0 12px;padding:16px;text-align:center;background:#8a641f;color:#fff;text-decoration:none;border-radius:14px;font-weight:700;font-size:18px}.tip{font-size:14px;line-height:1.7;color:#6f6a61}.hash{word-break:break-all;font-family:monospace;font-size:12px;background:#f5f5f5;padding:10px;border-radius:10px}
</style>
</head>
<body><main><div class="card">
<h1>月迹最新日用版</h1>
<div class="meta">版本：$versionName（versionCode $versionCode）<br>构建时间：$buildTime<br>文件大小：$fileSizeMb MB</div>
<a class="btn" href="/yueji-latest.apk">下载最新 APK</a>
<p class="tip">手机与电脑处在同一个 Wi-Fi / 局域网时可直接下载。下载后点击 APK 并选择更新/安装即可覆盖现有 Daily 版本。请不要先卸载旧版，否则 Android 会删除应用私有数据。</p>
<p class="tip">如果手机提示禁止安装未知应用，请只为当前浏览器临时允许“安装未知应用”。</p>
<div class="hash">SHA-256：$sha256</div>
</div></main></body>
</html>
"@
$htmlBytes = [System.Text.Encoding]::UTF8.GetBytes($html)

Write-Host "网络接口：$($lan.Alias)"
Write-Host ''
Write-Host '手机请打开这个地址：' -ForegroundColor Cyan
Write-Host $url -ForegroundColor Green
Write-Host ''
Write-Host 'APK 直链：' -ForegroundColor Cyan
Write-Host $apkUrl -ForegroundColor Green
Write-Host ''
Write-Host '注意：' -ForegroundColor Yellow
Write-Host '1. 手机和电脑必须处在同一个 Wi-Fi / 局域网。'
Write-Host '2. 第一次运行若 Windows 防火墙弹窗，请允许“专用网络”访问。'
Write-Host '3. 保持本窗口打开，下载地址才会持续可用。'
Write-Host '4. 按 Ctrl+C 可以停止下载服务。'
Write-Host ''
Write-Host '局域网下载服务已启动。' -ForegroundColor Green

try {
    while ($true) {
        $client = $listener.AcceptTcpClient()
        try {
            $stream = $client.GetStream()
            $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::ASCII, $false, 1024, $true)
            $requestLine = $reader.ReadLine()
            if ([string]::IsNullOrWhiteSpace($requestLine)) { continue }

            while ($true) {
                $line = $reader.ReadLine()
                if ([string]::IsNullOrEmpty($line)) { break }
            }

            $parts = $requestLine.Split(' ')
            if ($parts.Count -lt 2) { continue }
            $method = $parts[0].ToUpperInvariant()
            $target = ($parts[1] -split '\?')[0]
            $path = [System.Uri]::UnescapeDataString($target)
            $remote = $client.Client.RemoteEndPoint.ToString()

            if ($method -notin @('GET', 'HEAD')) {
                $body = [System.Text.Encoding]::UTF8.GetBytes('仅支持 GET/HEAD')
                Write-HttpHeader $stream '405 Method Not Allowed' 'text/plain; charset=utf-8' $body.Length
                if ($method -ne 'HEAD') { $stream.Write($body, 0, $body.Length) }
                continue
            }

            if ($path -eq '/' -or $path -eq '/index.html') {
                Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $remote 打开下载页"
                Write-HttpHeader $stream '200 OK' 'text/html; charset=utf-8' $htmlBytes.Length
                if ($method -ne 'HEAD') { $stream.Write($htmlBytes, 0, $htmlBytes.Length) }
            } elseif ($path -eq '/yueji-latest.apk' -or $path -eq '/app.apk') {
                Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $remote 下载 APK" -ForegroundColor Cyan
                $apkLength = (Get-Item $distApk).Length
                Write-HttpHeader $stream '200 OK' 'application/vnd.android.package-archive' $apkLength @('Content-Disposition: attachment; filename="yueji-latest.apk"')
                if ($method -ne 'HEAD') {
                    $file = [System.IO.File]::OpenRead($distApk)
                    try {
                        $file.CopyTo($stream)
                    } finally {
                        $file.Dispose()
                    }
                }
            } elseif ($path -eq '/favicon.ico') {
                Write-HttpHeader $stream '204 No Content' 'image/x-icon' 0
            } else {
                $body = [System.Text.Encoding]::UTF8.GetBytes('404：没有这个文件')
                Write-HttpHeader $stream '404 Not Found' 'text/plain; charset=utf-8' $body.Length
                if ($method -ne 'HEAD') { $stream.Write($body, 0, $body.Length) }
            }

            $stream.Flush()
        } catch {
            Write-Host "处理下载请求时出现错误：$($_.Exception.Message)" -ForegroundColor DarkYellow
        } finally {
            $client.Close()
        }
    }
} finally {
    if ($listener) { $listener.Stop() }
    Write-Host ''
    Write-Host '局域网下载服务已停止。'
}

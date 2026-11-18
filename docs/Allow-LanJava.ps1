param(
    [ValidateSet("Server", "Client", "Both")][string]$Role = "Both",
    [string]$JavaPath = ""
)
# Xem trước nội dung; chạy bằng PowerShell Administrator. Không tắt firewall.
$ErrorActionPreference = "Stop"
if (-not $JavaPath) {
    $settings = & java -XshowSettings:properties -version 2>&1 | Out-String
    if ($settings -notmatch 'java.home\s*=\s*([^\r\n]+)') { throw "Không tìm được Java. Truyền -JavaPath đến java.exe của JDK." }
    $JavaPath = Join-Path $Matches[1].Trim() "bin\java.exe"
}
if (-not (Test-Path $JavaPath)) { throw "Không tồn tại: $JavaPath" }
Write-Host "Allow Java: $JavaPath ; role: $Role ; Private / LocalSubnet only"
function Add-CinemaRule($Name, $Protocol, $Ports) {
    Get-NetFirewallRule -DisplayName $Name -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    New-NetFirewallRule -DisplayName $Name -Group "CinemaRMI Demo" -Direction Inbound -Action Allow -Program $JavaPath -Profile Private -RemoteAddress LocalSubnet -Protocol $Protocol -LocalPort $Ports | Out-Null
}
if ($Role -eq "Server" -or $Role -eq "Both") {
    Add-CinemaRule "CinemaRMI Server TCP" "TCP" @("1099","5000")
    Add-CinemaRule "CinemaRMI Discovery UDP" "UDP" "45888"
}
if ($Role -eq "Client" -or $Role -eq "Both") {
    Add-CinemaRule "CinemaRMI Client Callback" "TCP" "Any"
}
Write-Host "Done. Firewall vẫn bật. Chỉ áp dụng Java đã chọn trong LAN Private."
# Gỡ riêng rule của bài demo khi không cần:
# Get-NetFirewallRule -Group "CinemaRMI Demo" | Remove-NetFirewallRule

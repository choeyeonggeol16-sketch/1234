$ErrorActionPreference = 'Stop'
try {
    Add-Type -Path (Join-Path $PSScriptRoot 'PSS_Native_Path_Check.cs')
    $desktop = [Environment]::GetFolderPath('Desktop')
    if (-not $desktop) { $desktop = $PSScriptRoot }
    $folder = Join-Path $desktop ('PSS_Native_Path_' + (Get-Date -Format 'yyyyMMdd_HHmmss_fff'))
    $reports = [PssNativePathAudit]::Run($folder)
    ConvertTo-Json -InputObject $reports -Depth 6 | Set-Content -LiteralPath (Join-Path $folder 'report.json') -Encoding UTF8
    $zip = $folder + '.zip'
    Compress-Archive -LiteralPath $folder -DestinationPath $zip
    foreach ($report in $reports) { Write-Host ('PID=' + $report.Pid + ' ' + $report.Status) }
    Write-Host ('Report ZIP: ' + $zip) -ForegroundColor Green
} catch {
    Write-Host ('ERROR: ' + $_.Exception.GetBaseException().Message) -ForegroundColor Red
    exit 1
}

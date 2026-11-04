$ErrorActionPreference = 'Stop'
try {
    Add-Type -Path (Join-Path $PSScriptRoot 'PSS_Loaded_Behavior_Export.cs')
    $reports = [PssLoadedBehaviorExport]::Run()
    $desktop = [Environment]::GetFolderPath('Desktop')
    if (-not $desktop) { $desktop = $PSScriptRoot }
    $reportPath = Join-Path $desktop ('PSS_Loaded_Behavior_' + (Get-Date -Format 'yyyyMMdd_HHmmss_fff') + '.json')
    ConvertTo-Json -InputObject $reports -Depth 60 | Set-Content -LiteralPath $reportPath -Encoding UTF8
    foreach ($report in $reports) {
        Write-Host ('PID=' + $report.Pid + ' nodes=' + $report.NodesRead + ' ' + $report.Status)
    }
    Write-Host ('Report: ' + $reportPath) -ForegroundColor Green
} catch {
    Write-Host ('ERROR: ' + $_.Exception.GetBaseException().Message) -ForegroundColor Red
    exit 1
}

$ErrorActionPreference = 'Stop'
try {
    if (-not ('PssRoamingCodeCheck' -as [type])) {
        Add-Type -Path (Join-Path $PSScriptRoot 'PSS_Roaming_Code_Check.cs')
    }
    $outputRoot = [Environment]::GetFolderPath('Desktop')
    if (-not $outputRoot) { $outputRoot = $PSScriptRoot }
    $captureName = 'PSS_Roaming_Code_' + (Get-Date -Format 'yyyyMMdd_HHmmss_fff')
    $captureFolder = Join-Path $outputRoot $captureName
    $capturedClients = [PssRoamingCodeCheck]::Run($captureFolder)
    $captureZip = $captureFolder + '.zip'
    Compress-Archive -LiteralPath $captureFolder -DestinationPath $captureZip
    Write-Host ('Captured clients: ' + $capturedClients)
    Write-Host ('Report ZIP: ' + $captureZip) -ForegroundColor Green
} catch {
    Write-Host ('ERROR: ' + $_.Exception.GetBaseException().Message) -ForegroundColor Red
    exit 1
}

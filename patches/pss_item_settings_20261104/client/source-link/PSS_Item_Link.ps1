param([int]$TargetPid = 0, [int]$GamePort = 5000, [int]$WindowPort = 5001)
$ErrorActionPreference = 'Stop'
try {
    if (-not ('PssItemLink' -as [type])) {
        Add-Type -Path (Join-Path $PSScriptRoot 'PSS_Item_Link.cs')
    }
    [PssItemLink]::Run($TargetPid, $GamePort, $WindowPort)
} catch {
    Write-Host ('ERROR: ' + $_.Exception.GetBaseException().Message) -ForegroundColor Red
    exit 1
}

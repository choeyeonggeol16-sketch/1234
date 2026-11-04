param([string]$GameFile = '', [switch]$Restore)
$ErrorActionPreference = 'Stop'
try {
    if (-not ('PssClientPatch' -as [type])) { Add-Type -Path (Join-Path $PSScriptRoot 'PSS_Client_Patch.cs') }
    if (-not $GameFile) { $GameFile = [PssClientPatch]::Find($PSScriptRoot) }
    if ($Restore) { [PssClientPatch]::Restore($GameFile) }
    else { [PssClientPatch]::Apply($GameFile) }
} catch {
    Write-Host ('ERROR: ' + $_.Exception.GetBaseException().Message) -ForegroundColor Red
    exit 1
}

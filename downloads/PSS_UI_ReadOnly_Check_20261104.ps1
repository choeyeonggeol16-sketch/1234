param([int]$TargetPid = 0)
$ErrorActionPreference = 'Stop'
$desktopPath = [Environment]::GetFolderPath('Desktop')
if (-not $desktopPath) { $desktopPath = $PSScriptRoot }
$outputPath = Join-Path $desktopPath 'PSS_UI_ReadOnly_Check'
[void](New-Item -ItemType Directory -Path $outputPath -Force)
$reportPath = Join-Path $outputPath 'report.txt'
$script:report = New-Object 'System.Collections.Generic.List[string]'
function Note([string]$text) { $script:report.Add($text); Write-Host $text }

if (-not ('PssUiReadOnlyProbe' -as [type])) { Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class PssUiReadOnlyProbe {
  [DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr OpenProcess(uint access, bool inherit, int pid);
  [DllImport("kernel32.dll")] public static extern bool CloseHandle(IntPtr handle);
  [DllImport("kernel32.dll", SetLastError=true)] public static extern bool ReadProcessMemory(IntPtr process, IntPtr address, byte[] data, UIntPtr length, out UIntPtr read);
  public static byte[] Read(IntPtr process, uint address, int count) {
    if (address < 0x10000 || count < 1 || count > 1024) throw new ArgumentException("Invalid diagnostic read");
    var bytes = new byte[count]; UIntPtr read;
    if (!ReadProcessMemory(process, (IntPtr.Size == 4 ? new IntPtr(unchecked((int)address)) : new IntPtr((long)address)), bytes, new UIntPtr((uint)count), out read) || read.ToUInt64() != (ulong)count)
      throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
    return bytes;
  }
}
'@
}
function Read32([uint32]$address) {
    return [BitConverter]::ToUInt32([PssUiReadOnlyProbe]::Read($script:processHandle, $address, 4), 0)
}
function Dump([string]$name, [uint32]$address, [int]$count) {
    try {
        $bytes = [PssUiReadOnlyProbe]::Read($script:processHandle, $address, $count)
        [IO.File]::WriteAllBytes((Join-Path $outputPath ($name + '.bin')), $bytes)
        Note ('{0} address=0x{1:X8} bytes={2}' -f $name, $address, [BitConverter]::ToString($bytes))
    } catch { Note ($name + ': ' + $_.Exception.Message) }
}
function Snapshot([string]$label) {
    Note ('--- ' + $label + ' ---')
    try {
        $phase = Read32 0x15D8E44
        $uiPointer = Read32 0x15B4BCC
        Note ('phase={0} candidate_UI=0x{1:X8}' -f $phase, $uiPointer)
        if ($uiPointer) {
            $tablePointer = Read32 $uiPointer
            $showPointer = Read32 ($tablePointer + 0x44)
            Note ('vtable=0x{0:X8} show=0x{1:X8}' -f $tablePointer, $showPointer)
            Dump ($label + '-ui-object') $uiPointer 128
            Dump ($label + '-ui-vtable') $tablePointer 128
            Dump ($label + '-ui-show') $showPointer 256
        }
    } catch { Note ('UI read failed: ' + $_.Exception.Message) }
}
$script:processHandle = [IntPtr]::Zero
try {
    Note ('PSS settings check ' + [DateTime]::Now.ToString('s'))
    Note 'Read-only diagnostic: no client code is called and no packets are sent.'
    Note 'Addresses are candidates from the supplied trial source; their UI identity remains unverified.'
    if ($TargetPid -eq 0) {
        $candidates = @(Get-Process | Where-Object {
            try {
                $_.MainModule.FileVersionInfo.FileVersion -eq '19, 02, 15, 1002' -or
                ($_.MainModule.FileVersionInfo.FileMajorPart -eq 19 -and
                 $_.MainModule.FileVersionInfo.FileMinorPart -eq 2 -and
                 $_.MainModule.FileVersionInfo.FileBuildPart -eq 15 -and
                 $_.MainModule.FileVersionInfo.FilePrivatePart -eq 1002)
            } catch { $false }
        })
        if ($candidates.Count -ne 1) {
            $candidates | Format-Table -Property @('Id', 'ProcessName') | Out-Host
            throw 'Select one client using -TargetPid <PID>. Enter the game with your character first.'
        }
        $targetProcess = $candidates[0]
    } else { $targetProcess = Get-Process -Id $TargetPid }
    $clientPath = $targetProcess.MainModule.FileName
    $clientHash = (Get-FileHash -LiteralPath $clientPath -Algorithm SHA256).Hash
    Note ('PID={0} SHA256={1}' -f $targetProcess.Id, $clientHash)
    if ($clientHash -ne '9C82129EFED1D93981782069E82DE79869920769AA0F67BC1183CA75CF621823') {
        throw 'Exact client hash mismatch. No fixed-address reads were attempted.'
    }
    if ($targetProcess.MainModule.BaseAddress.ToInt64() -ne 0x400000) { throw 'Unexpected client base address.' }
    # Query + read access only. No process memory writes or remote threads.
    $script:processHandle = [PssUiReadOnlyProbe]::OpenProcess(0x410, $false, $targetProcess.Id)
    if ($script:processHandle -eq [IntPtr]::Zero) {
        throw 'Cannot read client memory. Run with the same privilege level as the game.'
    }
    Snapshot 'candidate'
    # Capture only the known request wrappers, without executing them.
    Dump 'native-start-request' 0x642BF0 256
    Dump 'native-finish-request' 0x642B10 128
    Dump 'native-time-request' 0x642B80 128
    Note 'Read-only collection complete. This does not open a settings window.'
} catch { Note ('ERROR: ' + $_.Exception.Message) }
finally {
    if ($script:processHandle -ne [IntPtr]::Zero) { [void][PssUiReadOnlyProbe]::CloseHandle($script:processHandle) }
    $script:report | Set-Content -LiteralPath $reportPath -Encoding UTF8
    Write-Host ('Report: ' + $reportPath)
}

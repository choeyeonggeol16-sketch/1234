param([int]$TargetPid = 4356, [switch]$Restore)
$ErrorActionPreference = 'Stop'
if (-not ('PssSettingsShowTest' -as [type])) { Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Text;
using System.Diagnostics;
using System.ComponentModel;
using System.Security.Cryptography;
using System.Runtime.InteropServices;
public static class PssSettingsShowTest {
    const uint Show = 0x7918D0, Branch = Show + 8;
    static readonly byte[] Original = {0x55,0x8B,0xEC,0x80,0x7D,0x08,0x00,0x75,0x0D,0xC7,0x45,0x08,0,0,0,0,0x5D,0xE9,0xBA,0xC1,0x4C,0,0x5D,0xC2,0x04,0};
    delegate bool EnumProc(IntPtr window, IntPtr parameter);
    [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr OpenProcess(uint rights, bool inherit, int pid);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool ReadProcessMemory(IntPtr process, IntPtr address, [Out] byte[] data, UIntPtr size, out UIntPtr read);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool WriteProcessMemory(IntPtr process, IntPtr address, byte[] data, UIntPtr size, out UIntPtr written);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool VirtualProtectEx(IntPtr process, IntPtr address, UIntPtr size, uint protection, out uint old);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool FlushInstructionCache(IntPtr process, IntPtr address, UIntPtr size);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc callback, IntPtr parameter);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr window, out uint pid);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetClassName(IntPtr window, StringBuilder text, int length);
    [DllImport("user32.dll", SetLastError=true)] static extern bool PostMessage(IntPtr window, uint message, IntPtr wparam, IntPtr lparam);
    static IntPtr Address(uint value) { return IntPtr.Size == 4 ? new IntPtr(unchecked((int)value)) : new IntPtr((long)value); }
    static byte[] Read(IntPtr process, uint address, int count) {
        byte[] data = new byte[count]; UIntPtr read;
        if (!ReadProcessMemory(process, Address(address), data, new UIntPtr((uint)count), out read) || read.ToUInt64() != (ulong)count)
            throw new Win32Exception(Marshal.GetLastWin32Error());
        return data;
    }
    static uint Read32(IntPtr process, uint address) { return BitConverter.ToUInt32(Read(process,address,4),0); }
    static IntPtr Panel(int pid) {
        IntPtr found = IntPtr.Zero;
        EnumWindows(delegate(IntPtr window, IntPtr parameter) {
            uint owner; GetWindowThreadProcessId(window,out owner);
            if (owner != (uint)pid) return true;
            StringBuilder name = new StringBuilder(128); GetClassName(window,name,name.Capacity);
            if (name.ToString() == "Pss2019Panel") { found=window; return false; }
            return true;
        },IntPtr.Zero);
        return found;
    }
    static bool SetBranch(IntPtr process, bool restore) {
        byte[] current=Read(process,Show,Original.Length);
        for (int i=0;i<Original.Length;i++)
            if (i==8 ? current[i]!=0x0D && current[i]!=0x07 : current[i]!=Original[i])
                throw new InvalidOperationException("Show function signature differs. Nothing written.");
        byte target=restore ? (byte)0x0D : (byte)0x07;
        if (current[8]==target) return false;
        uint old;
        if (!VirtualProtectEx(process,Address(Branch),new UIntPtr(1),0x40,out old)) throw new Win32Exception(Marshal.GetLastWin32Error());
        try {
            UIntPtr written;
            if (!WriteProcessMemory(process,Address(Branch),new byte[]{target},new UIntPtr(1),out written) || written.ToUInt64()!=1)
                throw new Win32Exception(Marshal.GetLastWin32Error());
            if (!FlushInstructionCache(process,Address(Branch),new UIntPtr(1))) throw new Win32Exception(Marshal.GetLastWin32Error());
            if (Read(process,Branch,1)[0]!=target) throw new InvalidOperationException("Branch verification failed.");
        } finally {
            uint ignored;
            if (!VirtualProtectEx(process,Address(Branch),new UIntPtr(1),old,out ignored))
                throw new Win32Exception(Marshal.GetLastWin32Error());
        }
        return true;
    }
    public static string Run(int pid, bool restore) {
        StringBuilder log=new StringBuilder(); IntPtr process=IntPtr.Zero;
        log.AppendLine("PSS UI show branch test: " + DateTime.Now.ToString("s"));
        log.AppendLine("Runtime one-byte change only; no client file changes; not live-game verified.");
        try {
            using (Process game=Process.GetProcessById(pid)) {
                string hash;
                using (SHA256 sha=SHA256.Create()) using (FileStream input=File.OpenRead(game.MainModule.FileName))
                    hash=BitConverter.ToString(sha.ComputeHash(input)).Replace("-","");
                log.AppendLine("PID="+pid+" SHA256="+hash);
                if (hash!="9C82129EFED1D93981782069E82DE79869920769AA0F67BC1183CA75CF621823" || game.MainModule.BaseAddress.ToInt64()!=0x400000)
                    throw new InvalidOperationException("Exact original client mismatch. Nothing written.");
            }
            process=OpenProcess(0x438,false,pid);
            if (process==IntPtr.Zero) throw new Win32Exception(Marshal.GetLastWin32Error());
            if (!restore) {
                if (Read32(process,0x15D8E44)!=3) throw new InvalidOperationException("Enter the game world first.");
                uint ui=Read32(process,0x15B4BCC);
                if (ui==0 || Read32(process,ui)!=0x11B09C8 || Read32(process,0x11B09C8+0x44)!=Show)
                    throw new InvalidOperationException("PSS UI object/vtable differs. Nothing written.");
                byte[] name=Read(process,ui+4,14);
                if (Encoding.ASCII.GetString(name)!="PlaySupportUI\0") throw new InvalidOperationException("PSS UI object name differs.");
                IntPtr panel=Panel(pid);
                if (panel==IntPtr.Zero) throw new InvalidOperationException("Original PSS button panel missing. Connect the existing PSS helper, then run again. Nothing written.");
                bool changed=SetBranch(process,false);
                log.AppendLine("Show(true) branch 0x7918D8 = 07; changed="+changed);
                if (!PostMessage(panel,0x111,new IntPtr(101),IntPtr.Zero)) {
                    int error=Marshal.GetLastWin32Error();
                    if (changed) SetBranch(process,true);
                    throw new Win32Exception(error);
                }
                log.AppendLine("Existing PSS settings button request queued. This is not proof the settings window opened.");
            } else {
                bool changed=SetBranch(process,true);
                log.AppendLine("Original branch restored: 0x7918D8 = 0D; changed="+changed);
            }
        } catch (Exception error) { log.AppendLine("ERROR: "+error.Message); }
        finally { if (process!=IntPtr.Zero) CloseHandle(process); }
        return log.ToString();
    }
}
'@
}
$desktopPath = [Environment]::GetFolderPath('Desktop')
if (-not $desktopPath) { $desktopPath = $PSScriptRoot }
$outputPath = Join-Path $desktopPath 'PSS_Settings_Show_Test'
[void](New-Item -ItemType Directory -Path $outputPath -Force)
$result = [PssSettingsShowTest]::Run($TargetPid, $Restore.IsPresent)
$result | Set-Content -LiteralPath (Join-Path $outputPath 'report.txt') -Encoding UTF8
Write-Host $result
Write-Host ('Report: ' + (Join-Path $outputPath 'report.txt'))

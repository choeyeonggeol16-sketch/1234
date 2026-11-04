param([int]$TargetPid = 0)
$ErrorActionPreference = 'Stop'
if (-not ('PssItemWindowProbe' -as [type])) { Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Text;
using System.Diagnostics;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Runtime.InteropServices;
public static class PssItemWindowProbe {
    [DllImport("kernel32.dll",SetLastError=true)] static extern IntPtr OpenProcess(uint access,bool inherit,int pid);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool ReadProcessMemory(IntPtr process,IntPtr address,[Out] byte[] bytes,UIntPtr size,out UIntPtr read);
    static int FindClient() {
        List<int> matches=new List<int>();
        Dictionary<string,bool> checkedFiles=new Dictionary<string,bool>(StringComparer.OrdinalIgnoreCase);
        foreach (Process game in Process.GetProcesses()) {
            using (game) {
                try {
                    string path=game.MainModule.FileName;
                    if (game.MainModule.BaseAddress.ToInt64()!=0x400000 || new FileInfo(path).Length!=8354488) continue;
                    bool match;
                    if (!checkedFiles.TryGetValue(path,out match)) {
                        using (SHA256 sha=SHA256.Create()) using (FileStream input=File.OpenRead(path))
                            match=BitConverter.ToString(sha.ComputeHash(input)).Replace("-","")=="9C82129EFED1D93981782069E82DE79869920769AA0F67BC1183CA75CF621823";
                        checkedFiles[path]=match;
                    }
                    if (match) matches.Add(game.Id);
                } catch { /* Some unrelated system processes do not allow module queries. */ }
            }
        }
        if (matches.Count==1) return matches[0];
        if (matches.Count==0) throw new InvalidOperationException("Original client not found/readable. Enter the game; run this tool at the same privilege level as the game.");
        throw new InvalidOperationException("More than one original client found: "+string.Join(",",matches)+". Close the extra client or specify -TargetPid.");
    }
    public static string Run(int pid,string folder) {
        StringBuilder log=new StringBuilder();IntPtr process=IntPtr.Zero;
        log.AppendLine("PSS item/window linkage read-only probe "+DateTime.Now.ToString("s"));
        try {
            if (pid==0) pid=FindClient();
            using (Process game=Process.GetProcessById(pid)) {
                string hash;
                using (SHA256 sha=SHA256.Create()) using (FileStream file=File.OpenRead(game.MainModule.FileName))
                    hash=BitConverter.ToString(sha.ComputeHash(file)).Replace("-","");
                if (hash!="9C82129EFED1D93981782069E82DE79869920769AA0F67BC1183CA75CF621823" || game.MainModule.BaseAddress.ToInt64()!=0x400000)
                    throw new InvalidOperationException("Exact original client mismatch.");
                log.AppendLine("PID="+pid+" SHA256="+hash);
            }
            process=OpenProcess(0x410,false,pid);
            if (process==IntPtr.Zero) throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
            const int first=0x401000,last=0x1100000,page=4096;
            byte[] image=new byte[last-first];bool[] valid=new bool[image.Length/page];int readPages=0;
            for (int offset=0;offset<image.Length;offset+=page) {
                byte[] chunk=new byte[page];UIntPtr got;
                if (ReadProcessMemory(process,new IntPtr(first+offset),chunk,new UIntPtr(page),out got) && got.ToUInt64()==page) {
                    Buffer.BlockCopy(chunk,0,image,offset,page);valid[offset/page]=true;readPages++;
                }
            }
            SortedSet<int> snapshots=new SortedSet<int>();int uiRefs=0,startCalls=0;
            for (int i=0;i<image.Length-5;i++) {
                if (!valid[i/page] || !valid[(i+4)/page]) continue;
                bool ui=BitConverter.ToUInt32(image,i)==0x15B4BCC;
                bool call=image[i]==0xE8 && (long)first+i+5+BitConverter.ToInt32(image,i+1)==0x669C20;
                if (!ui && !call) continue;
                if (ui)uiRefs++;if(call)startCalls++;
                log.AppendLine((ui?"UI_PTR_REF ":"START_CALL ")+"0x"+(first+i).ToString("X8"));
                int entry=i/page;
                if (snapshots.Count<64) {
                    if (entry>0 && valid[entry-1])snapshots.Add(entry-1);
                    snapshots.Add(entry);
                    if (entry+1<valid.Length && valid[entry+1])snapshots.Add(entry+1);
                }
            }
            foreach (int index in snapshots) {
                byte[] chunk=new byte[page];Buffer.BlockCopy(image,index*page,chunk,0,page);
                File.WriteAllBytes(Path.Combine(folder,"code-"+(first+index*page).ToString("X8")+".bin"),chunk);
            }
            log.AppendLine("readPages="+readPages+" uiPointerReferences="+uiRefs+" startCalls="+startCalls+" codePagesSaved="+snapshots.Count);
            log.AppendLine("Read-only bounded image-code scan; no process-memory writes, no UI invocation, no packets sent.");
        } catch(Exception error) {log.AppendLine("ERROR: "+error.Message);}
        finally {if(process!=IntPtr.Zero)CloseHandle(process);}
        return log.ToString();
    }
}
'@
}
$desktopPath = [Environment]::GetFolderPath('Desktop')
if (-not $desktopPath) { $desktopPath = $PSScriptRoot }
$stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
$outputPath = Join-Path $desktopPath ('PSS_Item_Window_Probe_' + $stamp)
[void](New-Item -ItemType Directory -Path $outputPath)
$result = [PssItemWindowProbe]::Run($TargetPid, $outputPath)
$result | Set-Content -LiteralPath (Join-Path $outputPath 'report.txt') -Encoding UTF8
$archivePath = $outputPath + '.zip'
Compress-Archive -Path (Join-Path $outputPath '*') -DestinationPath $archivePath
Write-Host $result
Write-Host ('Upload: ' + $archivePath)

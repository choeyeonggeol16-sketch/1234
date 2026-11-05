using System;
using System.IO;
using System.Text;
using System.Diagnostics;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Runtime.InteropServices;

public sealed class PssNativePathReport {
    public string Source="Native PSS path configuration audit; read-only", Time=DateTime.Now.ToString("s"), Status, GroupSource, ClientHash;
    public int Pid, SelectedIndex, NativeUpperBoundRaw, ContextPathIndex;
    public byte PathFlag, FixedPositionFlag;
    public bool PssRunning, ConfigurationGroupPresent, ContextPresent, ContextPathObjectPresent, SelectedEntryChecked, SelectedEntryPresent;
    public List<string> Files = new List<string>();
}

public static class PssNativePathAudit {
    const string Original="9C82129EFED1D93981782069E82DE79869920769AA0F67BC1183CA75CF621823";
    const string Patched="C2E643673406FDCA03288211F204F812ED0EB7A3419C16662B1CB2DCD7D0135F";
    [DllImport("kernel32.dll",SetLastError=true)] static extern IntPtr OpenProcess(uint access,bool inherit,int pid);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool ReadProcessMemory(IntPtr handle,IntPtr address,[Out]byte[] bytes,UIntPtr count,out UIntPtr read);
    static byte[] Read(IntPtr handle,uint address,int count){
        byte[] data=new byte[count];UIntPtr got;
        IntPtr pointer=IntPtr.Size==4?new IntPtr(unchecked((int)address)):new IntPtr((long)address);
        if(!ReadProcessMemory(handle,pointer,data,new UIntPtr((uint)count),out got) || got.ToUInt64()!=(ulong)count)
            throw new IOException("Read failed at 0x"+address.ToString("X8"));
        return data;
    }
    static uint U32(IntPtr h,uint address){return BitConverter.ToUInt32(Read(h,address,4),0);}
    static bool Match(IntPtr h,uint a,byte[] expected){
        byte[] data=Read(h,a,expected.Length);
        for(int i=0;i<data.Length;i++)if(data[i]!=expected[i])return false;
        return true;
    }
    static string Hash(string path){
        using(SHA256 sha=SHA256.Create())using(FileStream input=File.OpenRead(path))
            return BitConverter.ToString(sha.ComputeHash(input)).Replace("-","");
    }
    static void WriteBlock(IntPtr h,uint address,string folder,string prefix,PssNativePathReport report){
        string name=prefix+address.ToString("X8")+".bin";
        if(report.Files.Contains(name))return;
        File.WriteAllBytes(Path.Combine(folder,name),Read(h,address,4096));report.Files.Add(name);
    }
    static bool StaticAddress(uint value){return value>=0x1100000 && value<0x1550000;}
    static void ConstructorTables(IntPtr h,string folder,PssNativePathReport report){
        // Candidates are verified offline; no client function is called.
        byte[] code=Read(h,0x75eaa0,256);int saved=0;
        for(int i=0;i+6<=code.Length && saved<4;i++){
            if(code[i]!=0xc7 || (code[i+1]!=1 && code[i+1]!=3 && code[i+1]!=6 && code[i+1]!=7))continue;
            uint table=BitConverter.ToUInt32(code,i+2);if(!StaticAddress(table))continue;
            try{
                uint first=U32(h,table),second=U32(h,table+4);
                if(first<0x401000 || first>=0x1100000 || second<0x401000 || second>=0x1100000)continue;
                WriteBlock(h,table&0xfffff000,folder,"metadata_",report);saved++;
            }catch(IOException){ }
        }
    }
    public static List<PssNativePathReport> Run(string folder){
        Directory.CreateDirectory(folder);
        List<PssNativePathReport> reports=new List<PssNativePathReport>();
        Dictionary<string,string> hashes=new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);
        foreach(Process process in Process.GetProcesses())using(process){
            IntPtr h=IntPtr.Zero;PssNativePathReport report=null;
            try{
                if(reports.Count>=3)break;
                string path=process.MainModule.FileName;long length=new FileInfo(path).Length;
                if((length!=8354488 && length!=8358584) || process.MainModule.BaseAddress.ToInt64()!=0x400000)continue;
                string hash;if(!hashes.TryGetValue(path,out hash)){hash=Hash(path);hashes[path]=hash;}
                if(hash!=Original && hash!=Patched)continue;
                h=OpenProcess(0x410,false,process.Id);
                if(h==IntPtr.Zero){reports.Add(new PssNativePathReport{Pid=process.Id,Status="Read access denied; run at the same privilege level as the game"});continue;}
                if(U32(h,0x15d8e44)!=3)continue;
                report=new PssNativePathReport{Pid=process.Id,ClientHash=hash};reports.Add(report);
                if(!Match(h,0x6590d0,new byte[]{0x55,0x8b,0xec,0x6a,0xff,0x68,0x88,0xa3,1,1})){
                    report.Status="Expected Battle initialization code mismatch";continue;
                }
                uint manager=U32(h,0x15b3d14);
                if(manager==0){report.Status="PSS manager unavailable";continue;}
                report.PssRunning=Read(h,manager+0x58,1)[0]!=0;
                uint task=U32(h,manager+0x24),config=U32(h,manager+0x5c);
                if(task!=0 && U32(h,task)==0x1184c78)config=U32(h,task+4);
                uint group=config==0?0:U32(h,config+0x20);
                report.GroupSource="current PSS configuration";
                if(group==0){uint defaults=U32(h,0x15b4bb0);group=defaults==0?0:U32(h,defaults+0x20);report.GroupSource="default configuration fallback";}
                report.ConfigurationGroupPresent=group!=0;
                if(group!=0){
                    report.PathFlag=Read(h,group+0xd,1)[0];report.FixedPositionFlag=Read(h,group+0xe,1)[0];
                    report.SelectedIndex=unchecked((int)U32(h,group+8));
                    report.NativeUpperBoundRaw=unchecked((int)U32(h,group+0x14));
                    uint array=U32(h,group+0x10);
                    // The native boundary check is inclusive; do not assume the
                    // upper bound is an element count or read the ambiguous edge.
                    if(report.SelectedIndex>=0 && report.NativeUpperBoundRaw>report.SelectedIndex &&
                       report.NativeUpperBoundRaw<=64 && array>=0x10000 &&
                       (ulong)array+(uint)report.SelectedIndex*4+4<=0x100000000UL)
                    {
                        report.SelectedEntryPresent=U32(h,array+(uint)report.SelectedIndex*4)!=0;
                        report.SelectedEntryChecked=true;
                    }
                }
                uint context=U32(h,0x15b3e68);
                report.ContextPresent=context!=0;
                report.ContextPathIndex=-1;
                if(context!=0){report.ContextPathObjectPresent=U32(h,context+0xcc)!=0;report.ContextPathIndex=unchecked((int)U32(h,context+0xd4));}
                string output=Path.Combine(folder,"client_"+process.Id);Directory.CreateDirectory(output);
                WriteBlock(h,0x75e000,output,"code_",report);WriteBlock(h,0x75f000,output,"code_",report);
                ConstructorTables(h,output,report);
                report.Status=U32(h,manager+0x5c)==config || (task!=0 && U32(h,manager+0x24)==task) ?
                    "Collected; raw upper bound is not labeled as a route count; non-atomic snapshot" : "PSS configuration changed during capture";
            }catch(Exception error){if(report!=null)report.Status=error is IOException?error.Message:"Collection error: "+error.GetType().Name;}
            finally{if(h!=IntPtr.Zero)CloseHandle(h);}
        }
        if(reports.Count==0)reports.Add(new PssNativePathReport{Status="Exact client in game world not found/readable"});
        return reports;
    }
}

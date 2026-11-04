using System;
using System.IO;
using System.Text;
using System.Diagnostics;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Runtime.InteropServices;

// Read-only collection of bounded native PSS code, never a full process dump.
public static class PssRoamingCodeCheck {
    const string Original="9C82129EFED1D93981782069E82DE79869920769AA0F67BC1183CA75CF621823";
    const string Patched="C2E643673406FDCA03288211F204F812ED0EB7A3419C16662B1CB2DCD7D0135F";
    const int MaxPages=96;
    [StructLayout(LayoutKind.Sequential)] struct MemoryInfo {
        public IntPtr BaseAddress,AllocationBase;
        public uint AllocationProtect;
        public UIntPtr RegionSize;
        public uint State,Protect,Type;
    }
    [DllImport("kernel32.dll",SetLastError=true)] static extern IntPtr OpenProcess(uint rights,bool inherit,int pid);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr process);
    [DllImport("kernel32.dll",SetLastError=true)] static extern bool ReadProcessMemory(IntPtr process,IntPtr address,[Out]byte[] bytes,UIntPtr length,out UIntPtr read);
    [DllImport("kernel32.dll",SetLastError=true)] static extern UIntPtr VirtualQueryEx(IntPtr process,IntPtr address,out MemoryInfo info,UIntPtr size);
    static IntPtr Address(uint n){return IntPtr.Size==4?new IntPtr(unchecked((int)n)):new IntPtr((long)n);}
    static byte[] Read(IntPtr process,uint address,int count){
        byte[] bytes=new byte[count];UIntPtr got;
        if(!ReadProcessMemory(process,Address(address),bytes,new UIntPtr((uint)count),out got)||got.ToUInt64()!=(ulong)count)
            throw new IOException("Read failed at 0x"+address.ToString("X8")+" error="+Marshal.GetLastWin32Error());
        return bytes;
    }
    static uint Read32(IntPtr process,uint address){return BitConverter.ToUInt32(Read(process,address,4),0);}
    static bool Executable(IntPtr process,uint address){
        MemoryInfo info;
        return VirtualQueryEx(process,Address(address),out info,new UIntPtr((uint)Marshal.SizeOf(typeof(MemoryInfo)))).ToUInt64()!=0
            &&info.State==0x1000&&(info.Protect&0xf0)!=0&&(info.Protect&0x101)==0;
    }
    static string Hash(string path){
        using(SHA256 sha=SHA256.Create())using(FileStream input=File.OpenRead(path))return BitConverter.ToString(sha.ComputeHash(input)).Replace("-","");
    }
    static bool Matches(IntPtr process,uint address,byte[] expected){
        try{byte[] bytes=Read(process,address,expected.Length);for(int i=0;i<bytes.Length;i++)if(bytes[i]!=expected[i])return false;return true;}
        catch(IOException){return false;}
    }
    // Bounded candidate discovery; executable-page checks follow before any collection.
    public static uint[] RelativeCalls(byte[] code,uint start){
        HashSet<uint> found=new HashSet<uint>();
        for(int i=0;i+5<=code.Length;i++)if(code[i]==0xe8){
            long target=(long)start+i+5+BitConverter.ToInt32(code,i+1);
            if(target>=0x401000&&target<0x1100000)found.Add((uint)target);
        }
        uint[] result=new uint[found.Count];found.CopyTo(result);Array.Sort(result);return result;
    }
    static void Page(IntPtr process,uint address,string folder,HashSet<uint> pages,StringBuilder report){
        uint page=address&0xfffff000;
        if(page<0x401000||page>=0x1100000||pages.Count>=MaxPages||pages.Contains(page))return;
        if(!Executable(process,page)){report.AppendLine("SKIP non-executable/unavailable page 0x"+page.ToString("X8"));return;}
        try{
            byte[] bytes=Read(process,page,4096);File.WriteAllBytes(Path.Combine(folder,"code_"+page.ToString("X8")+".bin"),bytes);
            pages.Add(page);
        }catch(IOException error){report.AppendLine(error.Message);}
    }
    static void Calls(IntPtr process,uint start,int length,string folder,HashSet<uint> pages,StringBuilder report){
        try{
            foreach(uint target in RelativeCalls(Read(process,start,length),start)){
                report.AppendLine("CALL-CANDIDATE 0x"+target.ToString("X8"));Page(process,target,folder,pages,report);
            }
        }catch(IOException error){report.AppendLine(error.Message);}
    }
    static void Vtable(IntPtr process,uint instance,string tag,string folder,HashSet<uint> pages,StringBuilder report){
        if(instance==0)return;
        try{
            uint table=Read32(process,instance);
            if(table<0x401000||table>=0x180d000)return;
            byte[] slots=Read(process,table,128);
            for(int i=0;i<3;i++){
                uint function=BitConverter.ToUInt32(slots,i*4);
                if(function<0x401000||function>=0x1100000||!Executable(process,function))return;
            }
            report.AppendLine(tag+" candidate-vtable=0x"+table.ToString("X8"));
            for(int i=0;i<32;i++){
                uint function=BitConverter.ToUInt32(slots,i*4);
                if(function<0x401000||function>=0x1100000||!Executable(process,function))continue;
                report.AppendLine(tag+" virtual["+i+"]=0x"+function.ToString("X8"));Page(process,function,folder,pages,report);
            }
        }catch(IOException error){report.AppendLine(error.Message);}
    }
    public static int Run(string folder){
        Directory.CreateDirectory(folder);StringBuilder report=new StringBuilder();int captured=0,exact=0;
        report.AppendLine("Read-only native PSS movement investigation "+DateTime.Now.ToString("s"));
        report.AppendLine("No game calls, packets, patches or whole-memory dumps. Code cap: 96 executable pages per client.");
        Dictionary<string,string> hashes=new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);
        foreach(Process client in Process.GetProcesses())using(client){
            IntPtr process=IntPtr.Zero;
            try{
                if(client.HasExited)continue;
                string path=client.MainModule.FileName;long size=new FileInfo(path).Length;
                if((size!=8354488&&size!=8358584)||client.MainModule.BaseAddress.ToInt64()!=0x400000)continue;
                string hash;if(!hashes.TryGetValue(path,out hash)){hash=Hash(path);hashes[path]=hash;}
                if(hash!=Original&&hash!=Patched)continue;
                exact++;report.AppendLine("\nPID="+client.Id+" file="+Path.GetFileName(path)+" SHA256="+hash);
                process=OpenProcess(0x410,false,client.Id);
                if(process==IntPtr.Zero){report.AppendLine("READ ACCESS DENIED error="+Marshal.GetLastWin32Error()+"; run at the same privilege level as the game.");continue;}
                uint phase=Read32(process,0x15d8e44);report.AppendLine("phase="+phase);
                if(phase!=3){report.AppendLine("Not in world; skipped.");continue;}
                bool reset=Matches(process,0x6688d0,new byte[]{0x55,0x8b,0xec,0x6a,0xff,0x68,0xe8,0xe4,1,1});
                bool battle=Matches(process,0x669840,new byte[]{0x55,0x8b,0xec,0x6a,0xff,0x68,0x60,0xe8,1,1});
                bool advance=Matches(process,0x669b20,new byte[]{0x56,0x8b,0xf1,0x83,0x7e,0x20,0});
                report.AppendLine("known-code reset="+reset+" battle="+battle+" advance="+advance);
                if(!reset||!battle||!advance){report.AppendLine("Expected decoded code not readable. No code captured for this process.");continue;}
                if(captured>=3){report.AppendLine("Client capture limit reached.");continue;}
                string output=Path.Combine(folder,"client_"+client.Id);Directory.CreateDirectory(output);HashSet<uint> pages=new HashSet<uint>();
                // PSS conductor region, plus the native PSS request wrapper.
                for(uint address=0x660000;address<0x670000;address+=4096)Page(process,address,output,pages,report);
                Page(process,0x642b80,output,pages,report);
                Calls(process,0x6688d0,0x130,output,pages,report);
                Calls(process,0x669840,0x2e0,output,pages,report);
                Calls(process,0x669b20,0x100,output,pages,report);
                captured++;
                uint manager=Read32(process,0x15b3d14);report.AppendLine("manager=0x"+manager.ToString("X8"));
                if(manager!=0){
                    uint flow=Read32(process,manager+0x20);byte running=Read(process,manager+0x58,1)[0];
                    report.AppendLine("running="+running+" flow=0x"+flow.ToString("X8"));
                    Vtable(process,manager,"manager",output,pages,report);Vtable(process,flow,"flow",output,pages,report);
                }
                report.AppendLine("captured executable pages="+pages.Count);
            }catch(Exception error){
                // Avoid system-process noise. Report only recognized clients.
                if(process!=IntPtr.Zero)report.AppendLine("PID="+client.Id+" ERROR: "+error.Message);
            }finally{if(process!=IntPtr.Zero)CloseHandle(process);}
        }
        report.AppendLine("\nExact processes="+exact+" Captured clients="+captured);
        if(exact==0)report.AppendLine("Exact client not found/readable. Enter the game and run at the same privilege level.");
        File.WriteAllText(Path.Combine(folder,"report.txt"),report.ToString(),new UTF8Encoding(true));
        Console.WriteLine(report.ToString());return captured;
    }
}

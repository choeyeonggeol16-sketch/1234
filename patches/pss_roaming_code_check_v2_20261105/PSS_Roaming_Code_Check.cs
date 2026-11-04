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
            byte[] slots=Read(process,table,12);
            for(int i=0;i<3;i++){
                uint function=BitConverter.ToUInt32(slots,i*4);
                if(function<0x401000||function>=0x1100000||!Executable(process,function))return;
            }
            report.AppendLine(tag+" verified-three-method-vtable=0x"+table.ToString("X8"));
            // Only the three-method prefix is established (destructor/update/begin
            // for tasks). Following bytes can belong to adjacent types.
            if(tag=="current_task"&&table>=0x1170000&&table<0x1190000){
                uint dataPage=table&0xfffff000;
                File.WriteAllBytes(Path.Combine(folder,"pss_task_vtable_"+dataPage.ToString("X8")+".bin"),Read(process,dataPage,4096));
            }
            for(int i=0;i<3;i++){
                uint function=BitConverter.ToUInt32(slots,i*4);
                report.AppendLine(tag+" virtual["+i+"]=0x"+function.ToString("X8"));Page(process,function,folder,pages,report);
                if(tag=="current_task"){
                    Page(process,function+0x600,folder,pages,report);
                    Calls(process,function,0x600,folder,pages,report);
                }
            }
        }catch(IOException error){report.AppendLine(error.Message);}
    }
    static string TaskName(IntPtr process,uint task){
        if(task==0)return "<none>";
        try{
            uint table=Read32(process,task);
            if(table<0x1170000||table>=0x1190000)return "<unverified task type>";
            for(uint offset=0;offset<12;offset+=4){
                uint function=Read32(process,table+offset);
                if(function<0x401000||function>=0x1100000||!Executable(process,function))return "<unverified task methods>";
            }
            uint length=Read32(process,task+0x28),capacity=Read32(process,task+0x2c);
            if(length==0||length>64||capacity<length||capacity>4096)return "<unverified name layout>";
            uint address=capacity<16?task+0x18:Read32(process,task+0x18);
            byte[] text=Read(process,address,(int)length);
            foreach(byte character in text)if(character<32||character>126)return "<non-ASCII task name omitted>";
            return Encoding.ASCII.GetString(text);
        }catch(IOException){return "<unreadable task name>";}
    }
    public static int Run(string folder){
        Directory.CreateDirectory(folder);StringBuilder report=new StringBuilder();int captured=0,exact=0;
        report.AppendLine("Read-only native PSS movement investigation V2 "+DateTime.Now.ToString("s"));
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
                // Confirmed PSS task implementations span 0x65xxxx through 0x67xxxx.
                for(uint address=0x650000;address<0x680000;address+=4096)Page(process,address,output,pages,report);
                Page(process,0x642b80,output,pages,report);
                // Fixed compiled task names/vtables referenced by the captured builder.
                if(Read32(process,0x11874e8)==0x668760&&Read32(process,0x11874ec)==0x6691b0){
                    File.WriteAllBytes(Path.Combine(output,"pss_constants_01187000.bin"),Read(process,0x1187000,4096));
                    report.AppendLine("Captured fixed PSS compiled names/vtables page 0x01187000");
                }
                captured++;
                uint manager=Read32(process,0x15b3d14);report.AppendLine("manager=0x"+manager.ToString("X8"));
                if(manager!=0){
                    uint flow=Read32(process,manager+0x20);byte running=Read(process,manager+0x58,1)[0];
                    uint task=Read32(process,manager+0x24);
                    report.AppendLine("running="+running+" active_queue=0x"+flow.ToString("X8")+" current_task=0x"+task.ToString("X8"));
                    report.AppendLine("primary_count="+Read32(process,manager+0x14)+" auxiliary_count="+Read32(process,manager+0x1c));
                    report.AppendLine("current_task_name="+TaskName(process,task));
                    Vtable(process,task,"current_task",output,pages,report);
                    Vtable(process,manager,"manager",output,pages,report);
                }
                Calls(process,0x6688d0,0x130,output,pages,report);
                Calls(process,0x669840,0x2e0,output,pages,report);
                Calls(process,0x669b20,0x100,output,pages,report);
                Calls(process,0x657f10,0x800,output,pages,report);
                Calls(process,0x6778e0,0x800,output,pages,report);
                Calls(process,0x678000,0x900,output,pages,report);
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

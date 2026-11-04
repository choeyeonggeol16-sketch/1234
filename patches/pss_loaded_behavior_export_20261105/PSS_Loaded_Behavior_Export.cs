using System;
using System.IO;
using System.Text;
using System.Diagnostics;
using System.Collections.Generic;
using System.Security.Cryptography;
using System.Runtime.InteropServices;

public sealed class PssBehaviorNode {
    public string Type, Tag, Vtable, Update, Rtti, Warning;
    public List<PssBehaviorNode> Children = new List<PssBehaviorNode>();
}

// Reads the already instantiated graph. This is NOT the original JSON asset.
// Injection of the read delegate also permits tests without opening any process.
public sealed class PssBehaviorTreeReader {
    readonly Func<uint,int,byte[]> read;
    readonly HashSet<uint> seen = new HashSet<uint>();
    int bytes, nodes;
    public const int MaxBytes=262144, MaxNodes=256, MaxDepth=24, MaxChildren=64;
    static readonly Dictionary<uint,string> Types = new Dictionary<uint,string> {
        {0x11b4094,"Sequence"},{0x11b4084,"Selector"},
        {0x1184878,"BotAttackNode"},{0x1185068,"BotBuyNode"},
        {0x11879d0,"BotCustomSpellNode"},{0x11892f4,"BotDialogNode"},
        {0x118af9c,"BotGetNode"},{0x118e830,"BotSearchItemNode"},
        {0x118ec14,"BotSearchMonsterNode"},{0x118f3dc,"BotSpellAttackNode"},
        {0x118fb54,"BotTeleportBookNode"},{0x11915e0,"BotWalkTargetNode"},
        {0x1191984,"BotWalkToItemNode"},{0x118ca64,"BotPlayPath"},
        {0x11902cc,"BotTeleportNode"},{0x118d850,"BotRestartNode"},
        {0x1189ee4,"BotEscapeNode"},{0x1189a74,"BotEndNode"},
        {0x11887d0,"BotDepositNode"},{0x118dbd4,"BotRetrieveNode"},
        {0x118cdf8,"BotDieNode"},{0x118be94,"BotMonsterAttackCheckNode"},
        {0x1185814,"BotCanAttackNode"},{0x1191228,"BotWalkNPCNode"},
        {0x118f034,"BotSearchNpcNode"},{0x1188f64,"BotDialogActionNode"},
        {0x1183d90,"BotAdenTelNode"},{0x118414c,"BotAnswerNode"}
    };
    public PssBehaviorTreeReader(Func<uint,int,byte[]> reader){read=reader;}
    byte[] Read(uint address,int count){
        if(address<0x10000 || (ulong)address+(uint)count>0x100000000UL || count<0 || count>256 || bytes>MaxBytes-count)
            throw new IOException("Invalid address or read budget reached");
        bytes+=count;
        byte[] value=read(address,count);
        if(value==null || value.Length!=count)throw new IOException("Short read");
        return value;
    }
    uint U32(uint address){return BitConverter.ToUInt32(Read(address,4),0);}
    static bool Module(uint a,int size){return a>=0x401000 && (ulong)a+(uint)size<=0x1f2f000UL;}
    static bool Code(uint a){return a>=0x401000 && a<0x1100000;}
    string StaticAscii(uint address){
        if(!Module(address,128))return null;
        byte[] data=Read(address,128);int length=Array.IndexOf(data,(byte)0);
        if(length<1)return null;
        for(int i=0;i<length;i++)if(data[i]<32 || data[i]>126)return null;
        return Encoding.ASCII.GetString(data,0,length);
    }
    string Rtti(uint table){
        try {
            uint locator=U32(table-4);if(!Module(locator,20) || U32(locator)!=0)return null;
            uint descriptor=U32(locator+12);if(!Module(descriptor,136))return null;
            string name=StaticAscii(descriptor+8);
            return name!=null && (name.StartsWith(".?AV") || name.StartsWith(".?AU")) ? name : null;
        }catch(IOException){return null;}
    }
    string Tag(uint address){
        uint length=U32(address+0x1c),capacity=U32(address+0x20);
        if(length==0)return "";
        if(length>64 || capacity<length || capacity>4096)return "<unverified tag omitted>";
        uint text=capacity<16 ? address+0xc : U32(address+0xc);
        byte[] data=Read(text,(int)length);
        foreach(byte b in data)if(b<32 || b>126)return "<non-ASCII tag omitted>";
        return Encoding.ASCII.GetString(data);
    }
    public PssBehaviorNode ReadTree(uint address){return Visit(address,0);}
    PssBehaviorNode Visit(uint address,int depth){
        PssBehaviorNode node=new PssBehaviorNode();
        try {
            if(depth>MaxDepth || nodes>=MaxNodes){node.Type="Limit";node.Warning="Graph limit reached";return node;}
            nodes++;
            uint table=U32(address);if(!Module(table,8) || table<0x1100000)throw new IOException("Unverified node vtable");
            uint destroy=U32(table),update=U32(table+4);
            if(!Code(destroy) || !Code(update))throw new IOException("Unverified node methods");
            node.Vtable="0x"+table.ToString("X8");node.Update="0x"+update.ToString("X8");
            node.Rtti=Rtti(table);
            string type;node.Type=Types.TryGetValue(table,out type)?type:"Unresolved";
            if(node.Type=="Unresolved" && node.Rtti!=null &&
                (node.Rtti.StartsWith(".?AVInverter@") || node.Rtti.StartsWith(".?AUInverter@")))
                node.Type="Inverter";
            node.Tag=node.Type=="Unresolved"?"<unverified layout omitted>":Tag(address);
            if(!seen.Add(address)){node.Warning="Repeated node: shared graph edge or cycle; not followed again";return node;}
            if(node.Type=="Sequence" || node.Type=="Selector")ReadChildren(address,node,depth);
            else if(node.Type=="Inverter"){
                if(depth>=MaxDepth || nodes>=MaxNodes)node.Warning="Graph limit reached";
                else node.Children.Add(Visit(U32(address+0x24),depth+1));
            }
            else if(node.Type=="Unresolved")node.Warning="Type not mapped; child layout was not guessed";
        }catch(IOException error){node.Warning=error.Message;}
        return node;
    }
    void ReadChildren(uint address,PssBehaviorNode parent,int depth){
        if(depth>=MaxDepth){parent.Warning="Depth limit reached";return;}
        uint head=U32(address+0x24),count=U32(address+0x28);
        if(head<0x10000 || count>MaxChildren)throw new IOException("Invalid child list or too many children");
        uint link=U32(head),previous=head;HashSet<uint> links=new HashSet<uint>();
        for(uint i=0;i<count;i++){
            if(nodes>=MaxNodes){parent.Warning="Node limit reached";return;}
            if(link==head || link<0x10000 || !links.Add(link) || U32(link+4)!=previous)
                throw new IOException("Child list changed or contains invalid links");
            uint next=U32(link),child=U32(link+8);
            parent.Children.Add(Visit(child,depth+1));previous=link;link=next;
        }
        if(link!=head || U32(head+4)!=previous || U32(address+0x28)!=count)
            throw new IOException("Child list changed during read");
    }
    public int BytesRead {get{return bytes;}}
    public int NodesRead {get{return nodes;}}
}

public sealed class PssLoadedBehaviorReport {
    public string Source="Instantiated PSS graph; NOT the original playSupport.json";
    public string Time=DateTime.Now.ToString("s"),ClientHash,Status;
    public int Pid,Target,BytesRead,NodesRead;
    public PssBehaviorNode Battle;
}

public static class PssLoadedBehaviorExport {
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
    public static List<PssLoadedBehaviorReport> Run(){
        List<PssLoadedBehaviorReport> reports=new List<PssLoadedBehaviorReport>();
        Dictionary<string,string> hashes=new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);
        foreach(Process process in Process.GetProcesses())using(process){
            IntPtr h=IntPtr.Zero;PssLoadedBehaviorReport report=null;
            try {
                if(reports.Count>=3)break;
                string path=process.MainModule.FileName;long length=new FileInfo(path).Length;
                if((length!=8354488 && length!=8358584) || process.MainModule.BaseAddress.ToInt64()!=0x400000)continue;
                string hash;if(!hashes.TryGetValue(path,out hash)){hash=Hash(path);hashes[path]=hash;}
                if(hash!=Original && hash!=Patched)continue;
                h=OpenProcess(0x410,false,process.Id);
                if(h==IntPtr.Zero){report=new PssLoadedBehaviorReport {Pid=process.Id,ClientHash=hash,Status="Read access denied; run at the same privilege level as the game"};reports.Add(report);continue;}
                if(U32(h,0x15d8e44)!=3)continue;
                report=new PssLoadedBehaviorReport {Pid=process.Id,ClientHash=hash};reports.Add(report);
                if(!Match(h,0x669840,new byte[]{0x55,0x8b,0xec,0x6a,0xff,0x68,0x60,0xe8,1,1}) ||
                   U32(h,0x11b4094)!=0x7a72f0 || U32(h,0x11b4098)!=0x7a7350){report.Status="Expected PSS code/type identity mismatch";continue;}
                uint manager=U32(h,0x15b3d14);
                if(manager==0 || Read(h,manager+0x58,1)[0]!=1){report.Status="Start PSS first, then run while idle";continue;}
                uint task=U32(h,manager+0x24);
                if(task==0 || U32(h,task)!=0x1184c78){report.Status="Current task is not Battle; retry while idle in Battle";continue;}
                uint root=U32(h,task+0x38),context=U32(h,0x15b3e68);
                report.Target=context==0?-1:unchecked((int)U32(h,context+0xa8));
                PssBehaviorTreeReader reader=new PssBehaviorTreeReader((a,n)=>Read(h,a,n));
                report.Battle=reader.ReadTree(root);report.BytesRead=reader.BytesRead;report.NodesRead=reader.NodesRead;
                report.Status=U32(h,manager+0x24)==task && U32(h,task+0x38)==root ?
                    "Collected; inspect per-node warnings; non-atomic snapshot" : "Task changed during collection; snapshot may be inconsistent";
            }catch(Exception error){if(report!=null)report.Status=error is IOException?error.Message:"Collection error: "+error.GetType().Name;}
            finally{if(h!=IntPtr.Zero)CloseHandle(h);}
        }
        if(reports.Count==0)reports.Add(new PssLoadedBehaviorReport {Status="Exact client in game world not found/readable. Enter the game and run at the same privilege level."});
        return reports;
    }
}

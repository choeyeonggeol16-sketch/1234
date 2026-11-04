using System;
using System.IO;
using System.Text;
using System.Collections.Generic;
using System.Security.Cryptography;

// Exact-version import extension. No original code section is patched on disk.
public static class PssClientPatch {
    public const string OriginalHash="9c82129efed1d93981782069e82de79869920769aa0f67bc1183ca75cf621823";
    public const int OriginalLength=8354488, Page=4096, OldCertificate=0x7f3a00;
    public const uint IdataRva=0x1477000;
    static readonly byte[] Marker=Encoding.ASCII.GetBytes("PSSUI-AUTO-V1\0\0\0");
    static uint U(byte[] b,int o){return BitConverter.ToUInt32(b,o);}
    static void W(byte[] b,int o,uint n){Array.Copy(BitConverter.GetBytes(n),0,b,o,4);}
    public static string Hash(byte[] bytes){using(SHA256 sha=SHA256.Create())return BitConverter.ToString(sha.ComputeHash(bytes)).Replace("-","").ToLowerInvariant();}
    static int Align4(int n){return (n+3)&~3;}
    static void Need(bool condition,string error){if(!condition)throw new InvalidOperationException(error);}
    static uint Checksum(byte[] bytes,int checksumOffset){
        ulong sum=0;
        for(int i=0;i<bytes.Length;i+=2){
            if(i>=checksumOffset&&i<checksumOffset+4)continue;
            sum+=(uint)bytes[i]|(i+1<bytes.Length?(uint)bytes[i+1]<<8:0);
            sum=(sum&65535)+(sum>>16);
        }
        sum=(sum&65535)+(sum>>16);
        return (uint)((sum&65535)+(uint)bytes.Length);
    }
    public static byte[] Build(byte[] original){
        Need(original.Length==OriginalLength&&Hash(original)==OriginalHash,"Exact original client hash differs. No patch applied.");
        int pe=(int)U(original,0x3c),opt=pe+24;
        Need(U(original,pe)==0x4550&&BitConverter.ToUInt16(original,pe+4)==0x14c,"PE32 x86 required.");
        Need(BitConverter.ToUInt16(original,opt)==0x10b&&U(original,opt+28)==0x400000&&U(original,opt+56)==0x1b2f000,"Unexpected image layout.");
        int sections=opt+BitConverter.ToUInt16(original,pe+20),idata=sections+2*40,dirs=opt+96;
        Need(BitConverter.ToUInt16(original,pe+6)==6&&U(original,idata+8)==Page&&U(original,idata+12)==IdataRva,"Unexpected idata section.");
        Need(U(original,idata+16)==512&&U(original,idata+20)==0x526000,"Unexpected idata file location.");
        Need(U(original,dirs+32)==OldCertificate&&U(original,dirs+36)==original.Length-OldCertificate,"Unexpected certificate overlay.");
        byte[] page=new byte[Page];Array.Copy(original,0x526000,page,0,512);
        int import=(int)(U(original,dirs+8)-IdataRva),count=0;
        while(count<16){
            int p=import+count*20;Need(p>=0&&p+20<=512,"Import descriptors outside original idata.");
            if(U(page,p)==0&&U(page,p+4)==0&&U(page,p+8)==0&&U(page,p+12)==0&&U(page,p+16)==0)break;
            count++;
        }
        Need(count==2,"Unexpected original dependency count.");
        int table=512,cursor=table+(count+2)*20;
        Array.Copy(page,import,page,table,count*20);
        int dllName=cursor;byte[] dll=Encoding.ASCII.GetBytes("Pss2019.dll\0");Array.Copy(dll,0,page,cursor,dll.Length);cursor+=dll.Length;
        cursor=Align4(cursor);int symbol=cursor;cursor+=2;
        byte[] name=Encoding.ASCII.GetBytes("PssClientInit@0\0");Array.Copy(name,0,page,cursor,name.Length);cursor=Align4(cursor+name.Length);
        int lookup=cursor;cursor+=8;int address=cursor;cursor+=8;
        Need(cursor<0xf00,"New import table too large.");
        W(page,lookup,IdataRva+(uint)symbol);W(page,address,IdataRva+(uint)symbol);
        int descriptor=table+count*20;
        W(page,descriptor,IdataRva+(uint)lookup);W(page,descriptor+12,IdataRva+(uint)dllName);W(page,descriptor+16,IdataRva+(uint)address);
        Array.Copy(Marker,0,page,0xf00,Marker.Length);
        byte[] patched=new byte[original.Length+Page];
        Array.Copy(original,0,patched,0,OldCertificate);Array.Copy(page,0,patched,OldCertificate,Page);
        Array.Copy(original,OldCertificate,patched,OldCertificate+Page,original.Length-OldCertificate);
        W(patched,idata+16,Page);W(patched,idata+20,OldCertificate);
        W(patched,dirs+8,IdataRva+512);W(patched,dirs+12,(uint)((count+2)*20));
        W(patched,dirs+32,OldCertificate+Page);W(patched,opt+64,0);W(patched,opt+64,Checksum(patched,opt+64));
        Need(U(patched,opt+56)==U(original,opt+56),"Image size changed.");
        return patched;
    }
    static void ClosedClient(string path){
        using(FileStream file=new FileStream(path,FileMode.Open,FileAccess.ReadWrite,FileShare.None)){}
    }
    static void AtomicWrite(string path,byte[] bytes){
        string temporary=path+".pss-pending";
        Need(!File.Exists(temporary),"A previous pending patch exists. Check it before retrying.");
        try{
            using(FileStream output=new FileStream(temporary,FileMode.CreateNew,FileAccess.Write,FileShare.None)){output.Write(bytes,0,bytes.Length);output.Flush(true);}
            File.Replace(temporary,path,null);
        }finally{if(File.Exists(temporary))File.Delete(temporary);}
    }
    public static string Find(string folder){
        List<string> found=new List<string>();
        foreach(string path in Directory.GetFiles(folder)){
            string ext=Path.GetExtension(path).ToLowerInvariant();if(ext!=".bin"&&ext!=".exe")continue;
            long size=new FileInfo(path).Length;if(size!=OriginalLength&&size!=OriginalLength+Page)continue;
            byte[] data=File.ReadAllBytes(path);
            if(Hash(data)==OriginalHash){found.Add(path);continue;}
            string backup=path+".pss-original.bak";
            if(size==OriginalLength+Page&&File.Exists(backup)){
                byte[] old=File.ReadAllBytes(backup);if(Hash(old)==OriginalHash&&Hash(Build(old))==Hash(data))found.Add(path);
            }
        }
        Need(found.Count==1,"Put these files beside ONE original game .bin/.exe, or pass -GameFile with its full path.");
        return found[0];
    }
    public static void Apply(string path){
        path=Path.GetFullPath(path);ClosedClient(path);
        string dll=Path.Combine(Path.GetDirectoryName(path),"Pss2019.dll");
        Need(File.Exists(dll),"Put the supplied Pss2019.dll beside the game .bin file first.");
        string sums=dll+".sha256";
        Need(File.Exists(sums),"The supplied Pss2019.dll.sha256 file is missing.");
        string expected=File.ReadAllText(sums).Trim().Split(new char[]{' ','\t','\r','\n'},StringSplitOptions.RemoveEmptyEntries)[0];
        Need(expected.Length==64&&Hash(File.ReadAllBytes(dll))==expected.ToLowerInvariant(),"DLL checksum differs. Use the supplied automatic-loading DLL.");
        byte[] current=File.ReadAllBytes(path);string backup=path+".pss-original.bak";
        if(File.Exists(backup)){
            byte[] old=File.ReadAllBytes(backup);Need(Hash(old)==OriginalHash,"Existing backup is not the exact original. It was preserved.");
            if(Hash(current)==Hash(Build(old))){Console.WriteLine("Already applied: "+path);return;}
        }
        byte[] patched=Build(current);
        if(!File.Exists(backup))File.Copy(path,backup,false);
        Need(Hash(File.ReadAllBytes(backup))==OriginalHash,"Backup verification failed.");
        Need(Hash(File.ReadAllBytes(path))==OriginalHash,"Client changed while preparing the patch.");
        AtomicWrite(path,patched);
        Console.WriteLine("Applied: "+path);Console.WriteLine("Original backup: "+backup);
        Console.WriteLine("SHA256: "+Hash(File.ReadAllBytes(path)));
        Console.WriteLine("Launch the game normally. No PssButton.exe or connect button is needed.");
    }
    public static void Restore(string path){
        path=Path.GetFullPath(path);ClosedClient(path);string backup=path+".pss-original.bak";
        Need(File.Exists(backup),"Original backup missing.");
        byte[] old=File.ReadAllBytes(backup);Need(Hash(old)==OriginalHash,"Original backup hash differs.");
        string hash=Hash(File.ReadAllBytes(path));
        Need(hash==OriginalHash||hash==Hash(Build(old)),"Client was changed by something else; restore refused to preserve that file.");
        if(hash!=OriginalHash)AtomicWrite(path,old);
        Console.WriteLine("Original restored: "+path);
    }
}

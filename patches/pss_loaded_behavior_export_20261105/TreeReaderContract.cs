using System;
using System.IO;
using System.Text;
using System.Collections.Generic;

class TreeReaderContract {
    static Dictionary<uint,byte> memory;
    static int checks;
    static void Check(bool yes,string message){checks++;if(!yes)throw new Exception(message);}
    static void Put(uint a,byte[] data){for(uint i=0;i<data.Length;i++)memory[a+i]=data[i];}
    static void U32(uint a,uint n){Put(a,BitConverter.GetBytes(n));}
    static byte[] Read(uint a,int n){byte[] data=new byte[n];for(uint i=0;i<n;i++){byte value;if(!memory.TryGetValue(a+i,out value))throw new IOException("fixture address absent");data[i]=value;}return data;}
    static void Table(uint table,uint update){U32(table-4,0);U32(table,0x658000);U32(table+4,update);}
    static void Node(uint a,uint table,string tag){Put(a,new byte[44]);U32(a,table);byte[] t=Encoding.ASCII.GetBytes(tag);Put(a+12,t);U32(a+28,(uint)t.Length);U32(a+32,15);}
    static void List(uint a,uint head,params uint[] children){
        U32(a+36,head);U32(a+40,(uint)children.Length);
        U32(head,children.Length==0?head:head+16);U32(head+4,head+(uint)children.Length*16);
        for(uint i=0;i<children.Length;i++){
            uint link=head+(i+1)*16;U32(link,i+1==children.Length?head:link+16);
            U32(link+4,i==0?head:link-16);U32(link+8,children[i]);U32(link+12,0);
        }
    }
    static void Reset(){memory=new Dictionary<uint,byte>();Table(0x11b4094,0x7a7350);Table(0x11b4084,0x7a7120);Table(0x118ec14,0x686560);Table(0x11915e0,0x68da50);}
    static PssBehaviorNode Tree(uint a){return new PssBehaviorTreeReader(Read).ReadTree(a);}
    static void Main(){
        Reset();Node(0x20000,0x11b4094,"Battle");Node(0x21000,0x11b4084,"Choose");Node(0x22000,0x118ec14,"Find");Node(0x23000,0x11915e0,"Walk");
        List(0x20000,0x30000,0x21000,0x23000);List(0x21000,0x31000,0x22000);
        PssBehaviorNode root=Tree(0x20000);
        Check(root.Type=="Sequence"&&root.Tag=="Battle"&&root.Warning==null,"sequence");
        Check(root.Children.Count==2&&root.Children[0].Type=="Selector","selector/order");
        Check(root.Children[0].Children[0].Type=="BotSearchMonsterNode","search type");
        Check(root.Children[1].Type=="BotWalkTargetNode","move type");
        Check(root.Children[0].Children[0].Children.Count==0,"no leaf pointer scanning");

        // Standard x86 MSVC RTTI proves the decorator family before following it.
        Table(0x11a0010,0x732300);U32(0x11a000c,0x1370000);Put(0x1370000,new byte[20]);U32(0x137000c,0x1530000);
        Put(0x1530008,new byte[128]);Put(0x1530008,Encoding.ASCII.GetBytes(".?AVInverter@BT@@"));
        Node(0x24000,0x11a0010,"Invert");U32(0x24024,0x22000);
        root=Tree(0x24000);Check(root.Type=="Inverter"&&root.Children.Count==1&&root.Children[0].Type=="BotSearchMonsterNode","RTTI decorator");

        Reset();Node(0x20000,0x11b4094,"Cycle");List(0x20000,0x30000,0x20000);
        root=Tree(0x20000);Check(root.Children.Count==1&&root.Children[0].Warning!=null,"graph cycle bounded");
        Reset();Node(0x20000,0x11b4094,"ListCycle");Node(0x21000,0x118ec14,"Find");List(0x20000,0x30000,0x21000,0x21000);U32(0x30010,0x30010);
        root=Tree(0x20000);Check(root.Warning!=null,"list cycle rejected");
        Reset();Node(0x20000,0x11b4094,"Broken");List(0x20000,0x30000,0x21000);U32(0x30014,0x123456);
        Check(Tree(0x20000).Warning!=null,"backlink validation");
        Reset();Node(0x20000,0x11b4094,"Oversize");U32(0x20024,0x30000);U32(0x20028,65);
        Check(Tree(0x20000).Warning!=null,"child cap");
        Reset();Node(0x20000,0x118ec14,"LongTag");U32(0x2001c,1000);U32(0x20020,1000);
        Check(Tree(0x20000).Tag=="<unverified tag omitted>","string cap");
        Reset();Node(0x20000,0x118ec14,"Tag");U32(0x2001c,21);U32(0x20020,31);U32(0x2000c,0x40000);Put(0x40000,Encoding.ASCII.GetBytes("Heap-tag-twenty-char!"));
        Check(Tree(0x20000).Tag=="Heap-tag-twenty-char!","heap string layout");
        Reset();Table(0x1180000,0x1234);Node(0x20000,0x1180000,"Bad");
        Check(Tree(0x20000).Warning!=null,"invalid methods rejected");
        Reset();Table(0x1180000,0x666666);Node(0x20000,0x1180000,"Private");
        root=Tree(0x20000);Check(root.Type=="Unresolved"&&root.Tag=="<unverified layout omitted>"&&root.Children.Count==0,"unknown layout not scanned");
        Reset();for(uint i=0;i<27;i++){uint a=0x20000+i*0x100;Node(a,0x11b4094,"Deep");List(a,0x50000+i*0x100,a+0x100);}
        root=Tree(0x20000);int depth=0;while(root.Children.Count!=0){root=root.Children[0];depth++;}
        Check(root.Warning!=null&&depth<=PssBehaviorTreeReader.MaxDepth,"depth cap");
        PssBehaviorTreeReader reader=new PssBehaviorTreeReader(Read);reader.ReadTree(0x20000);
        Check(reader.BytesRead<=PssBehaviorTreeReader.MaxBytes&&reader.NodesRead<=PssBehaviorTreeReader.MaxNodes,"memory/node caps");
        Reset();Node(0x20000,0x11b4094,"Wide");uint[] branches=new uint[5];
        for(uint b=0;b<5;b++){
            branches[b]=0x40000+b*0x100;Node(branches[b],0x11b4094,"Branch");uint[] leaves=new uint[64];
            for(uint i=0;i<64;i++){leaves[i]=0x100000+(b*64+i)*0x100;Node(leaves[i],0x118ec14,"Find");}
            List(branches[b],0x300000+b*0x10000,leaves);
        }
        List(0x20000,0x30000,branches);reader=new PssBehaviorTreeReader(Read);root=reader.ReadTree(0x20000);
        Check(reader.NodesRead==PssBehaviorTreeReader.MaxNodes&&root.Warning!=null,"wide graph capped at 256 exported nodes");
        Console.WriteLine("PASS loaded behavior tree contracts checks="+checks);
    }
}

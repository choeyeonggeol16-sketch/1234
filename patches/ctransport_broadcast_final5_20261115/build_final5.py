#!/usr/bin/env python3
"""Apply remaining 5 broadcast classes, verify exact latest SRC and build one ZIP.

No 'success' output is published unless a Java21 full compilation and runtime
wire regression succeed in the GitHub Actions job.
"""
from __future__ import annotations
import argparse,hashlib,json,pathlib,re,sys,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT))
from patches.ctransport_broadcast_final5_20261115 import skills_drop,world_capture
from patches.ctransport_broadcast_expand4_20261115 import expand4 as helper

INPUT="CTransport_SharedBroadcast_50Packet_FULL_SRC_20261115.zip"
INPUT_SHA256="88372d2b7e69989406e708065268382e9ad2b9e471b721270d05aca0da6fe0ed"
OUTPUT="CTransport_SharedBroadcast_FinalFive_FULL_SRC_20261115.zip"
SHA=lambda v:hashlib.sha256(v).hexdigest()
BASE=skills_drop.BASE
WORLD_PATH=BASE+"SC_WorldPutObject.java"

PARITY_SRC=r'''package l1j.server.server.model.network.core.sc.packet;
import java.nio.*;
import java.nio.charset.*;
import java.lang.reflect.*;
import java.util.*;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject.WorldPutBuilder;
public final class SharedFinalFiveWireParity {
    static int tests;
    static ByteBuffer ref(int size) {
        ByteBuffer b=ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short)size);
        return b;
    }
    static byte[] invoke(String cls,String method,Class<?>[] types,Object...args)throws Exception {
        Class<?> c=Class.forName("l1j.server.server.model.network.core.sc.packet."+cls);
        Method m=c.getDeclaredMethod(method,types);m.setAccessible(true);
        return (byte[])m.invoke(null,args);
    }
    static void eq(byte[] got, byte[] expected,String name,int testcase) {
        if (!Arrays.equals(got,expected))
          throw new AssertionError(name+" case "+testcase+"\nGOT="+Arrays.toString(got)+"\nEXP="+Arrays.toString(expected));
        tests++;
    }
    static int rnd(int c,int n){return (int)(0x123fedcbL^((c+1L)*0x9e3779b9L)^((n+1L)*0xc2b2ae35L));}
    static void attack() throws Exception {
       Class<?>[] typ={
         int.class,int.class,int.class,int.class,int.class,int.class,int.class,
         int.class,int.class,int.class,int.class};
       for (int k=0;k<512;k++){
         int[] v=new int[11];
         for(int j=0;j<v.length;j++)v[j]=rnd(k,j);
         int action=v[0],cha=v[1],target=v[2],hit=v[3],heading=v[4],seq=v[5],
             gfx=v[6],myX=v[7],myY=v[8],tx=v[9],ty=v[10];
         ByteBuffer b=ref(35);
         b.put((byte)Opcodes.S_ATTACK).put((byte)action)
          .putInt(cha).putInt(target).put((byte)hit).put((byte)0)
          .put((byte)heading).putInt(seq).putShort((short)gfx).put((byte)6)
          .putShort((short)myX).putShort((short)myY)
          .putShort((short)tx).putShort((short)ty)
          .put((byte)0).put((byte)0).put((byte)0).put((byte)0).put((byte)0);
         Object[] args=new Object[v.length];
         for(int j=0;j<v.length;j++)args[j]=v[j];
         eq(invoke("SC_UseAttackSkill","sharedWire",typ,args),b.array(),"attack",k);
       }
    }
    static void arrow() throws Exception {
        Class<?>[] typ={int.class,int.class,int.class,int.class,int.class,
           int.class,int.class,int.class,int.class,boolean.class,int.class};
        for(int k=0;k<512;k++){
          int a=rnd(k,0),ox=rnd(k,1),oy=rnd(k,2),heading=rnd(k,3),act=rnd(k,4),
             t=rnd(k,5),gfx=rnd(k,6),tx=rnd(k,7),ty=rnd(k,8),seq=rnd(k,9);
          boolean hit=(k&1)!=0;
          ByteBuffer b=ref(35);
          b.put((byte)Opcodes.S_ATTACK).put((byte)act)
           .putInt(a).putInt(t).put((byte)(hit?6:0)).put((byte)0).put((byte)heading)
           .putInt(seq).putShort((short)gfx).put((byte)0)
           .putShort((short)ox).putShort((short)oy)
           .putShort((short)tx).putShort((short)ty)
           .put((byte)0).put((byte)0).put((byte)0).put((byte)0).put((byte)0);
          eq(invoke("SC_UseArrowSkill","sharedWire",typ,a,ox,oy,heading,act,t,gfx,tx,ty,hit,seq),b.array(),"arrow",k);
        }
    }
    static void area()throws Exception {
        Class<?>[] types={int.class,int.class,int.class,int.class,int.class,int.class,
            int.class,int.class,int[].class,int.class};
        for(int k=0;k<512;k++){
          int count=k%47;
          int[] ids=new int[count];
          for(int j=0;j<count;j++)ids[j]=rnd(k,j);
          int id=rnd(k,0),x=rnd(k,1),y=rnd(k,2),heading=rnd(k,3),seq=rnd(k,4),
              gfx=rnd(k,5),act=rnd(k,6),type=rnd(k,7);
          ByteBuffer b=ref(24+6*count);
          b.put((byte)Opcodes.S_ATTACK_MANY).put((byte)act).putInt(id)
           .putShort((short)x).putShort((short)y).put((byte)heading)
           .putInt(seq).putShort((short)gfx).put((byte)type)
           .putShort((short)0).putShort((short)count);
          for(int j=0;j<count;j++)b.putInt(ids[j]).put((byte)32).put((byte)act);
          eq(invoke("SC_RangeSkill","sharedWire",types,id,x,y,heading,seq,gfx,act,type,ids,count),
              b.array(),"range",k);
        }
        int[] ids=new int[2800];
        if(invoke("SC_RangeSkill","sharedWire",types,1,2,3,4,5,6,7,8,ids,2800)!=null)
           throw new AssertionError("Oversized range frames must use legacy send");
    }
    static byte[] originalGround(int x,int y,int id,int gfx,int light,int count,String name) {
        int size=45+(name==null?0:name.length()*2);
        ByteBuffer b=ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        WriteBuffer w=new WriteBuffer();
        w.armExternal(b,0,size);
        w.beginDirect();w.position(2);
        try {
            w.writeC(Opcodes.S_PUT_OBJECT);
            w.writeH(x);w.writeH(y);w.writeD(id);w.writeH(gfx);
            w.writeC(0);w.writeC(0);w.writeC(light);w.writeC(0);
            w.writeD(count);w.writeC(0);w.writeC(0);
            w.writeS(name);
            w.writeC(0);w.writeD(0);w.writeD(0);w.writeC(255);
            w.writeC(0);w.writeC(0);w.writeC(0);w.writeH(65535);
            w.writeD(0);w.writeC(8);w.writeC(0);
            int len=w.position();
            b.putShort(0,(short)len);
            return Arrays.copyOf(b.array(),len);
        } finally {w.releaseResources();}
    }
    static void drop()throws Exception {
        Class<?>[] ty={int.class,int.class,int.class,int.class,int.class,int.class,String.class};
        String[] names={"","a","테스트","리니지1","漢字","⛄","😀","abcd","장검 +8","A\u0000Z"};
        for(int k=0;k<512;k++){
           int x=rnd(k,0),y=rnd(k,1),id=rnd(k,2),gfx=rnd(k,3),
              light=rnd(k,4),count=rnd(k,5);
           String name=names[k%names.length];
           byte[] actual=invoke("SC_DropItem","sharedWire",ty,x,y,id,gfx,light,count,name);
           eq(actual,originalGround(x,y,id,gfx,light,count,name),"item",k);
        }
    }
    static byte[] worldBuilder(WorldPutBuilder s)throws Exception {
        Class<?> c=SC_WorldPutObject.class;
        Method m=c.getDeclaredMethod("sharedWireBuilder",WorldPutBuilder.class);
        m.setAccessible(true);
        return (byte[])m.invoke(null,s);
    }
    static void builder()throws Exception {
        for(int i=0;i<100;i++){
            WorldPutBuilder b=new WorldPutBuilder();
            b.object_id=rnd(i,1);
            b.x=i*81;b.y=i*43;b.h=i%8;b.action=i*19;
            b.spr_id=i*91;b.light_size=i*3;b.law_full=i*13;
            b.name="NPC가나다"+i;b.title="상인"+i;b.master_name="마스터"+i;
            b.move_speed=i%4;b.brave_speed=i%3;b.emblem_id=i*7;
            b.altitude=i*4;b.hitratio=-1;b.level=i*5;
            b.weapon_sprite=i*7;b.couplestate=(i&1)!=0;
            b.boundarylevelindex=i%11;b.weakelemental=i%5;
            b.mana_ratio=i*4;b.botindex=i%3;
            byte[] frame=worldBuilder(b);
            if(frame==null || frame.length<12)throw new AssertionError("Builder serialization failed "+i);
            if(((frame[0]&255)|((frame[1]&255)<<8))!=frame.length)
               throw new AssertionError("Builder wire length mismatch");
            if((frame[2]&255)!=(Opcodes.S_EXTENDED_PROTOBUF&255))
               throw new AssertionError("Builder opcode mismatch");
            if((frame[3]&255)!=0x77 || (frame[4]&255)!=0)
               throw new AssertionError("Builder protobuf message id mismatch");
            tests++;
        }
    }
    public static void main(String[] args)throws Exception {
        attack();arrow();area();drop();builder();
        System.out.println("SHARED_FINAL_FIVE_WIRE_PARITY_OK cases="+tests);
    }
}
'''

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--input",type=pathlib.Path,default=ROOT/"downloads"/INPUT)
    p.add_argument("--output",type=pathlib.Path,default=ROOT/"downloads"/OUTPUT)
    p.add_argument("--work",type=pathlib.Path,default=pathlib.Path("/tmp/broadcast-final-five"))
    a=p.parse_args()
    blob=a.input.read_bytes()
    if SHA(blob)!=INPUT_SHA256:raise ValueError("Not the latest verified full SRC: "+SHA(blob))
    with zipfile.ZipFile(a.input) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
    orig=dict(files)
    skills_drop.integrate(files)
    source=files[WORLD_PATH].decode("utf8")
    world=world_capture.apply(source)
    assert world!=source
    files[WORLD_PATH]=world.encode("utf8")
    changed=sorted(p for p in files if p in orig and orig[p]!=files[p])
    expected={
        skills_drop.CORE+"connection/CUserSocket.java",
        skills_drop.CORE+"broadcast/CAsyncSendConstBuffer.java",
        skills_drop.CORE+"broadcast/CSharedBroadcast.java",
        skills_drop.BASE+"SC_DropItem.java",
        skills_drop.BASE+"SC_RangeSkill.java",
        skills_drop.BASE+"SC_UseArrowSkill.java",
        skills_drop.BASE+"SC_UseAttackSkill.java",
        WORLD_PATH
    }
    assert set(changed)==expected,(changed,sorted(expected))
    assert all(p.endswith(".java") for p in changed)
    assert len([p for p in files if p.endswith(".java")])==1711
    assert files["CTransport.dll"]==orig["CTransport.dll"]
    assert SHA(files["CTransport.dll"])=="0801f22fedac95ee29d33c747c5e4019c3b36d79d31615c7d84cc79405258606"
    before=orig[skills_drop.CORE+"connection/CUserSocket.java"].decode("utf8")
    after=files[skills_drop.CORE+"connection/CUserSocket.java"].decode("utf8")
    original_enqueue=before[before.index("public void enqueueConstBuffer("):before.index("public void enqueueConstBuffer(")+1700]
    assert original_enqueue in after,"Existing constant-buffer enqueue changed"
    for class_name in ("SC_UseArrowSkill","SC_UseAttackSkill","SC_RangeSkill","SC_DropItem","SC_WorldPutObject"):
        class_text=files[skills_drop.BASE+class_name+".java"].decode("utf8")
        assert "sharedWire" in class_text,class_name
    a.work.mkdir(parents=True,exist_ok=True)
    for path,data in files.items():
        if path.startswith("src/"):
            dst=a.work/path;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(data)
    test=a.work/"testsrc"/"l1j/server/server/model/network/core/sc/packet/SharedFinalFiveWireParity.java"
    test.parent.mkdir(parents=True,exist_ok=True)
    test.write_text(PARITY_SRC,encoding="utf8")
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(a.output,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path,data in sorted(files.items()):z.writestr(path,data)
    with zipfile.ZipFile(a.output) as z:
        assert z.testzip() is None
        assert len(z.namelist())==len(orig)
    report={
        "baseline_sha256":INPUT_SHA256,
        "output_sha256":SHA(a.output.read_bytes()),
        "changed_java_files":changed,
        "changed_java_count":len(changed),
        "new_shared_packet_classes":5,
        "previous_shared_classes":50,
        "all_sc_broadcast_classes_shared_or_single_snapshot":True,
        "ground_item_known_object_updates_only_after_confirmed_SEND":True,
        "full_src_java_count":1711,
        "native_dll_changed":False,
        "socket_fifo_policy_changed":False,
        "windows_runtime_tested":False,
    }
    (a.work/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
    print(json.dumps(report,ensure_ascii=False,indent=2))
if __name__=="__main__":main()

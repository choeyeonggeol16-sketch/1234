#!/usr/bin/env python3
"""Phase9: move remaining identical NPC teleport FX and treasure chest opening
from per-viewer direct encodes to one shared immutable frame each.

Never touch generic recipient-dependent ServerPacket. Preserve exact AOI sets,
FX -> object removal -> known-object mutation ordering, and native IOCP send.
"""
from __future__ import annotations
import argparse,hashlib,json,pathlib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
SOURCE=ROOT/"downloads/RKern_Broadcast_NonSC_Phase8_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase9_FULL_SRC_20261115.zip"
PIN="3a14dce40deb83013ae0aea95dd3f9893e37a446"
NPC="src/l1j/server/server/model/Instance/L1NpcInstance.java"
CHEST="src/l1j/server/MJTemplate/TreasureChest/MJL1TreasureChest.java"
SOUND="src/l1j/server/server/model/network/core/sc/packet/SC_SkillSound.java"
ACTION="src/l1j/server/server/model/network/core/sc/packet/SC_DoActionGFX.java"
SHARED="src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java"
BUFFER="l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer"

def blobsha(data:bytes)->str:
    return hashlib.sha1(b"blob "+str(len(data)).encode()+b"\0"+data).hexdigest()
def method_bounds(src:str,sig:str):
    assert src.count(sig)==1,(sig,src.count(sig))
    a=src.index(sig)
    b=src.index("{",a)
    level=0
    for i in range(b,len(src)):
        if src[i]=="{":level+=1
        if src[i]=="}":
            level-=1
            if level==0:return a,i+1,src[a:i+1]
    raise AssertionError("unterminated "+sig)

def sound(src:str)->str:
    marker="private static byte[] sharedWire(int objectId, int gfxId)"
    assert src.count(marker)==1
    assert "final int WIRE_SIZE = 2 + 13" in src
    return src.replace(marker,"public static byte[] sharedWire(int objectId, int gfxId)",1)

def npc(src:str)->str:
    a,b,method=method_bounds(src,"public void teleport(int nx, int ny, int dir) {")
    oldstart=("try(var shared=new "+BUFFER+
        "(SC_RemoveObject.broadcastWire(getId()),Integer.MIN_VALUE,Integer.MAX_VALUE)) {")
    assert method.count(oldstart)==1,"NPC shared remove resource changed"
    replacement=("try(var effectShared=new "+BUFFER+
        "(SC_SkillSound.sharedWire(getId(),169),Integer.MIN_VALUE,Integer.MAX_VALUE);\n"
        "            var shared=new "+BUFFER+
        "(SC_RemoveObject.broadcastWire(getId()),Integer.MIN_VALUE,Integer.MAX_VALUE)) {")
    method=method.replace(oldstart,replacement,1)
    send="SC_SkillSound.send(pc,getId(),169);"
    assert method.count(send)==1,"original NPC FX send changed"
    method=method.replace(send,"",1)
    needle="shared.send(socket,pc.getId());"
    assert method.count(needle)==1,"expected one NPC remove send"
    method=method.replace(needle,
        "effectShared.send(socket,pc.getId());\n"
        "                            shared.send(socket,pc.getId());",1)
    assert method.index("effectShared.send(socket,pc.getId())") < method.index("shared.send(socket,pc.getId())") < method.index("pc.removeKnownObject(this)")
    assert "L1World.getInstance().getVisiblePlayerFast(this)" in method
    return src[:a]+method+src[b:]

def chest(src:str)->str:
    a,b,method=method_bounds(src,"private void showOpen(){")
    assert "L1World.getInstance().getVisiblePlayer(this)" in method
    assert method.count("SC_DoActionGFX.send(")==1
    assert "CHEST_OPEN" in method
    assert "getId()" in method
    new="""private void showOpen(){
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                .sendRecipients(L1World.getInstance().getVisiblePlayer(this),
                        SC_DoActionGFX.broadcastWire(getId(), CHEST_OPEN));
    }"""
    return src[:a]+new+src[b:]

TEST=r'''import java.util.Arrays;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
public final class ActionEffectSharedParity9 {
    static int checks;
    static void ok(boolean v){if(!v)throw new AssertionError("parity "+checks);checks++;}
    static byte[] skill(int id,int gfx){
        byte[] b=new byte[15];b[0]=15;b[2]=(byte)Opcodes.S_EFFECT;
        b[3]=(byte)id;b[4]=(byte)(id>>>8);b[5]=(byte)(id>>>16);b[6]=(byte)(id>>>24);
        b[7]=(byte)gfx;b[8]=(byte)(gfx>>>8);
        return b;
    }
    static byte[] action(int id,int act){
        byte[] b=new byte[8];b[0]=8;b[2]=(byte)Opcodes.S_ACTION;
        b[3]=(byte)id;b[4]=(byte)(id>>>8);b[5]=(byte)(id>>>16);b[6]=(byte)(id>>>24);
        b[7]=(byte)act;return b;
    }
    public static void main(String[] unused){
        int rnd=0x10325476;
        for(int i=0;i<4096;i++){
            rnd^=rnd<<13;rnd^=rnd>>>17;rnd^=rnd<<5;
            int id=i==0?0:i==1?-1:i==2?Integer.MIN_VALUE:i==3?Integer.MAX_VALUE:rnd;
            int effect=i==0?0:i==1?169:rnd;
            int act=i==0?0:i==1?255:rnd>>>7;
            ok(Arrays.equals(skill(id,effect),SC_SkillSound.sharedWire(id,effect)));
            ok(Arrays.equals(action(id,act),SC_DoActionGFX.broadcastWire(id,act)));
        }
        System.out.println("ACTION_EFFECT_SHARED_PARITY9_OK "+checks);
    }
}'''

def main()->None:
    parser=argparse.ArgumentParser()
    parser.add_argument("--input",type=pathlib.Path,default=SOURCE)
    parser.add_argument("--output",type=pathlib.Path,default=OUTPUT)
    parser.add_argument("--work",type=pathlib.Path,default=pathlib.Path("/tmp/broadcast-nonsc-phase9"))
    args=parser.parse_args()
    srczip=args.input.read_bytes()
    assert blobsha(srczip)==PIN,"phase8 baseline changed"
    with zipfile.ZipFile(args.input) as z:
        assert z.testzip() is None
        files={x.filename:z.read(x) for x in z.infolist() if not x.is_dir()}
    prior=files.copy()
    assert sum(x.endswith(".java") for x in files)==1711
    assert "CTransport.dll" in files
    assert "public static byte[] broadcastWire(int objectId,int actionId)" in files[ACTION].decode("utf8")
    assert "public static void sendRecipients(" in files[SHARED].decode("utf8")
    for path,fn in ((SOUND,sound),(NPC,npc),(CHEST,chest)):
        src=files[path].decode("utf8")
        result=fn(src)
        assert src!=result
        files[path]=result.encode("utf8")
    changes=sorted(x for x in files if files[x]!=prior[x])
    assert changes==sorted([SOUND,NPC,CHEST]),changes
    assert files["CTransport.dll"]==prior["CTransport.dll"]
    args.work.mkdir(parents=True,exist_ok=True)
    for name,data in files.items():
        if name.startswith("src/") and name.endswith(".java"):
            target=args.work/name;target.parent.mkdir(parents=True,exist_ok=True)
            target.write_bytes(data)
    test=args.work/"testsrc/ActionEffectSharedParity9.java"
    test.parent.mkdir(parents=True,exist_ok=True)
    test.write_text(TEST)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path,data in sorted(files.items()):z.writestr(path,data)
    with zipfile.ZipFile(args.output) as z:
        assert z.testzip() is None
        assert set(z.namelist())==set(files)
    info={"phase":9,"baseline_git_blob":PIN,
          "output_sha256":hashlib.sha256(args.output.read_bytes()).hexdigest(),
          "changed_java":changes,"java_count":1711,"npc_effect_before_remove":True,
          "npc_remove_before_forget":True,"chest_original_aoi":True,
          "recipient_major_send_order":True,"wire_reused_per_event":True,
          "native_dll_unchanged":True,"no_legacy_fallback":True,
          "windows_runtime_tested":False}
    (args.work/"audit.json").write_text(json.dumps(info,indent=2)+"\n")
    print(json.dumps(info,indent=2))
if __name__=="__main__":
    main()

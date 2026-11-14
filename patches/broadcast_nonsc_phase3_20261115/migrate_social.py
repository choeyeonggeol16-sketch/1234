#!/usr/bin/env python3
"""Phase3: replace one active generic ServerPacket broadcast with shared Proto wire.

Social action handler previously sent one ProtoPacket to self and serialized
same packet again per visible player. Reuses existing encodeFields exactly once
and sends one frozen immutable frame to self (first) and visible players.
No legacy callback/fallback, no new worker, queue, or class. Other generic
ServerPacket callers are NOT converted until recipient-independence is verified.
"""
from __future__ import annotations
import argparse,hashlib,json,pathlib,re,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
IN=ROOT/"downloads/RKern_Broadcast_NonSC_Phase2_FULL_SRC_20261115.zip"
HASH="c3788d17f4b3e66f41a86205e59b8f16ce3b06b45f9bfedd3d26abe054a15f91"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase3_FULL_SRC_20261115.zip"
FILES={
  "broadcast":"src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java",
  "actionui":"src/l1j/server/server/model/network/core/cs/packet/CS_ActionUi.java",
  "social":"src/l1j/server/server/packets/Protocol/MainServer/Proto/SC_SOCIAL_ACTION_NOTI.java",
}
SHA=lambda b:hashlib.sha256(b).hexdigest()

NEW_SHARED=r'''
    /**
     * Send a single cached protobuf frame to the triggering PC first, then to
     * every visible PC in the original world traversal order. This preserves
     * the exact recipients and the prior own-send-first order, while removing
     * repeated ServerPacket.writeImpl() calls. No per-recipient encoder.
     */
    public static void sendRawSelfAndVisible(L1PcInstance self,byte[] encoded) {
        if(self==null)return;
        final byte[] wire=frameRaw(encoded);
        if(wire==null)return;
        try(var shared=new CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            final CIOClient own=self.getNetConnection();
            if(own!=null && own.isConnected())shared.send(own.getConnection(),self.getId());
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayer(self)) {
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client!=null && client.isConnected())
                    shared.send(client.getConnection(),pc.getId());
            }
        }
    }
'''
def replace_one(s,old,new,label):
    if s.count(old)!=1:raise ValueError(label+" expected one match; got "+str(s.count(old)))
    return s.replace(old,new,1)

def broadcast(s):
    if "public static void sendRawSelfAndVisible(" in s:
        raise ValueError("Already migrated")
    marker="    /** Global broadcast includes the sender and uses the normal socket FIFO. */"
    if marker not in s:
        marker="    public static void sendAll(byte[] wire)"
    if marker not in s:raise ValueError("CSharedBroadcast insertion point not found")
    if "private static byte[] frameRaw(byte[] encoded)" not in s:
        raise ValueError("Expected existing recipient-independent raw framing")
    return s.replace(marker,NEW_SHARED+"\n"+marker,1)

def method(s,needle):
    start=s.index(needle)
    if s.count(needle)!=1:raise ValueError("ambiguous method "+needle)
    brace=s.find('{',start)
    depth=0
    for i in range(brace,len(s)):
        if s[i]=='{':depth+=1
        elif s[i]=='}':
            depth-=1
            if depth==0:return (start,i+1,s[start:i+1])
    raise ValueError("unterminated method "+needle)

def actionui(s):
    start,end,old=method(s,"private static void handleSocialAction(")
    if old.count("SC_SOCIAL_ACTION_NOTI.get(")!=1:
        raise ValueError("Unexpected social action factory")
    if old.count("pc.broadcastPacket(")!=1 or old.count("pc.sendPackets(")!=1:
        raise ValueError("Unexpected social action send order")
    if not re.search(r'final\s+ServerPacket\s+packet\s*=',old):
        raise ValueError("Expected original ServerPacket variable")
    # Source expression ensures same ID, type and code fields as original
    new='''private static void handleSocialAction(
            L1PcInstance pc,
            int actionType,
            int code) {
        final byte[] encoded=SC_SOCIAL_ACTION_NOTI.encodeFields(
                pc.getId(),actionType,code);
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                .sendRawSelfAndVisible(pc,encoded);
    }'''
    return s[:start]+new+s[end:]

TEST=r'''import java.util.*;
import java.lang.reflect.*;
import l1j.server.server.model.network.core.broadcast.CSharedBroadcast;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SOCIAL_ACTION_NOTI;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.ProtoMsg;
public final class SocialActionSharedParity {
    static int checks;
    static void verify(boolean ok,String s){if(!ok)throw new AssertionError(s);checks++;}
    static byte[] frame(byte[] encoded)throws Exception{
        Method m=CSharedBroadcast.class.getDeclaredMethod("frameRaw",byte[].class);
        m.setAccessible(true);
        return (byte[])m.invoke(null,(Object)encoded);
    }
    public static void main(String[] args)throws Exception{
        int[] special={0,1,127,128,255,256,16384,32767,65535,Integer.MAX_VALUE,
                -1,Integer.MIN_VALUE};
        for(int i=0;i<512;i++){
            final int object=special[i%special.length]^((i+1)*0x9e3779b9);
            final int action=special[(i+3)%special.length]^((i+1)*0x45d9f3b);
            final int code=special[(i+8)%special.length]^((i+1)*0x7feb352d);
            final byte[] once=SC_SOCIAL_ACTION_NOTI.encodeFields(object,action,code);
            final ProtoWriter writer=ProtoWriter.tl();
            writer.int32(1,object).int32(2,action).int32(3,code);
            final byte[] reference=writer.finishWithTrailer(ProtoMsg.SC_SOCIAL_ACTION_NOTI);
            verify(Arrays.equals(once,reference),"social proto encoding "+i);
            final byte[] shared=frame(once);
            verify(shared!=null && shared.length==once.length+2,"framed size "+i);
            verify((shared[0]&255)|((shared[1]&255)<<8)==shared.length,"LE header "+i);
            verify(Arrays.equals(once,Arrays.copyOfRange(shared,2,shared.length)),"payload "+i);
            once[0]^=0x5a;
            verify(shared[2]!=once[0],"immutable ownership "+i);
        }
        System.out.println("SOCIAL_ACTION_SHARED_WIRE_PARITY_OK cases="+checks);
    }
}
'''

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--input",type=pathlib.Path,default=IN)
    p.add_argument("--output",type=pathlib.Path,default=OUT)
    p.add_argument("--work",type=pathlib.Path,default=pathlib.Path("/tmp/broadcast-nonsc-phase3"))
    args=p.parse_args()
    if SHA(args.input.read_bytes())!=HASH:raise ValueError("Not the verified phase2 source ZIP")
    with zipfile.ZipFile(args.input) as z:
        assert z.testzip() is None
        contents={f.filename:z.read(f) for f in z.infolist() if not f.is_dir()}
    previous=dict(contents)
    # Find the class in its exact canonical post-protocol-migration package.
    social=[x for x in contents if x.endswith("/SC_SOCIAL_ACTION_NOTI.java") and x.startswith("src/")]
    if len(social)!=1:raise ValueError("Missing/ambiguous social packet class "+str(social))
    code=contents[social[0]].decode("utf8")
    if "static byte[] encodeFields(int " not in code:
        raise ValueError("Not a proven pure fixed-field social encoder")
    files_changed=[]
    for name,edit in (("broadcast",broadcast),("actionui",actionui)):
        path=FILES[name]
        src=contents[path].decode("utf8")
        out=edit(src)
        if out==src:raise AssertionError("No source changed "+path)
        contents[path]=out.encode("utf8")
        files_changed.append(path)
    assert sum(x.endswith(".java") for x in contents)==1711
    assert contents["CTransport.dll"]==previous["CTransport.dll"]
    assert len(files_changed)==2
    for x in contents:
        if x not in files_changed and previous[x]!=contents[x]:
            raise AssertionError("Unexpected file edit "+x)
    args.work.mkdir(parents=True,exist_ok=True)
    for path,data in contents.items():
        if path.startswith("src/"):
            dest=args.work/path;dest.parent.mkdir(parents=True,exist_ok=True)
            dest.write_bytes(data)
    test=args.work/"testsrc/SocialActionSharedParity.java"
    test.parent.mkdir(parents=True,exist_ok=True)
    # Dynamically reflect the canonical class package in test compilation.
    pkg=re.search(r"(?m)^package\s+([\w.]+)\s*;",code)
    if not pkg:raise ValueError("Social Proto source lacks package")
    test.write_text(TEST.replace(
        "l1j.server.server.packets.Protocol.MainServer.Proto.SC_SOCIAL_ACTION_NOTI",
        pkg.group(1)+".SC_SOCIAL_ACTION_NOTI"),encoding="utf8")
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path,data in sorted(contents.items()):z.writestr(path,data)
    with zipfile.ZipFile(args.output) as z:assert z.testzip() is None
    data=dict(input_sha256=HASH,output_sha256=SHA(args.output.read_bytes()),
              modified_files=files_changed,modified_count=2,
              removed_generic_SocialAction_writeImpl_per_viewer=True,
              raw_proto_encoded_once=True,recipient_self_send_first=True,
              no_legacy_packet_fallback=True,full_java_sources=1711,
              native_dll_unchanged=True,windows_runtime_tested=False)
    (args.work/"audit.json").write_text(json.dumps(data,indent=2,ensure_ascii=False)+"\n")
    print(json.dumps(data,indent=2,ensure_ascii=False))
if __name__=="__main__":main()

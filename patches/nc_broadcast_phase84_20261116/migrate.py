#!/usr/bin/env python3
"""Phase84: eliminate remaining explicit preframed Java byte-array broadcast producers.
Use exact 83 SHA, change only identified production paths; no workers/queues/classes.
"""
from pathlib import Path
import hashlib,re,zipfile,json,shutil,collections,sys
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase83_FULL_SRC_20261116.zip"
PIN="d3dc7bb184034fd7ba2526087943372ed788b7756888e11f05864ae516a752f8"
OUT=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase84_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else None
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"
PK="src/l1j/server/server/model/network/core/sc/packet/"
P="src/l1j/server/server/model/network/core/"
changed={}
counts=collections.Counter()
def apply(files,key,old,new,expected=1,tag=None):
    s=files[key]
    n=s.count(old)
    assert n==expected,(key,tag,n,old[:180])
    files[key]=s.replace(old,new)
    counts[tag or key]+=expected
def pre(name):
    return "src/l1j/server/"+name

def modify(files):
    # Core factory for remaining preframed small attribute packets.
    core=P+"broadcast/CSharedBroadcast.java"
    anchor="    public static NativeWireWriter moveObjectWriter(int id,int x,int y,int heading) {"
    new="""    public static NativeWireWriter changeAttrWriter(int x,int y,
            boolean width,boolean closed) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_CHANGE_ATTR)
                .putShort((short)x).putShort((short)y)
                .put((byte)(width?1:0)).put((byte)(closed?1:0));
    }
    public static NativeWireWriter iconIndexWriter(int index,
            l1j.server.MJTemplate.MJSimpleRgb color,int surface,int duration) {
        // Match the existing SC_IconMessage.sharedIndexWire varint wire.
        final byte[] rgb=color==null?null:color.get_bytes().clone();
        return b -> {
            b.put((byte)l1j.server.server.Opcodes.S_EXTENDED_PROTOBUF)
             .putShort((short)0x67).put((byte)0x08);
            nativeVarint(b,(long)surface*2);
            b.put((byte)0x10);nativeVarint(b,(long)index*2);
            b.put((byte)0x1a);
            if(rgb!=null)b.put(rgb);
            b.put((byte)0x20);nativeVarint(b,duration);
            b.putShort((short)0);
        };
    }
    private static void nativeVarint(java.nio.ByteBuffer b,long value){
        if(value<0L)value&=0xffffffffL;
        while((value & ~0x7fL)!=0L){
            b.put((byte)((value&0x7fL)|0x80L));
            value>>>=7;
        }
        b.put((byte)value);
    }

"""
    apply(files,core,anchor,new+anchor,tag="native_attr_and_icon_index")

    # SC_ChartRank has an already-captured immutable Snapshot with size.
    chart=PK+"SC_ChartRank.java"
    anchor="    public static byte[] sharedWire(Snapshot snapshot) {"
    new="""    /** Directly write the original chart snapshot into one native slab.
     * The original snapshot is captured before any recipient is inspected. */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter
            nativeWriter(Snapshot snapshot) {
        if(snapshot==null||snapshot.wireSize>0xffff)return null;
        return new l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter(){
            @Override public void write(java.nio.ByteBuffer out) {
                out.put((byte)Opcodes.S_EXTENDED_PROTOBUF);
                out.putShort((short)SC_BASECAMP_CHART_NOTI_PACKET);
                if(snapshot.zero) {
                    out.put((byte)0x10).put((byte)0).putShort((short)0);
                } else {
                    for(byte[] payload:snapshot.serialized) {
                        out.put((byte)0x0a);
                        putBit(out,payload.length);
                        out.put(payload);
                    }
                    out.put((byte)0x10);
                    putBit(out,snapshot.serverPoint);
                    out.putShort((short)0);
                }
                if(out.position()!=snapshot.wireSize)
                    throw new IllegalStateException("Native chart length");
            }
            @Override public int wireLength() {return snapshot.wireSize;}
        };
    }

"""
    apply(files,chart,anchor,new+anchor,tag="native_chart_snapshot")
    # SC_NewChat already has native writer function; expose it to CS_Craft.
    chat=PK+"SC_NewChat.java"
    anchor="    public static byte[] sharedWire(ChatData d) {"
    new="""    /** Reuses the very same Native encoder as global chat broadcasts.
     * ChatData already snapshots text/sender MS949 and world state once. */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter
            nativeWriter(ChatData data) {
        return data==null?null:b -> writeNativeChat(b,data);
    }

"""
    apply(files,chat,anchor,new+anchor,tag="native_chat_snapshot")
    # Castle roster: source array is copied once before recipients, no heap wire.
    roster=PK+"SC_WarStartMessage.java"
    anchor="    public static byte[] sharedWire(String[] clanNames) {"
    new="""    /** NC-like pooled broadcast with immutable town names. */
    public static l1j.server.server.model.network.core.broadcast.CSharedBroadcast.NativeWireWriter
            nativeWriter(String[] clanNames) {
        if(clanNames==null)throw new IllegalArgumentException("clan names");
        final String[] snapshot=clanNames.clone();
        return b -> {
            b.put((byte)Opcodes.S_EVENT).put((byte)0x4e).put((byte)0x07);
            for(String name:snapshot){
                l1j.server.server.model.network.core.broadcast.CSharedBroadcast.writeMS949(b,name);
                b.put((byte)0);
            }
        };
    }

"""
    apply(files,roster,anchor,new+anchor,tag="native_roster_snapshot")
    # Dungeon notices
    key=pre("IndunSystem/Gludio/GludioInstanceDungeon.java")
    old="""        final byte[] wire = l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.sharedWire(msg);
        if (wire == null) return;
        try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                wire, Integer.MIN_VALUE, Integer.MAX_VALUE)) {"""
    new="""        try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                """+FQ+""".systemWriter(msg),Integer.MIN_VALUE,Integer.MAX_VALUE)) {"""
    apply(files,key,old,new,tag="dungeon_notice")
    # Gambling and Orim chat: keep exact chat formatting and self/send interleaving.
    for suffix in ("IndunSystem/MiniGame/L1Gambling.java","IndunSystem/MiniGame/L1Gambling3.java"):
        key=pre(suffix)
        old="""        final byte[] chatWire=SC_ChatPacket.sharedWire(Opcodes.S_SAY,0,pc.getId(),
                    (senderName==null?"":senderName)+": "+(chatText==null?"":chatText),true);
        try(var chatShared=chatWire==null?null:new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    chatWire,Integer.MIN_VALUE,Integer.MAX_VALUE)){"""
        new="""        final String frozenChat=(senderName==null?"":senderName)+": "
                +(chatText==null?"":chatText);
        try(var chatShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                """+FQ+""".chatWriter(Opcodes.S_SAY,0,pc.getId(),frozenChat,true),
                Integer.MIN_VALUE,Integer.MAX_VALUE)){"""
        apply(files,key,old,new,tag="gambling_chat")
    key=pre("IndunSystem/Orim/OrimController.java")
    expr=r"final byte\[\]\s+chatWire=SC_ChatPacket\.sharedWire\(\s*l1j\.server\.server\.Opcodes\.S_SAY,15,pc\.getId\(\),msg,true\);\s*try\(var shared=chatWire==null\?null:new l1j\.server\.server\.model\.network\.core\.broadcast\.CAsyncSendConstBuffer\(\s*chatWire,Integer\.MIN_VALUE,Integer\.MAX_VALUE\)\)\s*\{"
    replacement="""try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    """+FQ+""".chatWriter(l1j.server.server.Opcodes.S_SAY,15,pc.getId(),msg,true),
                    Integer.MIN_VALUE,Integer.MAX_VALUE)){"""
    patched,n=re.subn(expr,lambda _:replacement,files[key])
    assert n==1,(key,"orim_chat",n)
    files[key]=patched
    counts["orim_chat"]+=1
    # KDA Chart Snapshot, preserving pre-existing chart view predicates.
    key=pre("MJKDASystem/Chart/MJKDAChartUpdator.java")
    old="""                    final byte[] wire=SC_ChartRank.sharedWire(_pck);
                    if(wire!=null){
                        try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)){"""
    new="""                    final var writer=SC_ChartRank.nativeWriter(_pck);
                    if(writer!=null){
                        try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                                writer,Integer.MIN_VALUE,Integer.MAX_VALUE)){"""
    apply(files,key,old,new,tag="kda_chart")
    # Three raid scenarios: conditional fixed message type, no byte-array allocation.
    for suffix in ("MJRaidSystem/Objects/MJRaidAntaras.java","MJRaidSystem/Objects/MJRaidFafurion.java","MJRaidSystem/Objects/MJRaidRindvior.java"):
        key=pre(suffix)
        old="""                  i<s_startMessages.length
                    ? SC_ServerMessage.sharedWire(s_startMessages[i])
                    : SC_SystemMessage.sharedWire(s_lastMessage),"""
        new="""                  i<s_startMessages.length
                    ? """+FQ+""".serverMessageWriter(s_startMessages[i])
                    : """+FQ+""".systemWriter(s_lastMessage),"""
        apply(files,key,old,new,tag="raid_conditional_notice")
    # Precomputed door coordinate broadcasts -> Native source.
    key=pre("MJTemplate/L1Instance/MJL1LiftGateInstance.java")
    old="""                final byte[] wire=SC_ChangeAttr.broadcastWire(_attrCoordinates[idx],
                        _attrCoordinates[idx+1],_isWidth,closed);
                frames[made++]=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                        wire,Integer.MIN_VALUE,Integer.MAX_VALUE);"""
    new="""                frames[made++]=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                        """+FQ+""".changeAttrWriter(_attrCoordinates[idx],
                                _attrCoordinates[idx+1],_isWidth,closed),
                        Integer.MIN_VALUE,Integer.MAX_VALUE);"""
    apply(files,key,old,new,tag="lift_gate_attr")
    # Two interleaved castle war notices, preserve order.
    key=pre("MJWarSystem/MJCastleWar.java")
    old="""        final byte[] iconWire=SC_IconMessage.sharedIndexWire(
                1510,MJSimpleRgb.red(),6298,10);
        final byte[] rosterWire=SC_WarStartMessage.sharedWire(names);
        try(var iconShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    iconWire,Integer.MIN_VALUE,Integer.MAX_VALUE);
            var rosterShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    rosterWire,Integer.MIN_VALUE,Integer.MAX_VALUE)){"""
    new="""        try(var iconShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    """+FQ+""".iconIndexWriter(1510,MJSimpleRgb.red(),6298,10),
                    Integer.MIN_VALUE,Integer.MAX_VALUE);
            var rosterShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    SC_WarStartMessage.nativeWriter(names),Integer.MIN_VALUE,Integer.MAX_VALUE)){"""
    apply(files,key,old,new,tag="castle_war")
    # Three staff announcements: avoid framed wire arrays.
    key=pre("SpecialEventHandler.java")
    old="final byte[] noticeWire=SC_SystemMessage.sharedWire("
    # Each literal message is a string and the broadcast constructor always
    # consumes the same frozen variable, so replace to typed Native writer.
    apply(files,key,old,"final "+FQ+".NativeWireWriter noticeWire="+FQ+".systemWriter(",3,tag="staff_announcement")
    # Event staff NPC chat
    key=pre("server/model/L1HauntedHouse.java")
    expr=r"final byte\[\] wire=SC_NpcChatPacket\.sharedNpcWire\(_guide,msg,2\);\s*if\(wire==null\)return;\s*try\(var shared=new l1j\.server\.server\.model\.network\.core\.broadcast\.CAsyncSendConstBuffer\(\s*wire,Integer\.MIN_VALUE,Integer\.MAX_VALUE\)\)\s*\{"
    replacement="""final var writer="""+FQ+""".npcChatWriter(_guide,msg,2);
        if(writer==null)return;
        try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                writer,Integer.MIN_VALUE,Integer.MAX_VALUE)) {"""
    patched,n=re.subn(expr,lambda _:replacement,files[key])
    assert n==1,(key,"haunted_npc_chat",n)
    files[key]=patched
    counts["haunted_npc_chat"]+=1
    # Public chat channels: writer captures ChatData and builds native frame
    # only if any accepted recipient remains after spam/AOI filters.
    key=pre("server/model/network/core/cs/packet/CS_Craft.java")
    old="""final byte[] chatBytes=SC_NewChat.sharedWire("""
    new="""final var chatBytes=SC_NewChat.nativeWriter("""
    apply(files,key,old,new,6,tag="all_local_chat_channels")
    old="final byte[] chatWire=(chatType==3 || chatType==12)"
    new="final "+FQ+".NativeWireWriter chatWire=(chatType==3 || chatType==12)"
    apply(files,key,old,new,tag="global_chat_type")
    old="SC_NewChat.sharedWire(SC_NewChat.prepare("
    new="SC_NewChat.nativeWriter(SC_NewChat.prepare("
    apply(files,key,old,new,tag="global_chat_writer")
    # Raw social action packet was split around a line break; fix the
    # previously missed call while preserving exact self/visible order.
    key=pre("server/model/network/core/cs/packet/CS_ActionUi.java")
    old=".sendRawSelfAndVisible(pc,encoded);"
    new=".sendWrittenRawSelfAndVisible(pc,encoded);"
    apply(files,key,old,new,tag="raw_self_and_visible")

def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        names=z.namelist()
        original={n:z.read(n) for n in names if not n.endswith("/")}
    files={}
    for name,data in original.items():
        if name.endswith(".java") and name.startswith("src/"):
            files[name]=data.decode("utf-8").replace("\r\n","\n").replace("\r","\n")
    before=files.copy()
    modify(files)
    changed=[]
    for name,s in files.items():
        if s!=before[name]:
            changed.append(name)
            original_data=original[name].decode("utf-8")
            original[name]=(s.replace("\n","\r\n") if "\r\n" in original_data else s).encode("utf-8")
    for name,data in original.items():
        if (name.startswith("src/") and name.endswith(".java")) or name.startswith("native/"):
            p=WORK/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    native_root=ROOT/"patches/ctransport_core_20261112/native"
    for source in native_root.iterdir():
        if source.is_file() and not (WORK/"native"/source.name).exists():
            p=WORK/"native"/source.name;p.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,p)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for path in ("build","logs"):(WORK/path).mkdir(exist_ok=True)
    meta={"changed":sorted(changed),"counts":dict(counts),"baseline":PIN}
    (WORK/"phase84_changed.json").write_text(json.dumps(meta,ensure_ascii=False,indent=2)+"\n")
    print("PHASE84_NATIVE_VARIABLES_CONVERTED",json.dumps(meta,ensure_ascii=False)[:8000],flush=True)
def package():
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist()
        old={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    assert (WORK/"build/CTransport.dll").read_bytes()==old["CTransport.dll"]
    new=old.copy()
    meta=json.loads((WORK/"phase84_changed.json").read_text())
    for name in meta["changed"]:new[name]=(WORK/name).read_bytes()
    actual=sorted(n for n in old if old[n]!=new[n])
    assert actual==meta["changed"]
    assert len(new)==1734 and sum(n.endswith(".java") for n in new)==1710
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:z.writestr(info,b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and len(z.namelist())==1734
    report={"phase":84,"baseline_sha256":PIN,
        "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "dll_sha256":hashlib.sha256(old["CTransport.dll"]).hexdigest(),
        "native_abi":"0x000a0009","changed":actual,
        "converted":meta["counts"],"java_count":1710,"zip_entries":1734,
        "remaining_legacy_api_cleanup":"next",
        "new_classes_workers_queues":0,"5000_ingame_soak_verified":False}
    (WORK/"audit84.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print("PHASE84_PACKAGE_OK",json.dumps({"sha":report["full_src_sha256"],
            "changed":len(actual),"converted":sum(meta["counts"].values())}),flush=True)
if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()

#!/usr/bin/env python3
"""Phase82: remove Java byte[] for the high-volume NC-style non-SC shared-broadcast sources.

Pinned input is the full, Windows-tested Audit81 ZIP. No legacy fallback/new
queues/workers/source classes. All changes happen in this isolated copy.
"""
from pathlib import Path
import hashlib, json, re, sys, zipfile, shutil
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SPacket_Assemble_Audit81_FULL_SRC_20261116.zip"
PIN="6b077cbbafdc1067e8a5c31e9781dfe4221c78331b7bf3cde4659a0c631bb0c3"
OUT=ROOT/"downloads/RKern_NC_NonSC_Native_Broadcast_Phase82_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else None
P="src/l1j/server/server/model/network/core/"
B=P+"broadcast/CSharedBroadcast.java"
S=P+"broadcast/CAsyncSendConstBuffer.java"
Q=P+"sc/packet/"
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"
FQT=FQ+".NativeWireWriter"
CHANGED=set()
REPLACED={}

def load():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN, "Audit81 baseline SHA mismatch"
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos=z.infolist()
        raw={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    assert len(raw)==1734 and sum(n.endswith(".java") for n in raw)==1710
    return infos,raw

def edit(files,path,fn):
    old=files[path].decode("utf-8")
    crlf="\r\n" in old
    before=old.replace("\r\n","\n")
    after=fn(before)
    assert after!=before,("unchanged",path)
    files[path]=(after.replace("\n","\r\n") if crlf else after).encode("utf-8")
    CHANGED.add(path)
    return after

def replace(s,old,new):
    assert s.count(old)==1,(old[:120],s.count(old))
    return s.replace(old,new,1)

def append(s,method):
    where=s.rfind("}")
    assert where>=0
    return s[:where]+"\n"+method.rstrip()+"\n"+s[where:]

def core_source(s):
    s=replace(s,"    private final byte[] wire;","    private final byte[] wire;\n    private final CSharedBroadcast.NativeWireWriter written;")
    s=replace(s,"        wire = owned ? frame : frame.clone();","        wire = owned ? frame : frame.clone();\n        written = null;")
    s=replace(s,"        wire = null;\n        source = pooled;","        wire = null;\n        written = null;\n        source = pooled;")
    signature="    protected CAsyncSendConstBuffer(Source pooled, int senderId, int pendingLimit) {"
    constructor="""    /** Delayed Native Assemble; no Java wire array, and no source for zero viewers.
     * The first admitted socket owns the one immutable pooled frame.
     */
    public CAsyncSendConstBuffer(CSharedBroadcast.NativeWireWriter writer,
                                 int senderId,int pendingLimit) {
        if(writer==null || pendingLimit<=0)
            throw new IllegalArgumentException("native writer/limit");
        this.wire=null;
        this.written=writer;
        this.senderId=senderId;
        this.pendingLimit=pendingLimit;
    }

"""
    s=replace(s,signature,constructor+signature)
    old="""        final int limit=recipientId==senderId?Integer.MAX_VALUE:pendingLimit;
        final int length = wire == null ? source.wire.remaining() : wire.length;"""
    new="""        final int limit=recipientId==senderId?Integer.MAX_VALUE:pendingLimit;
        if(source==null && written!=null) {
            final int size=written.wireLength();
            if(size==0)return false;
            if(size>16384) {
                // Valid protocol frame larger than the 16KiB Native source:
                // same existing direct CIO FIFO, serialized per destination.
                return CSharedBroadcast.sendLargeDirectFrame(target,size,written);
            }
            final Source opened=openWrittenSource(target,written);
            if(opened==null)throw new IllegalStateException(
                    "Native non-SC shared source unavailable");
            source=opened;
        }
        final int length = wire == null ? source.wire.remaining() : wire.length;"""
    s=replace(s,old,new)
    return s

def core_broadcast(s):
    s=replace(s,"    public interface NativeWireWriter {\n        void write(java.nio.ByteBuffer output);\n    }",
"""    public interface NativeWireWriter {
        void write(java.nio.ByteBuffer output);
        /** -1: guaranteed <=16KiB; >16KiB: exact framed size; 0: refused. */
        default int wireLength(){return -1;}
    }""")
    s=replace(s,"    private static final java.nio.charset.Charset MS949 =",
"""    /**
     * Short text: guaranteed to fit one 16KiB Native slab without a byte[]
     * probe. Only unusually long text must calculate actual encoded length.
     */
    public static NativeWireWriter sizedMS949(String message,int fixed,
            NativeWireWriter writer) {
        if(writer==null)return null;
        final String text=message==null?"":message;
        final long upper=(long)fixed+((long)text.length()<<1);
        if(upper<=16384L)return writer;
        final long actual=(long)fixed+text.getBytes(MS949).length;
        if(actual>0xFFFFL)return null;
        return sizedWriter((int)actual,writer);
    }
    public static NativeWireWriter sizedWriter(int length,NativeWireWriter writer) {
        if(length<=0||length>0xFFFF||writer==null)return null;
        if(length<=16384)return writer;
        return new NativeWireWriter() {
            public void write(java.nio.ByteBuffer b){writer.write(b);}
            public int wireLength(){return length;}
        };
    }

    private static final java.nio.charset.Charset MS949 =""")
    s=replace(s,"    private static boolean sendLargeDirectFrame(","    static boolean sendLargeDirectFrame(")
    overloads="""
    /** Native writer overloads: exact original selection, order and duplicates,
     * including mutable world iterations and all existing per-recipient effects.
     * The legacy byte[] overloads remain only while other packet kinds migrate.
     */
    public static void sendObjectPlayers(
            Iterable<? extends L1Object> objects,NativeWireWriter writer) {
        if(objects==null||writer==null)return;
        try(var shared=new CAsyncSendConstBuffer(writer,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1Object o:objects)
                if(o instanceof L1PcInstance pc)sendRecipient(shared,pc);
        }
    }
    public static void sendRecipients(
            Iterable<? extends L1PcInstance> recipients,NativeWireWriter writer) {
        sendWrittenRecipients(recipients,writer);
    }
    public static void sendArray(L1PcInstance[] members,NativeWireWriter writer) {
        if(members==null||writer==null)return;
        try(var shared=new CAsyncSendConstBuffer(writer,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance pc:members)sendRecipient(shared,pc);
        }
    }
    public static void sendGroups(Iterable<? extends L1PcInstance> first,
            Iterable<? extends L1PcInstance> second,NativeWireWriter writer) {
        if(writer==null)return;
        try(var shared=new CAsyncSendConstBuffer(writer,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            sendGroup(first,shared);sendGroup(second,shared);
        }
    }
    public static void sendAllGms(NativeWireWriter writer) {
        if(writer==null)return;
        try(var shared=new CAsyncSendConstBuffer(writer,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance pc:L1World.getInstance().getAllPlayers())
                if(pc!=null&&pc.isGm())sendRecipient(shared,pc);
        }
    }
    public static void sendAllPlayable(NativeWireWriter writer) {
        if(writer==null)return;
        try(var shared=new CAsyncSendConstBuffer(writer,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance pc:L1World.getInstance().getAllPlayers())
                if(pc!=null&&!pc.isPrivateShop()&&pc.is_world())
                    sendRecipient(shared,pc);
        }
    }
    public static void sendMapTeam(int map,int team,NativeWireWriter writer) {
        if(writer==null)return;
        try(var shared=new CAsyncSendConstBuffer(writer,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayers(map))
                if(pc!=null&&pc.get_current_combat_team_id()==team)
                    sendRecipient(shared,pc);
        }
    }
    public static void sendMapTeamFrames(int map,int team,boolean teamOnly,
            NativeWireWriter[] writers) {
        if(writers==null||writers.length==0)return;
        final CAsyncSendConstBuffer[] shared=new CAsyncSendConstBuffer[writers.length];
        int opened=0;
        try {
            for(int i=0;i<writers.length;i++) {
                if(writers[i]==null)return;
                shared[i]=new CAsyncSendConstBuffer(
                    writers[i],Integer.MIN_VALUE,Integer.MAX_VALUE);
                opened++;
            }
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayers(map)) {
                if(pc==null||(teamOnly&&pc.get_current_combat_team_id()!=team))
                    continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket==null||!socket.isClientConnected())continue;
                for(int i=0;i<opened;i++)shared[i].send(socket,pc.getId());
            }
        } finally {
            for(int i=0;i<opened;i++)shared[i].close();
        }
    }
    public static void sendNestedVisible(L1Object origin,NativeWireWriter writer) {
        if(origin==null||writer==null)return;
        final L1World world=L1World.getInstance();
        try(var shared=new CAsyncSendConstBuffer(writer,Integer.MIN_VALUE,Integer.MAX_VALUE)){
            for(L1PcInstance anchor:world.getVisiblePlayer(origin)) {
                if(anchor==null)continue;
                for(L1PcInstance pc:world.getVisiblePlayer(anchor)) {
                    if(CTLMFilter.sameWorldOther(anchor,pc))sendRecipient(shared,pc);
                }
            }
        }
    }
    public static void sendAll(NativeWireWriter writer) {sendWrittenAll(writer);}
"""
    return append(s,overloads)

WRITERS={
"SC_SystemMessage":"""
    public static CSharedBroadcast.NativeWireWriter nativeWire(String message) {
        final String text=message==null?"":message;
        return CSharedBroadcast.sizedMS949(text,5,b->{
            b.put((byte)Opcodes.S_MESSAGE).put((byte)0x09);
            CSharedBroadcast.writeMS949(b,text);b.put((byte)0);
        });
    }
""",
"SC_ServerMessage":"""
    public static CSharedBroadcast.NativeWireWriter nativeWire(int type) {
        return b->b.put((byte)Opcodes.S_MESSAGE_CODE).putShort((short)type).put((byte)0);
    }
    public static CSharedBroadcast.NativeWireWriter nativeWire(int type,String message) {
        if(message==null)return nativeWire(type);
        return CSharedBroadcast.sizedMS949(message,7,b->{
            b.put((byte)Opcodes.S_MESSAGE_CODE).putShort((short)type).put((byte)1);
            CSharedBroadcast.writeMS949(b,message);b.put((byte)0);
        });
    }
    public static CSharedBroadcast.NativeWireWriter nativeWire(int type,
            String msg1,String msg2,String msg3) {
        final int count=(msg1==null?0:1)+(msg2==null?0:1)+(msg3==null?0:1);
        final long upper=6L+count+2L*((msg1==null?0:msg1.length())
             +(msg2==null?0:msg2.length())+(msg3==null?0:msg3.length()));
        final CSharedBroadcast.NativeWireWriter writer=b->{
            b.put((byte)Opcodes.S_MESSAGE_CODE).putShort((short)type).put((byte)count);
            if(msg1!=null){CSharedBroadcast.writeMS949(b,msg1);b.put((byte)0);}
            if(msg2!=null){CSharedBroadcast.writeMS949(b,msg2);b.put((byte)0);}
            if(msg3!=null){CSharedBroadcast.writeMS949(b,msg3);b.put((byte)0);}
        };
        if(upper<=16384)return writer;
        final long exact=6L+count+
           (msg1==null?0:msg1.getBytes(java.nio.charset.Charset.forName("MS949")).length)+
           (msg2==null?0:msg2.getBytes(java.nio.charset.Charset.forName("MS949")).length)+
           (msg3==null?0:msg3.getBytes(java.nio.charset.Charset.forName("MS949")).length);
        return exact>0xFFFFL?null:CSharedBroadcast.sizedWriter((int)exact,writer);
    }
""",
"SC_DisplayEffect":"""
    public static CSharedBroadcast.NativeWireWriter nativeWire(int value) {
        return b->b.put((byte)Opcodes.S_EVENT).put((byte)0x53)
                   .putInt(value).putShort((short)0);
    }
""",
"SC_SkillSound":"""
    public static CSharedBroadcast.NativeWireWriter nativeWire(int objectId,int gfxId) {
        return b->b.put((byte)Opcodes.S_EFFECT).putInt(objectId)
                   .putShort((short)gfxId).putShort((short)0).putInt(0);
    }
""",
"SC_TrueTargetNew":"""
    public static CSharedBroadcast.NativeWireWriter nativeWire(int targetId,boolean active) {
        return b->b.put((byte)Opcodes.S_EVENT).put((byte)0xc2).putInt(targetId)
                   .put((byte)0x4f).put((byte)0x33).putShort((short)0)
                   .putInt(active?1:0).putShort((short)0);
    }
""",
"SC_MessageYN":"""
    public static CSharedBroadcast.NativeWireWriter nativeIndexedWire(
            int idx,int type,String message) {
        final String text=message==null?"":message;
        return CSharedBroadcast.sizedMS949(text,12,b->{
            b.put((byte)Opcodes.S_ASK).putShort((short)0).putInt(idx)
             .putShort((short)type);
            CSharedBroadcast.writeMS949(b,text);b.put((byte)0);
        });
    }
    public static CSharedBroadcast.NativeWireWriter nativeWire(int type,String message) {
        return nativeIndexedWire(0,type,message);
    }
""",
"SC_ChatPacket":"""
    public static CSharedBroadcast.NativeWireWriter nativeWire(int opcode,int type,
            int objectId,String text,boolean includeObjectId) {
        final String safe=text==null?"":text;
        return CSharedBroadcast.sizedMS949(safe,5+(includeObjectId?4:0),
               b->writeNativeChat(b,opcode,type,objectId,safe,includeObjectId));
    }
""",
"SC_PacketBox":"""
    public static CSharedBroadcast.NativeWireWriter nativeGreenWire(String message) {
        final String text=message==null?"":message;
        return CSharedBroadcast.sizedMS949(text,6,b->{
            b.put((byte)Opcodes.S_EVENT).put((byte)GREEN_MESSAGE).put((byte)2);
            CSharedBroadcast.writeMS949(b,text);b.put((byte)0);
        });
    }
    public static CSharedBroadcast.NativeWireWriter nativeTownLeaderWire(String leader) {
        final String text=leader==null?"":leader;
        return CSharedBroadcast.sizedMS949(text,5,b->{
            b.put((byte)Opcodes.S_EVENT).put((byte)MSG_TOWN_LEADER);
            CSharedBroadcast.writeMS949(b,text);b.put((byte)0);
        });
    }
    public static CSharedBroadcast.NativeWireWriter nativeColorNoticeWire(int color,String message) {
        if(color!=RED_MESSAGE&&color!=YELLOW_MESSAGE)
            throw new IllegalArgumentException("not color message");
        final String text=message==null?"":message;
        return CSharedBroadcast.sizedMS949(text,9,b->{
            b.put((byte)Opcodes.S_EVENT).put((byte)GREEN_MESSAGE).put((byte)2)
             .putShort((short)26204).put((byte)color);
            CSharedBroadcast.writeMS949(b,text);b.put((byte)0);
        });
    }
    public static CSharedBroadcast.NativeWireWriter nativeScoreWire(String message) {
        final String text=message==null?"":message;
        return CSharedBroadcast.sizedMS949(text,6,b->{
            b.put((byte)Opcodes.S_EVENT).put((byte)GREEN_MESSAGE).put((byte)SCORE_MARK);
            CSharedBroadcast.writeMS949(b,text);b.put((byte)0);
        });
    }
    public static CSharedBroadcast.NativeWireWriter nativeRoundWire(int stage) {
        return b->b.put((byte)Opcodes.S_EVENT).put((byte)ROUND1).putInt(stage).putInt(12);
    }
    public static CSharedBroadcast.NativeWireWriter nativeMapTimerWire(int value) {
        return b->b.put((byte)Opcodes.S_EVENT).put((byte)MAP_TIMER).putInt(value);
    }
    public static CSharedBroadcast.NativeWireWriter nativeHadinDisplayWire(int value) {
        return b->b.put((byte)Opcodes.S_EVENT).put((byte)HADIN_DISPLAY).put((byte)value);
    }
    public static CSharedBroadcast.NativeWireWriter nativeMiniGameTimeWire(int time) {
        return b->b.put((byte)Opcodes.S_EVENT).put((byte)MINIGAME_TIME).putShort((short)time);
    }
    public static CSharedBroadcast.NativeWireWriter nativeMiniGameEndWire() {
        return b->b.put((byte)Opcodes.S_EVENT).put((byte)MINIGAME_END)
             .put((byte)147).put((byte)92).put((byte)151).put((byte)220)
             .put((byte)42).put((byte)74);
    }
    public static CSharedBroadcast.NativeWireWriter nativeDragonRaidBuffWire(int seconds) {
        return b->b.put((byte)Opcodes.S_EVENT).put((byte)DRAGONRAID_BUFF)
             .put((byte)1).put((byte)0x27).put((byte)0x0e).putInt(seconds)
             .putShort((short)0x63ef);
    }
    public static CSharedBroadcast.NativeWireWriter nativeEmblemStatusWire(int status) {
        final boolean hasByte=status==0||status==1;
        return b->{b.put((byte)Opcodes.S_EVENT).put((byte)PLEDGE_EMBLEM_STATUS)
           .put((byte)1);if(hasByte)b.put((byte)status);b.putInt(0);};
    }
""",
}
MAPPING={
"SC_SystemMessage.sharedWire":"SC_SystemMessage.nativeWire",
"SC_ServerMessage.sharedWire":"SC_ServerMessage.nativeWire",
"SC_DisplayEffect.sharedWire":"SC_DisplayEffect.nativeWire",
"SC_SkillSound.sharedWire":"SC_SkillSound.nativeWire",
"SC_TrueTargetNew.sharedWire":"SC_TrueTargetNew.nativeWire",
"SC_MessageYN.sharedWire":"SC_MessageYN.nativeWire",
"SC_MessageYN.sharedIndexedWire":"SC_MessageYN.nativeIndexedWire",
"SC_ChatPacket.sharedWire":"SC_ChatPacket.nativeWire",
"SC_PacketBox.sharedGreenWire":"SC_PacketBox.nativeGreenWire",
"SC_PacketBox.sharedTownLeaderWire":"SC_PacketBox.nativeTownLeaderWire",
"SC_PacketBox.sharedColorNoticeWire":"SC_PacketBox.nativeColorNoticeWire",
"SC_PacketBox.sharedScoreWire":"SC_PacketBox.nativeScoreWire",
"SC_PacketBox.sharedRoundWire":"SC_PacketBox.nativeRoundWire",
"SC_PacketBox.sharedMapTimerWire":"SC_PacketBox.nativeMapTimerWire",
"SC_PacketBox.sharedHadinDisplayWire":"SC_PacketBox.nativeHadinDisplayWire",
"SC_PacketBox.sharedMiniGameTimeWire":"SC_PacketBox.nativeMiniGameTimeWire",
"SC_PacketBox.sharedMiniGameEndWire":"SC_PacketBox.nativeMiniGameEndWire",
"SC_PacketBox.sharedDragonRaidBuffWire":"SC_PacketBox.nativeDragonRaidBuffWire",
"SC_PacketBox.sharedEmblemStatusWire":"SC_PacketBox.nativeEmblemStatusWire",
}
# Exact fully-qualified SC factories are preserved, changing only their final member.
DECL=re.compile(r'(\b(?:final\s+)?)(byte\s*\[\s*\])(\s+\w+\s*=\s*(?:(?:l1j\.server\.server\.model\.network\.core\.sc\.packet\.)?SC_\w+)\.native\w*\s*\()',re.S)

def transform_calls(path,s):
    if path.startswith(Q):
        return s
    before=s
    for old,new in MAPPING.items():
        pat=r'\b'+re.escape(old)+r'\s*\('
        count=len(re.findall(pat,s))
        if count:
            s=re.sub(pat,new+"(",s)
            REPLACED[old]=REPLACED.get(old,0)+count
    # Full package prefix does not prevent this local match.
    s=DECL.sub(lambda m:m.group(1)+FQT+m.group(3),s)
    # Recipient-major frame list: preserve original bytes order but no Java array.
    s=re.sub(r'byte\s*\[\s*\]\s*\[\s*\]\s+(\w+)\s*=\s*new\s+byte\s*\[\s*([^\]]+)\s*\]\s*\[\s*\]',
             FQT+r'[] \1=new '+FQT+r'[\2]',s)
    return s

def migrate(files):
    edit(files,S,core_source)
    edit(files,B,core_broadcast)
    for name,methods in WRITERS.items():
        path=Q+name+".java"
        def add(s,methods=methods):
            s=s.replace("public final class "+name+" {",
                        "public final class "+name+" {")
            # Keep imports in original position. Never rename old oracle helpers.
            return append(s.replace("package l1j.server.server.model.network.core.sc.packet;",
                 "package l1j.server.server.model.network.core.sc.packet;\n\n"
                 "import "+FQ+";"),methods)
        edit(files,path,add)
    for path,data in list(files.items()):
        if not path.startswith("src/") or not path.endswith(".java") or path in CHANGED:
            continue
        s=data.decode("utf-8")
        crlf="\r\n" in s
        out=transform_calls(path,s.replace("\r\n","\n"))
        if out!=s.replace("\r\n","\n"):
            files[path]=(out.replace("\n","\r\n") if crlf else out).encode("utf-8")
            CHANGED.add(path)
    assert sum(REPLACED.values())>170,REPLACED

def prepare(files):
    for n,data in files.items():
        if n.startswith("native/") or (n.startswith("src/") and n.endswith(".java")):
            dst=WORK/n;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(data)
    native_root=ROOT/"patches/ctransport_core_20261112/native"
    for src in native_root.iterdir():
        if src.is_file() and not (WORK/"native"/src.name).exists():
            shutil.copy2(src,WORK/"native"/src.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for n in ("build","logs"):(WORK/n).mkdir(exist_ok=True)
    print("PHASE82_PREPARED",len(CHANGED),"changed",sum(REPLACED.values()),"native writers",REPLACED,flush=True)

def package(infos,old,new):
    assert (WORK/"build/CTransport.dll").read_bytes()==old["CTransport.dll"]
    for n in CHANGED:new[n]=(WORK/n).read_bytes()
    delta={n for n in old if old[n]!=new[n]}
    assert delta==CHANGED,(sorted(delta^CHANGED))
    assert len(new)==1734 and sum(n.endswith(".java") for n in new)==1710
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:z.writestr(info,b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and len(z.namelist())==1734
    report={"phase":82,"baseline_sha256":PIN,
         "sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
         "dll_sha256":hashlib.sha256(old["CTransport.dll"]).hexdigest(),
         "changed_java_files":len(delta),"java_count":1710,
         "factory_calls_migrated":REPLACED,"calls_total":sum(REPLACED.values()),
         "new_queues_workers_classes":0,"5000_ingame_soak_verified":False}
    (WORK/"audit82.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print("PHASE82_ZIP_PUBLISHED",json.dumps(report,ensure_ascii=False),flush=True)

if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    infos,old=load();new=old.copy();migrate(new)
    if sys.argv[2]=="prepare":prepare(new)
    else:package(infos,old,new)

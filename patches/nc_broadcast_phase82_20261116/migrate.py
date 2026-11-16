#!/usr/bin/env python3
"""Phase82: migrate known legacy byte[] broadcast fanouts to lazily written Native slabs.
No new worker/queue/class, no alternate fallback. The exact package base is SHA-pinned.
"""
from pathlib import Path
import hashlib,re,zipfile,json,shutil,collections,sys
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SPacket_Assemble_Audit81_FULL_SRC_20261116.zip"
PIN="6b077cbbafdc1067e8a5c31e9781dfe4221c78331b7bf3cde4659a0c631bb0c3"
OUT=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase82_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else None
P="src/l1j/server/server/model/network/core/"
BC=P+"broadcast/CSharedBroadcast.java"
AS=P+"broadcast/CAsyncSendConstBuffer.java"
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"
CALLS={
 "sendObjectPlayers":"sendWrittenObjectPlayers",
 "sendArray":"sendWrittenArray",
 "sendRecipients":"sendWrittenRecipients",
 "sendGroups":"sendWrittenGroups",
 "sendAllPlayable":"sendWrittenAllPlayable",
 "sendAllGms":"sendWrittenAllGms",
 "sendMapTeam":"sendWrittenMapTeam",
 "sendNestedVisible":"sendWrittenNestedVisible",
 "sendAll":"sendWrittenAll"
}
FACTORIES={
 ("SC_PacketBox","sharedGreenWire"):"greenWriter",
 ("SC_PacketBox","sharedColorNoticeWire"):"colorNoticeWriter",
 ("SC_PacketBox","sharedScoreWire"):"scoreWriter",
 ("SC_PacketBox","sharedRoundWire"):"roundWriter",
 ("SC_PacketBox","sharedMapTimerWire"):"mapTimerWriter",
 ("SC_PacketBox","sharedHadinDisplayWire"):"hadinDisplayWriter",
 ("SC_PacketBox","sharedMiniGameTimeWire"):"miniGameTimeWriter",
 ("SC_PacketBox","sharedMiniGameEndWire"):"miniGameEndWriter",
 ("SC_PacketBox","sharedDragonRaidBuffWire"):"dragonRaidBuffWriter",
 ("SC_PacketBox","sharedTownLeaderWire"):"townLeaderWriter",
 ("SC_PacketBox","sharedEmblemStatusWire"):"emblemStatusWriter",
 ("SC_SystemMessage","sharedWire"):"systemWriter",
 ("SC_ServerMessage","sharedWire"):"serverMessageWriter",
 ("SC_DisplayEffect","sharedWire"):"displayEffectWriter",
 ("SC_LFCTrapEffect","sharedWire"):"lfcTrapWriter",
 ("SC_EffectLocation","sharedWire"):"effectLocationWriter",
 ("SC_ChatPacket","sharedWire"):"chatWriter"
}
# Parse Java call and split args at nesting depth zero: use it only in
# source snippets that have no malformed C comments. No broad regex surgery.
def parse(s,i):
    assert s[i]=="(",i
    stack=["("];string=None;escaped=False;starts=[i+1];args=[]
    j=i+1
    while j<len(s):
        ch=s[j]
        if escaped:escaped=False;j+=1;continue
        if string:
            if ch=="\\":escaped=True
            elif ch==string:string=None
            j+=1;continue
        if ch in ("'",'"'):string=ch;j+=1;continue
        if ch in "([{":stack.append(ch)
        elif ch in ")]}":
            assert stack,(i,j,ch)
            stack.pop()
            if not stack:
                args.append(s[starts[-1]:j])
                return j,args
        elif ch=="," and len(stack)==1:
            args.append(s[starts[-1]:j]);starts.append(j+1)
        j+=1
    raise ValueError("unterminated "+s[i:i+120])

def factory(expr):
    expr=expr.strip()
    m=re.fullmatch(r"(?:l1j\.server\.server\.model\.network\.core\.sc\.packet\.)?(SC_\w+)\s*\.\s*(shared\w*Wire)\s*\(",expr)
    # We cannot use fullmatch on the entire expression because there are args.
    m=re.match(r"(?:(?:l1j\.server\.server\.model\.network\.core\.sc\.packet)\.)?(SC_\w+)\s*\.\s*(shared\w*Wire)\s*\(",expr)
    if not m:return None
    j,args=parse(expr,m.end()-1)
    if expr[j+1:].strip():return None
    dest=FACTORIES.get((m.group(1),m.group(2)))
    if not dest:return None
    return FQ+"."+dest+"("+",".join(args)+")"

def transform_calls(name,source,counters):
    # Work right to left so offsets do not move. Preserve all other source bytes.
    found=[]
    pat=re.compile(r"(?:l1j\.server\.server\.model\.network\.core\.broadcast\.)?CSharedBroadcast\s*\.\s*(\w+)\s*\(")
    for m in pat.finditer(source):
        method=m.group(1)
        if method not in CALLS:continue
        j,args=parse(source,m.end()-1)
        if not args:continue
        writer=factory(args[-1])
        if writer is None:continue
        start=m.start()
        original=source[start:j+1]
        prefix=source[m.start():m.start(1)]
        replacement=prefix+CALLS[method]+"("+",".join(args[:-1]+[writer])+")"
        found.append((start,j+1,replacement))
        counters["call_"+method]+=1
    for a,b,rep in reversed(found):source=source[:a]+rep+source[b:]
    return source

def transform_new(name,source,counters):
    # Writer-backed CAsyncSendConstBuffer retains native lease semantics and
    # the original interleaved calls/side effects of its callers. Only directly
    # expressed immutable factories are converted. Others remain audited.
    pat=re.compile(r"new\s+(?:l1j\.server\.server\.model\.network\.core\.broadcast\.)?CAsyncSendConstBuffer\s*\(")
    found=[]
    for m in pat.finditer(source):
        j,args=parse(source,m.end()-1)
        if len(args)!=3:continue
        f=factory(args[0])
        if f is None:continue
        start=m.start()
        raw=source[start:j+1]
        at=raw.find("(")
        rep=raw[:at+1]+f+","+(",".join(args[1:]))+")"
        found.append((start,j+1,rep))
        counters["ctor_written"]+=1
    for a,b,rep in reversed(found):source=source[:a]+rep+source[b:]
    return source

NATIVE_FACTORIES=r"""
    // Phase82: the source payload is composed directly into the borrowed
    // Native CIOBufferEx. Every writer starts immediately AFTER the 2-byte
    // transport length; sealPooledWritableConstBuffer fills that header.
    public static NativeWireWriter greenWriter(String msg) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                        .put((byte)84).put((byte)2);
            writeMS949(b,msg);b.put((byte)0); };
    }
    public static NativeWireWriter systemWriter(String msg) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_MESSAGE)
                        .put((byte)0x09);
            writeMS949(b,msg);b.put((byte)0); };
    }
    public static NativeWireWriter displayEffectWriter(int effect) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                     .put((byte)0x53).putInt(effect).putShort((short)0);
    }
    public static NativeWireWriter lfcTrapWriter(int id,int gfx) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EFFECT)
                     .putInt(id).putShort((short)gfx);
    }
    public static NativeWireWriter effectLocationWriter(int x,int y,int gfx) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EFFECT_LOC)
                     .putShort((short)x).putShort((short)y)
                     .putShort((short)gfx).put((byte)0);
    }
    public static NativeWireWriter scoreWriter(String msg) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                        .put((byte)84).put((byte)4);
            writeMS949(b,msg);b.put((byte)0); };
    }
    public static NativeWireWriter colorNoticeWriter(int color,String msg) {
        if(color!=51 && color!=61)
            throw new IllegalArgumentException("invalid color notice");
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                        .put((byte)84).put((byte)2).putShort((short)26204)
                        .put((byte)color);
            writeMS949(b,msg);b.put((byte)0); };
    }
    public static NativeWireWriter roundWriter(int stage) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)156).putInt(stage).putInt(12);
    }
    public static NativeWireWriter mapTimerWriter(int time) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)153).putInt(time);
    }
    public static NativeWireWriter miniGameTimeWriter(int time) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)71).putShort((short)time);
    }
    public static NativeWireWriter hadinDisplayWriter(int value) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)83).put((byte)value);
    }
    public static NativeWireWriter miniGameEndWriter() {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)70).put((byte)147).put((byte)92).put((byte)151)
                .put((byte)220).put((byte)42).put((byte)74);
    }
    public static NativeWireWriter dragonRaidBuffWriter(int seconds) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)179).put((byte)1).put((byte)0x27).put((byte)0x0e)
                .putInt(seconds).putShort((short)0x63ef);
    }
    public static NativeWireWriter emblemStatusWriter(int status) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                         .put((byte)173).put((byte)1);
            if(status==0||status==1)b.put((byte)status);
            b.putInt(0); };
    }
    public static NativeWireWriter townLeaderWriter(String leader) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                        .put((byte)23);
            writeMS949(b,leader);b.put((byte)0); };
    }
    public static NativeWireWriter chatWriter(int opcode,int type,int id,
                                             String message,boolean withId) {
        return b -> { b.put((byte)opcode).put((byte)type);
            if(withId)b.putInt(id);
            writeMS949(b,message);b.put((byte)0); };
    }
    public static NativeWireWriter serverMessageWriter(int type) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_MESSAGE_CODE)
                     .putShort((short)type).put((byte)0);
    }
    public static NativeWireWriter serverMessageWriter(int type,String msg) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_MESSAGE_CODE)
                        .putShort((short)type).put((byte)(msg==null?0:1));
            if(msg!=null){writeMS949(b,msg);b.put((byte)0);} };
    }
    public static NativeWireWriter serverMessageWriter(int type,String m1,String m2,String m3) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_MESSAGE_CODE)
                .putShort((short)type)
                .put((byte)((m1==null?0:1)+(m2==null?0:1)+(m3==null?0:1)));
            if(m1!=null){writeMS949(b,m1);b.put((byte)0);}
            if(m2!=null){writeMS949(b,m2);b.put((byte)0);}
            if(m3!=null){writeMS949(b,m3);b.put((byte)0);} };
    }

    private static CAsyncSendConstBuffer sendWrittenListTo(
            CAsyncSendConstBuffer shared,Iterable<? extends L1PcInstance> list,
            NativeWireWriter writer) {
        if(list==null)return shared;
        for(L1PcInstance pc:list)shared=sendWrittenTo(shared,pc,writer);
        return shared;
    }
    public static void sendWrittenArray(L1PcInstance[] pcs,NativeWireWriter writer) {
        if(pcs==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try { for(L1PcInstance pc:pcs)shared=sendWrittenTo(shared,pc,writer); }
        finally {if(shared!=null)shared.close();}
    }
    public static void sendWrittenObjectPlayers(Iterable<? extends L1Object> objects,
                                                NativeWireWriter writer) {
        if(objects==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1Object obj:objects)
                if(obj instanceof L1PcInstance pc)
                    shared=sendWrittenTo(shared,pc,writer);
        } finally {if(shared!=null)shared.close();}
    }
    public static void sendWrittenGroups(
            Iterable<? extends L1PcInstance> first,
            Iterable<? extends L1PcInstance> second,NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            shared=sendWrittenListTo(shared,first,writer);
            shared=sendWrittenListTo(shared,second,writer);
        } finally {if(shared!=null)shared.close();}
    }
    public static void sendWrittenAllPlayable(NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:L1World.getInstance().getAllPlayers())
                if(pc!=null&&!pc.isPrivateShop()&&pc.is_world())
                    shared=sendWrittenTo(shared,pc,writer);
        } finally {if(shared!=null)shared.close();}
    }
    public static void sendWrittenAllGms(NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:L1World.getInstance().getAllPlayers())
                if(pc!=null&&pc.isGm())shared=sendWrittenTo(shared,pc,writer);
        } finally {if(shared!=null)shared.close();}
    }
    public static void sendWrittenMapTeam(int map,int team,NativeWireWriter writer) {
        if(writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayers(map))
                if(pc!=null&&pc.get_current_combat_team_id()==team)
                    shared=sendWrittenTo(shared,pc,writer);
        } finally {if(shared!=null)shared.close();}
    }
    public static void sendWrittenNestedVisible(L1Object origin,NativeWireWriter writer) {
        if(origin==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        final L1World world=L1World.getInstance();
        try {
            for(L1PcInstance anchor:world.getVisiblePlayer(origin)) {
                if(anchor==null)continue;
                for(L1PcInstance recipient:world.getVisiblePlayer(anchor)) {
                    if(CTLMFilter.sameWorldOther(anchor,recipient))
                        shared=sendWrittenTo(shared,recipient,writer);
                }
            }
        } finally {if(shared!=null)shared.close();}
    }
"""
def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        names=z.namelist()
        files={n:z.read(n) for n in names if not n.endswith("/")}
    counts=collections.Counter()
    changed=[]
    for name in list(files):
        if not name.startswith("src/") or not name.endswith(".java"):continue
        original=files[name].decode("utf-8")
        code=transform_calls(name,original,counts)
        code=transform_new(name,code,counts)
        if name==AS:
            code=code.replace("    private final byte[] wire;","    private final byte[] wire;\n    private final CSharedBroadcast.NativeWireWriter writer;",1)
            code=code.replace("        wire = owned ? frame : frame.clone();","        wire = owned ? frame : frame.clone();\n        writer = null;",1)
            anchor="    protected CAsyncSendConstBuffer(Source pooled, int senderId, int pendingLimit) {"
            add="""    /** Direct Native Assemble, lazily after first admissible recipient. */
    public CAsyncSendConstBuffer(CSharedBroadcast.NativeWireWriter nativeWriter,
                                 int senderId,int pendingLimit) {
        if(nativeWriter==null||pendingLimit<=0)
            throw new IllegalArgumentException("Native broadcast writer/limit");
        this.wire=null;
        this.writer=nativeWriter;
        this.senderId=senderId;
        this.pendingLimit=pendingLimit;
    }

"""
            assert code.count(anchor)==1
            code=code.replace(anchor,add+anchor,1)
            code=code.replace("        wire = null;\n        source = pooled;","        wire = null;\n        writer = null;\n        source = pooled;",1)
            needle="        final int limit=recipientId==senderId?Integer.MAX_VALUE:pendingLimit;\n        final int length = wire == null ? source.wire.remaining() : wire.length;"
            replacement="""        if(writer!=null && source==null) {
            source=openWrittenSource(target,writer);
            if(source==null)throw new IllegalStateException("Native broadcast source unavailable");
        }
        final int limit=recipientId==senderId?Integer.MAX_VALUE:pendingLimit;
        final int length = wire == null ? source.wire.remaining() : wire.length;"""
            assert code.count(needle)==1
            code=code.replace(needle,replacement,1)
        if name==BC:
            anchor="    /** Global broadcast includes the sender and uses the normal socket FIFO. */"
            assert code.count(anchor)==1
            code=code.replace(anchor,NATIVE_FACTORIES+"\n"+anchor,1)
        if code!=original:
            assert name not in changed
            changed.append(name)
            files[name]=(code.replace("\n","\r\n") if "\r\n" in original else code).encode("utf-8")
    # Preserve the exact original ZIP ordering, metadata and unchanged entries.
    for name,data in files.items():
        if (name.startswith("src/") and name.endswith(".java")) or name.startswith("native/"):
            p=WORK/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    # The original Windows workflow compiles the same old native files and
    # then restores the pinned shipping DLL before Java/IOCP tests.
    native_root=ROOT/"patches/ctransport_core_20261112/native"
    for src in native_root.iterdir():
        if src.is_file() and not (WORK/"native"/src.name).exists():
            dst=WORK/"native"/src.name;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dst)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    (WORK/"build").mkdir(exist_ok=True)
    (WORK/"logs").mkdir(exist_ok=True)
    (WORK/"phase82_changed.json").write_text(json.dumps({"changed":sorted(changed),
            "converted":dict(counts),"baseline":PIN},ensure_ascii=False,indent=2)+"\n")
    assert counts["call_sendRecipients"]>=40,dict(counts)
    assert counts["ctor_written"]>=40,dict(counts)
    print("PHASE82_CHANGED",len(changed),"TOTAL_REWRITES",sum(counts.values()),dict(counts),flush=True)
def package():
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist()
        old={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    assert (WORK/"build/CTransport.dll").read_bytes()==old["CTransport.dll"]
    new=old.copy()
    declared=json.loads((WORK/"phase82_changed.json").read_text())["changed"]
    for n in declared:new[n]=(WORK/n).read_bytes()
    actual=sorted(n for n in old if old[n]!=new[n])
    assert actual==declared
    assert len(new)==1734 and sum(n.endswith(".java") for n in new)==1710
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:z.writestr(info,b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and len(z.namelist())==1734
    reports=json.loads((WORK/"phase82_changed.json").read_text())
    report={"phase":82,"baseline_sha256":PIN,
        "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "dll_sha256":hashlib.sha256(old["CTransport.dll"]).hexdigest(),
        "native_abi":"0x000a0009","changed":declared,
        "conversion":reports["converted"],"total_converted":sum(reports["converted"].values()),
        "source_files":1710,"zip_entries":1734,
        "legacy_left_requires_further_rewrite":True,"new_classes_workers_queues":0,
        "5000_ingame_soak_verified":False}
    (WORK/"audit82.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print("PHASE82_PACKAGE_OK",json.dumps({"sha":report["full_src_sha256"],
                                        "changed":len(actual),"converted":report["total_converted"]}),flush=True)
if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()

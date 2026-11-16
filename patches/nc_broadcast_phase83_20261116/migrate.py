#!/usr/bin/env python3
"""Phase83 direct-Native factories for remaining uncommon L1 SC and raw Proto fanout.
Never introduce a new worker, queue or class. Keep 82 SHA-pinned.
"""
from pathlib import Path
import hashlib,re,zipfile,json,shutil,collections,sys,importlib.util
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase82_FULL_SRC_20261116.zip"
PIN="2be793057e4dc515212b03d409e41114dadfa21f656b83cae67f94111d8dbb00"
OUT=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase83_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else None
P="src/l1j/server/server/model/network/core/"
BC=P+"broadcast/CSharedBroadcast.java"
AS=P+"broadcast/CAsyncSendConstBuffer.java"
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"
p82=ROOT/"patches/nc_broadcast_phase82_20261116/migrate.py"
spec=importlib.util.spec_from_file_location("phase82_legacy_tool",p82)
a82=importlib.util.module_from_spec(spec);spec.loader.exec_module(a82)
MAPPING=dict(a82.FACTORIES)
MAPPING.update({
 ("SC_MoveChar","broadcastWire"):"moveObjectWriter",
 ("SC_DoActionGFX","broadcastWire"):"actionWriter",
 ("SC_RemoveObject","broadcastWire"):"removeObjectWriter",
 ("SC_SkillSound","sharedWire"):"skillSoundWriter",
 ("SC_ActionUI","sharedFlagWire"):"actionUiFlagWriter",
 ("SC_ChatPacket","sharedTargetWire"):"targetChatWriter",
 ("SC_MessageYN","sharedWire"):"askWriter",
 ("SC_MessageYN","sharedIndexedWire"):"askIndexedWriter",
 ("SC_IconMessage","sharedGmWire"):"gmIconWriter",
 ("SC_ChatMessageNoti","sharedMacroWire"):"macroWriter",
 ("SC_ShowCmd","sharedPkWire"):"showPkWriter",
 ("SC_ClanAttention","sharedMentWire"):"clanMentWriter",
 ("SC_ClanAttention","sharedListWire"):"clanListWriter",
 ("SC_CastleMaster","sharedWire"):"castleMasterWriter",
 ("SC_NpcChatPacket","sharedNpcWire"):"npcChatWriter",
 ("SC_Party","sharedNameColorWire"):"partyColorWriter",
 ("SC_GameStart","sharedWire"):"gameStartWriter",
 ("SC_GameRap","sharedWire"):"gameRapWriter",
 ("SC_EtcPacket","sharedRacingWire"):"racingWriter",
 ("SC_GameEnd","sharedWire"):"gameEndWriter",
 ("SC_GameOver","sharedWire"):"gameOverWriter",
 ("SC_TrueTargetNew","sharedWire"):"trueTargetWriter",
})
def factory(expr):
    expr=expr.strip()
    m=re.match(r"(?:(?:l1j\.server\.server\.model\.network\.core\.sc\.packet)\.)?(SC_\w+)\s*\.\s*(shared\w*Wire|broadcastWire)\s*\(",expr)
    if not m:return None
    j,args=a82.parse(expr,m.end()-1)
    if expr[j+1:].strip():return None
    method=MAPPING.get((m.group(1),m.group(2)))
    if method is None:return None
    return FQ+"."+method+"("+",".join(args)+")"
a82.factory=factory
a82.CALLS.update({"sendNestedVisible":"sendWrittenNestedVisible",
                  "sendAll":"sendWrittenAll"})
a82.FACTORIES.update(MAPPING)
FACTORIES83=r"""
    private static final byte[] GM_ICON_RGB_NATIVE=
            new l1j.server.MJTemplate.MJSimpleRgb(249,68,253).get_bytes();

    public static NativeWireWriter moveObjectWriter(int id,int x,int y,int heading) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_MOVE_OBJECT)
                .putInt(id).putShort((short)x).putShort((short)y)
                .put((byte)heading).put((byte)129).putInt(0);
    }
    public static NativeWireWriter actionWriter(int id,int action) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_ACTION)
                .putInt(id).put((byte)action);
    }
    public static NativeWireWriter removeObjectWriter(int id) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_REMOVE_OBJECT)
                .putInt(id).putShort((short)0);
    }
    public static NativeWireWriter skillSoundWriter(int id,int gfx) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EFFECT)
                .putInt(id).putShort((short)gfx)
                .putShort((short)0).putInt(0);
    }
    public static NativeWireWriter actionUiFlagWriter(byte[] flag) {
        if(flag!=null&&flag.length>65528)return null;
        final byte[] snapshot=flag==null?null:flag.clone();
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EXTENDED_PROTOBUF)
                          .putShort((short)339);
            if(snapshot!=null)b.put(snapshot);
            b.putShort((short)0); };
    }
    public static NativeWireWriter targetChatWriter(String target,int type,String chat) {
        final String value="["+(target==null?"":target)+"] "+(chat==null?"":chat);
        if(32L+4L*value.length()>65535L)return null;
        return chatWriter(l1j.server.server.Opcodes.S_MESSAGE,type,0,value,false);
    }
    public static NativeWireWriter askWriter(int type,String msg) {
        return askIndexedWriter(0,type,msg);
    }
    public static NativeWireWriter askIndexedWriter(int idx,int type,String msg) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_ASK)
                       .putShort((short)0).putInt(idx).putShort((short)type);
            writeMS949(b,msg);b.put((byte)0); };
    }
    public static NativeWireWriter gmIconWriter(String message) {
        final String text=message==null?"":message;
        final int max=text.length();
        if(max>127&&text.getBytes(MS949).length>255)
            throw new IllegalStateException("writeS2 overflow in macro icon");
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EXTENDED_PROTOBUF)
                         .putShort((short)0x40)
                         .put((byte)8).put((byte)56).put((byte)0x12);
            writeMS949S2(b,text);
            b.put((byte)0x1a).put(GM_ICON_RGB_NATIVE);
            b.put((byte)0x20).put((byte)60).putShort((short)0);
        };
    }
    public static NativeWireWriter macroWriter(int type,String msg,String sender) {
        final String text=msg==null?"":msg;
        if(text.length()>127&&text.getBytes(MS949).length>255)
            throw new IllegalStateException("macro text S2 overflow");
        if(sender!=null&&sender.length()>127&&sender.getBytes(MS949).length>255)
            throw new IllegalStateException("macro sender S2 overflow");
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EXTENDED_PROTOBUF)
                         .putShort((short)0x0204)
                         .put((byte)0x08).put((byte)0)
                         .put((byte)0x10).put((byte)type).put((byte)0x1a);
            writeMS949S2(b,text);
            if(sender!=null){b.put((byte)0x2a);writeMS949S2(b,sender);}
            b.put((byte)0x30).put((byte)0).putShort((short)0);
        };
    }
    public static NativeWireWriter showPkWriter(byte[] killer,byte[] victim) {
        if(killer==null||victim==null||killer.length>255||victim.length>255)
            return null;
        final byte[] a=killer.clone(),z=victim.clone();
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_EXTENDED_PROTOBUF)
                         .putShort((short)0x247).put((byte)0x08)
                         .put((byte)0).put((byte)0x12).put((byte)a.length).put(a)
                         .put((byte)0x18).put((byte)0).put((byte)0x22)
                         .put((byte)z.length).put(z).put((byte)0x28)
                         .put((byte)1).putShort((short)0);
        };
    }
    public static NativeWireWriter clanMentWriter(boolean on,String name) {
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_PLEDGE_WATCH)
                         .put((byte)(on?32:31)).putShort((short)269);
            writeMS949(b,name);b.put((byte)0); };
    }
    public static NativeWireWriter clanListWriter(int count,
            java.util.List<String> source) {
        final String[] snapshot;
        if(source==null)snapshot=null;
        else synchronized(source){snapshot=source.toArray(new String[0]);}
        return b -> {
            b.put((byte)l1j.server.server.Opcodes.S_PLEDGE_WATCH)
             .putShort((short)2).putInt(count);
            if(snapshot!=null)for(String name:snapshot){
                if(name==null)continue;
                writeMS949(b,name);b.put((byte)0);
            }
        };
    }
    public static NativeWireWriter castleMasterWriter(int type,int id) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_CASTLE_OWNER)
                .put((byte)type).putInt(id);
    }
    public static NativeWireWriter npcChatWriter(
            l1j.server.server.model.Instance.L1NpcInstance npc,String msg,int type) {
        if(npc==null||msg==null)return null;
        final String formatted;
        final String name=npc.getName();
        switch(type){
        case 0:case 4:
            formatted=npc.getNpcId()==8500200?msg:name+": "+msg;break;
        case 2:
            final int templateId=npc.getNpcTemplate()!=null
                 ? npc.getNpcTemplate().get_npcId():0;
            formatted=templateId==70518||templateId==70506
                 ? name+": "+msg:"<"+name+"> "+msg;break;
        case 3: formatted="["+name+"] "+msg;break;
        default:return null;
        }
        final int id=npc.getId(),color=type==4?21:type;
        return b -> { b.put((byte)l1j.server.server.Opcodes.S_SAY_CODE)
                         .put((byte)color).putInt(id);
            writeMS949(b,formatted);b.put((byte)0); };
    }
    public static NativeWireWriter partyColorWriter(int id,int color) {
        if(color<0||color>2)return null;
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0x6c).putInt(id).putShort((short)color);
    }
    public static NativeWireWriter gameStartWriter() {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0x40).put((byte)0x05).putInt(0).put((byte)0);
    }
    public static NativeWireWriter gameRapWriter(int value) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0x43).putShort((short)4).putShort((short)value);
    }
    public static NativeWireWriter racingWriter() {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0x41).put((byte)100).putInt(0).put((byte)0);
    }
    public static NativeWireWriter gameEndWriter() {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0x46).put((byte)147).put((byte)92).put((byte)151)
                .put((byte)220).put((byte)42).put((byte)74);
    }
    public static NativeWireWriter gameOverWriter() {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0x45).put((byte)10).put((byte)109).put((byte)85)
                .put((byte)208).put((byte)2).put((byte)220);
    }
    public static NativeWireWriter trueTargetWriter(int id,boolean active) {
        return b -> b.put((byte)l1j.server.server.Opcodes.S_EVENT)
                .put((byte)0xc2).putInt(id).put((byte)0x4f).put((byte)0x33)
                .putShort((short)0).putInt(active?1:0).putShort((short)0);
    }

    /** Exactly one immutable raw Proto payload, without the former new
     * Java framed array. The existing encoded data is snapshotted once;
     * payloads >16KiB use existing per-socket CIO direct FIFO (no old queue).
     */
    public static NativeWireWriter rawWriter(byte[] encoded) {
        if(encoded==null||encoded.length==0||encoded.length>65533)return null;
        final byte[] snapshot=encoded.clone();
        return new NativeWireWriter() {
            @Override public void write(java.nio.ByteBuffer out) {out.put(snapshot);}
            @Override public int wireLength() {return snapshot.length+2;}
        };
    }
    public static CAsyncSendConstBuffer openWrittenRaw(byte[] encoded) {
        final NativeWireWriter writer=rawWriter(encoded);
        return writer==null?null:
                new CAsyncSendConstBuffer(writer,Integer.MIN_VALUE,Integer.MAX_VALUE);
    }
    public static void sendWrittenRawArray(L1PcInstance[] recipients,byte[] encoded) {
        final NativeWireWriter writer=rawWriter(encoded);
        if(writer!=null)sendWrittenArray(recipients,writer);
    }
    public static void sendWrittenRawRecipients(
            Iterable<? extends L1PcInstance> recipients,byte[] encoded) {
        final NativeWireWriter writer=rawWriter(encoded);
        if(writer!=null)sendWrittenRecipients(recipients,writer);
    }
    public static void sendWrittenRawSelfAndVisible(L1PcInstance self,byte[] encoded) {
        final NativeWireWriter writer=rawWriter(encoded);
        if(writer!=null)sendWrittenSelfAndVisible(self,writer);
    }

    /** Recipient-major order: for each viewer, enqueue every original effect,
     * preserving pending packet order and duplicates. Native writers stay
     * lazy until a valid socket accepts its first frame.
     */
    public static void sendWrittenMapTeamEffects(
            int map,int team,boolean teamOnly,int[] effects) {
        if(effects==null||effects.length==0)return;
        final CAsyncSendConstBuffer[] frames=new CAsyncSendConstBuffer[effects.length];
        int opened=0;
        try {
            for(int i=0;i<effects.length;i++) {
                frames[i]=new CAsyncSendConstBuffer(
                        displayEffectWriter(effects[i]),
                        Integer.MIN_VALUE,Integer.MAX_VALUE);
                opened++;
            }
            for(L1PcInstance pc:L1World.getInstance().getVisiblePlayers(map)) {
                if(pc==null||(teamOnly&&pc.get_current_combat_team_id()!=team))
                    continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket==null||!socket.isClientConnected())continue;
                for(int i=0;i<opened;i++)frames[i].send(socket,pc.getId());
            }
        } finally {
            for(int i=0;i<opened;i++)frames[i].close();
        }
    }
"""

def rename_calls(name,s,c):
    if name==BC:
        # The existing native shared recipient utility is renamed, so legacy
        # methods no longer appear at external call sites. No semantics change.
        s=s.replace("public static void sendRecipient(CAsyncSendConstBuffer shared,L1PcInstance pc)",
                    "public static void sendWrittenRecipient(CAsyncSendConstBuffer shared,L1PcInstance pc)")
        s=re.sub(r"\bsendRecipient\s*\(","sendWrittenRecipient(",s)
    else:
        s,n=re.subn(r"(?<=CSharedBroadcast)\.sendRecipient\s*\(",".sendWrittenRecipient(",s)
        c["recipient_aliases_retired"]+=n
    raw={"sendRawArray":"sendWrittenRawArray",
         "sendRawRecipients":"sendWrittenRawRecipients",
         "sendRawSelfAndVisible":"sendWrittenRawSelfAndVisible",
         "openRaw":"openWrittenRaw"}
    if name!=BC:
        for old,new in raw.items():
            s,n=re.subn(r"(?<=CSharedBroadcast)\."+old+r"\s*\(","."+new+"(",s)
            c["raw_"+old]+=n
    return s
def special(name,s,c):
    if name.endswith("/MJEffectTriggerInstance.java"):
        for old,new in (
            ("final byte[] wire = SC_MoveChar.broadcastWire(getId(), x, y, h);",
             "final "+FQ+".NativeWireWriter wire = "+FQ+".moveObjectWriter(getId(), x, y, h);"),
            ("final byte[] wire = SC_DoActionGFX.broadcastWire(getId(), action);",
             "final "+FQ+".NativeWireWriter wire = "+FQ+".actionWriter(getId(), action);"),
        ):
            assert s.count(old)==1,(name,old)
            s=s.replace(old,new,1)
        old=FQ+".sendRecipients("
        assert s.count(old)==2
        s=s.replace(old,FQ+".sendWrittenRecipients(")
        c["typed_wire_variable"]+=2
    if name.endswith("/MJBattleTournamentArea.java"):
        old="""        final byte[][] frames=new byte[packets.length][];
        for(int i=0;i<packets.length;i++)
            frames[i]=SC_DisplayEffect.sharedWire(packets[i]);
        """+FQ+"""
                .sendMapTeamFrames(_map_id,_team_id,_round_id<4,frames);"""
        new="""        """+FQ+"""
                .sendWrittenMapTeamEffects(_map_id,_team_id,_round_id<4,packets);"""
        assert s.count(old)==1
        s=s.replace(old,new,1)
        c["mapTeamEffect"]+=1
    return s

def patch_core(name,code):
    if name==BC:
        needle="    public interface NativeWireWriter {\n        void write(java.nio.ByteBuffer output);\n    }"
        change="""    public interface NativeWireWriter {
        void write(java.nio.ByteBuffer output);
        /** A positive full framed length is mandatory for >16KiB Proto data. */
        default int wireLength(){return -1;}
    }"""
        assert code.count(needle)==1
        code=code.replace(needle,change,1)
        needle="    private static boolean sendLargeDirectFrame("
        assert code.count(needle)==1
        code=code.replace(needle,"    static boolean sendLargeDirectFrame(",1)
        needle="""        if(shared==null) {
            final CAsyncSendConstBuffer.Source source=
                    CAsyncSendConstBuffer.openWrittenSource(socket,writer);"""
        method="    private static CAsyncSendConstBuffer sendWrittenTo("
        from_index=code.index(method)
        to_index=code.index("    public static void sendWrittenSelfAndVisible(",from_index)
        chunk=code[from_index:to_index]
        assert chunk.count(needle)==1
        chunk=chunk.replace(needle, """        final int oversized=writer.wireLength();
        if(oversized>16384) {
            sendLargeDirectFrame(socket,oversized,writer);
            return shared;
        }
"""+needle,1)
        code=code[:from_index]+chunk+code[to_index:]
        needle="""        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:recipients) {
                if(pc==null)continue;"""
        # Must be the sendWrittenRecipients implementation and never rewrite
        # other similarly shaped methods.
        signature="    public static void sendWrittenRecipients(\n"
        pos=code.find(signature);assert pos>=0
        end=code.find("    /** writeS2 exact",pos);assert end>pos
        chunk=code[pos:end]
        assert chunk.count(needle)==1
        chunk=chunk.replace(needle, """        if(writer.wireLength()>16384) {
            for(L1PcInstance pc:recipients) {
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                sendLargeDirectFrame(client.getConnection(),writer.wireLength(),writer);
            }
            return;
        }
"""+needle,1)
        code=code[:pos]+chunk+code[end:]
        anchor="    /** Global broadcast includes the sender and uses the normal socket FIFO. */"
        assert code.count(anchor)==1
        code=code.replace(anchor,FACTORIES83+"\n"+anchor,1)
    elif name==AS:
        needle="        if(writer!=null && source==null) {\n            source=openWrittenSource(target,writer);"
        assert code.count(needle)==1
        code=code.replace(needle,"""        if(writer!=null && writer.wireLength()>16384)
            return CSharedBroadcast.sendLargeDirectFrame(target,writer.wireLength(),writer);
"""+needle,1)
    return code

def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        names=z.namelist()
        files={n:z.read(n) for n in names if not n.endswith("/")}
    counts=collections.Counter();changed=[]
    for name in list(files):
        if not name.startswith("src/") or not name.endswith(".java"):continue
        original=files[name].decode("utf-8")
        old=re.sub(r"\r+\n","\n",original).replace("\r","\n")
        code=a82.transform_calls(name,old,counts)
        code=a82.transform_new(name,code,counts)
        code=rename_calls(name,code,counts)
        code=special(name,code,counts)
        code=patch_core(name,code)
        if code!=old:
            changed.append(name)
            files[name]=(code.replace("\n","\r\n") if "\r\n" in original else code).encode("utf-8")
    for name,data in files.items():
        if (name.startswith("src/") and name.endswith(".java")) or name.startswith("native/"):
            p=WORK/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    native_root=ROOT/"patches/ctransport_core_20261112/native"
    for src in native_root.iterdir():
        if src.is_file() and not (WORK/"native"/src.name).exists():
            dst=WORK/"native"/src.name;dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dst)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for n in ("build","logs"):(WORK/n).mkdir(exist_ok=True)
    (WORK/"phase83_changed.json").write_text(json.dumps(
        {"changed":sorted(changed),"converted":dict(counts),"baseline":PIN},ensure_ascii=False,indent=2)+"\n")
    assert counts["recipient_aliases_retired"]>=160,dict(counts)
    assert counts["ctor_written"]>=35,dict(counts)
    print("PHASE83_CHANGED",len(changed),"TOTAL_REWRITES",sum(counts.values()),dict(counts),flush=True)
def package():
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist()
        old={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    assert (WORK/"build/CTransport.dll").read_bytes()==old["CTransport.dll"]
    new=old.copy()
    meta=json.loads((WORK/"phase83_changed.json").read_text())
    for n in meta["changed"]:new[n]=(WORK/n).read_bytes()
    actual=sorted(n for n in old if old[n]!=new[n])
    assert actual==meta["changed"]
    assert len(new)==1734 and sum(n.endswith(".java") for n in new)==1710
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:z.writestr(info,b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and len(z.namelist())==1734
    report={"phase":83,"baseline_sha256":PIN,
        "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "dll_sha256":hashlib.sha256(old["CTransport.dll"]).hexdigest(),
        "native_abi":"0x000a0009","changed":actual,
        "converted":meta["converted"],"source_files":1710,"zip_entries":1734,
        "legacy_left_requires_further_rewrite":True,"new_classes_workers_queues":0,
        "5000_ingame_soak_verified":False}
    (WORK/"audit83.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print("PHASE83_PACKAGE_OK",json.dumps({"sha":report["full_src_sha256"],
                                        "changed":len(actual),"converted":sum(meta["converted"].values())}),flush=True)
if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()

#!/usr/bin/env python3
"""Audit81: fix confirmed 16K Native pooled broadcast boundary regressions.

SHA-pinned phase80 base; mutate only 4 production Java files.
- Item names: reject based on actual MS949 byte length at the slow boundary.
- Icon S2: preserve old >255-byte quiet refusal before Native encoder runs.
- RangeSkill >16KiB: direct CIO FIFO large frames, never old byte[] fanout.
- Windows IOCP differential, pool-exhaustion and Java21 full compilation.
"""
from pathlib import Path
import hashlib,json,re,shutil,sys,zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase80_FULL_SRC_20261116.zip"
PIN="a0d534b066395b12a3a9e545cb094602e1cd568ea80ddca2900ff46a0a54e391"
OUT=ROOT/"downloads/RKern_NC_SPacket_Assemble_Audit81_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else None
P="src/l1j/server/server/model/network/core/"
BC=P+"broadcast/CSharedBroadcast.java"
SC={n:P+"sc/packet/SC_"+n+".java" for n in ("DropItem","IconMessage","RangeSkill")}
CHANGED={BC}|set(SC.values())
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"

def norm(files,key):
    src=files[key].decode("utf-8")
    return src.replace("\r\n","\n"),"\r\n" in src
def put(files,key,s,crlf):
    files[key]=(s.replace("\n","\r\n") if crlf else s).encode("utf-8")
def change(files,key,old,new):
    s,crlf=norm(files,key)
    assert s.count(old)==1,(key,"anchor",s.count(old),old[:130])
    put(files,key,s.replace(old,new,1),crlf)

def drop_fix(files):
    key=SC["DropItem"]
    old="""        final long required=(long)FIXED_WIRE_BYTES+((long)frozenName.length()<<1);
        if(required>16384L)return 0;"""
    new="""        if(!fitsNativeGroundItemWire(frozenName))return 0;"""
    change(files,key,old,new)
    marker="\tprivate static boolean send("
    method="""    /**
     * The original sharedWire checked the UTF-16 maximum against 65535, but
     * checked the REAL encoded byte length against the 16KiB shared source.
     * Reserve only needs its precise MS949 slow-path for very long names;
     * short names never allocate a temporary encoding array.
     */
    private static boolean fitsNativeGroundItemWire(String name) {
        final String text=name==null?"":name;
        final long reserve=(long)FIXED_WIRE_BYTES+((long)text.length()<<1);
        if(reserve>ConnectionConfig.MAX_WIRE_PACKET_SIZE)return false;
        if(reserve<=16384L)return true;
        return (long)FIXED_WIRE_BYTES+text.getBytes(
                java.nio.charset.Charset.forName("MS949")).length<=16384L;
    }

"""
    change(files,key,marker,method+marker)

def icon_fix(files):
    key=SC["IconMessage"]
    old="""        final byte[] rgb=color==null?null:color.get_bytes();
        """+FQ+""".sendWrittenAll(b -> {"""
    new="""        final byte[] rgb=color==null?null:color.get_bytes();
        // Exact pre-Native writeS2 admission: old sharedWire returned null
        // for >255 encoded bytes, rather than throwing during broadcast.
        if(!fitsTextBroadcast(text,rgb==null?0:rgb.length))return;
        """+FQ+""".sendWrittenAll(b -> {"""
    change(files,key,old,new)
    marker="    private static void putVar(java.nio.ByteBuffer b,long value) {"
    method="""    private static boolean fitsTextBroadcast(String text,int rgbLength) {
        final String safe=text==null?"":text;
        if(66L+((long)safe.length()<<2)+rgbLength>0xFFFFL)return false;
        // MS949 encodes at most two bytes per Java UTF-16 code unit.
        // Most messages need no byte-array length probe.
        return safe.length()<=127 || safe.getBytes(
                java.nio.charset.Charset.forName("MS949")).length<=255;
    }

"""
    change(files,key,marker,method+marker)

def broadcast_large_fix(files):
    key=BC
    marker="    public static void sendWrittenAll(NativeWireWriter writer) {"
    extra="""    /** Rare >16KiB range-skill frame.
     * Native ConstBuffer slabs are deliberately fixed at 16KiB. For a
     * protocol-valid larger attack use the SAME existing CIO direct-frame
     * writer/FIFO; never reinstate a legacy byte[] broadcaster or worker.
     * The 2-byte frame header remains socket-owned.
     */
    private static boolean sendLargeDirectFrame(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int length,NativeWireWriter writer) {
        if(socket==null||!socket.isClientConnected()
                ||length<=16384||length>0xFFFF)return false;
        final var frame=socket.beginDirectFrame(length);
        if(frame==null)return false;
        boolean committed=false;
        try {
            final var target=frame.buffer();
            final java.nio.ByteBuffer output=target.getBuffer()
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            output.position(target.baseOffset()+target.position());
            writer.write(output);
            if(output.position()!=target.baseOffset()+length)
                throw new IllegalStateException("large SC attack frame length mismatch");
            target.position(length);
            committed=socket.commitDirectFrame(frame);
            return committed;
        } finally {
            if(!committed)socket.abortDirectFrame(frame);
        }
    }

    public static void sendWrittenLarge(
            L1Object anchor,int length,NativeWireWriter writer) {
        if(anchor==null||writer==null)return;
        if(length<=16384||length>0xFFFF)
            throw new IllegalArgumentException("large SC wire length "+length);
        final L1World world=L1World.getInstance();
        final List<L1PcInstance> visible=world.borrowVisiblePlayerFast(anchor);
        try {
            for(L1PcInstance pc:visible) {
                if(!CTLMFilter.sameWorldOther(anchor,pc))continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                sendLargeDirectFrame(client.getConnection(),length,writer);
            }
        } finally {
            world.releaseVisiblePlayerFast(visible);
        }
    }

"""
    change(files,key,marker,extra+marker)

def range_fix(files):
    key=SC["RangeSkill"]
    old="""        if(frozenCount>=0 && 24L+6L*frozenCount<=0xFFFFL) {
            """+FQ+""".sendWritten(caster, b -> { b.put((byte)Opcodes.S_ATTACK_MANY).put((byte)action).putInt(id).putShort((short)x).putShort((short)y).put((byte)frozenHeading).putInt(seq).putShort((short)gfx).put((byte)type).putShort((short)0).putShort((short)frozenCount); for(int i=0;i<frozenCount;i++)b.putInt(ids[i]).put((byte)32).put((byte)action); }, true, -1, null);
        }"""
    new="""        final long size=24L+6L*frozenCount;
        if(frozenCount>=0 && size<=0xFFFFL) {
            final """+FQ+""".NativeWireWriter writer=b -> {
                b.put((byte)Opcodes.S_ATTACK_MANY).put((byte)action)
                 .putInt(id).putShort((short)x).putShort((short)y)
                 .put((byte)frozenHeading).putInt(seq).putShort((short)gfx)
                 .put((byte)type).putShort((short)0).putShort((short)frozenCount);
                for(int i=0;i<frozenCount;i++)
                    b.putInt(ids[i]).put((byte)32).put((byte)action);
            };
            if(size<=16384L)
                """+FQ+""".sendWritten(caster,writer,true,-1,null);
            else
                """+FQ+""".sendWrittenLarge(caster,(int)size,writer);
        }"""
    change(files,key,old,new)

def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN,"phase80 SHA pin mismatch"
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos=z.infolist()
        files={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    old=files.copy()
    assert len(files)==1734 and sum(n.endswith(".java") for n in files)==1710
    drop_fix(files);icon_fix(files);broadcast_large_fix(files);range_fix(files)
    changed={n for n in old if old[n]!=files[n]}
    assert changed==CHANGED,sorted(changed^CHANGED)
    for n,data in files.items():
        if n.startswith("native/") or (n.startswith("src/") and n.endswith(".java")):
            dst=WORK/n;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(data)
    native_root=ROOT/"patches/ctransport_core_20261112/native"
    for src in native_root.iterdir():
        if src.is_file() and not (WORK/"native"/src.name).exists():
            shutil.copy2(src,WORK/"native"/src.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for n in ("build","logs"):(WORK/n).mkdir(exist_ok=True)
    print("AUDIT81_THREE_SC_BOUNDARY_FIXES_PREPARED",sorted(changed),flush=True)

def package():
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist()
        old={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    assert (WORK/"build/CTransport.dll").read_bytes()==old["CTransport.dll"]
    new=old.copy()
    for n in CHANGED:new[n]=(WORK/n).read_bytes()
    changed={n for n in old if old[n]!=new[n]}
    assert changed==CHANGED,sorted(changed^CHANGED)
    for n,data in new.items():
        if "/sc/packet/SC_" not in n or not n.endswith(".java"):continue
        s=data.decode("utf-8")
        for method in ("send","sendRecipients","sendAll","sendRawRecipients",
                       "sendRawArray","sendArray","sendObjectPlayers","sendRawSelfAndVisible"):
            assert not re.search(r"CSharedBroadcast\\s*\\.\\s*"+method+r"\\s*\\(",s),(n,method)
        assert not re.search(r"new\\s+(?:l1j\\.server\\.server\\.model\\.network\\.core\\.broadcast\\.)?CAsyncSendConstBuffer\\s*\\(",s),n
    assert len(new)==1734 and sum(n.endswith(".java") for n in new)==1710
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:z.writestr(info,b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and len(z.namelist())==1734
    report={"phase":81,"baseline_sha256":PIN,
        "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "dll_sha256":hashlib.sha256(old["CTransport.dll"]).hexdigest(),
        "changed":sorted(changed),"java_count":1710,"zip_entries":1734,
        "sc_legacy_broadcast_remaining":0,"native_abi":"0x000a0009",
        "item_name_MS949_actual_length_fix":True,
        "icon_writeS2_255_guard_fix":True,
        "rangeskill_large_direct_FIFO_fix":True,
        "native_pool_16384_preserved":True,
        "new_queues_workers_classes":0,
        "5000_ingame_soak_verified":False}
    (WORK/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print("AUDIT81_FULL_SOURCE_PUBLISHED",json.dumps(report),flush=True)
if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()

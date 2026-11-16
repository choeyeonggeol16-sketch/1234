#!/usr/bin/env python3
"""73: move 6 exact L1 S-packet AOI broadcast encoders onto NC-style native pooled Assemble.

This is an RKern L1-specific FFM adapter grounded in NC L2Server's observed
pool (0x41e6e4), Assemble (0x70429c), const shared reference (0x924245)
and per-socket serial SEND (0x45c7ac/0x90e6c4). It is NOT recovered L1
packet construction and does NOT add new queues, workers, or SEND policy.
Preserve phase72's exhausted-ring source lease and error semantics.
"""
from pathlib import Path
import hashlib,json,shutil,sys,zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_PoolExhaustion_NoSilentDrop_FULL_SRC_20261116.zip"
PIN="e173dc9eab31fe340fd66983e2cea4dd094c243d02aa07a053bd3dd3c4d7fca3"
OUT=ROOT/"downloads/RKern_NC_SPacket_Assemble_Extended_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve()
P="src/l1j/server/server/model/network/core/"
BCAST=P+"broadcast/CSharedBroadcast.java"
FUNCTOR=P+"broadcast/CAsyncSendConstBufferExceptSelf.java"
SERVER=P+"transport/CIOServer.java"
SOCKET=P+"connection/CUserSocket.java"
CONFIG=P+"config/CIOConfig.java"
CPP="native/CTransport.cpp"
ABI="native/interop_abi.hpp"
SC={f"{P}sc/packet/SC_{name}.java" for name in
    ("ChangeHeading","Sound","OnlyEffect","RemoveObject","SkillSound","EffectLocation")}
CHANGED=SC|{BCAST,FUNCTOR,SERVER,SOCKET,CONFIG,CPP,ABI,"CTransport.dll"}
def update(files,key,old,new):
    data=files[key].decode("utf-8")
    crlf="\r\n" in data
    s=data.replace("\r\n","\n")
    count=s.count(old)
    assert count==1,(key,"replacement count",count,old[:150])
    s=s.replace(old,new,1)
    files[key]=s.replace("\n","\r\n").encode("utf-8") if crlf else s.encode("utf-8")
def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN,"phase72 pinned baseline"
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos=z.infolist()
        files={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    old=files.copy()
    assert len(files)==1734 and sum(x.endswith(".java") for x in files)==1710

    # Enum-like internal tags for integer-only L1 C/H/D schemas; the common
    # C++ formatter never requires a Java array or another marshalling buffer.
    marker="    private CSharedBroadcast() {}\n"
    shared="""    // L1-specific formatting selectors for the existing Native Assemble bridge.
    // These are RKern tags, NOT NC opcode/ABI identifiers.
    public static final int FORMAT_HEADING = 3;
    public static final int FORMAT_SOUND = 4;
    public static final int FORMAT_ONLY_EFFECT = 5;
    public static final int FORMAT_REMOVE_OBJECT = 6;
    public static final int FORMAT_SKILL_SOUND = 7;
    public static final int FORMAT_EFFECT_LOCATION = 8;

    /**
     * One NC-style pooled plaintext, lazily assembled only after first valid
     * recipient. Keep the exact original fast/radius/filter AOI traversal,
     * ExceptSelf, recipient order and socket FIFO; no alternate SEND path.
     */
    public static void sendFormatted(
            L1Object anchor, int format, int opcode,
            int p0, int p1, int p2, int p3,
            boolean fast, int radius, Predicate<L1PcInstance> filter) {
        if (anchor == null) return;
        final L1World world = L1World.getInstance();
        final boolean borrowed = fast && radius < 0;
        final List<L1PcInstance> recipients = borrowed
                ? world.borrowVisiblePlayerFast(anchor)
                : radius < 0 ? world.getVisiblePlayer(anchor)
                             : world.getVisiblePlayer(anchor, radius);
        CAsyncSendConstBufferExceptSelf shared = null;
        try {
            for (L1PcInstance pc : recipients) {
                if (!CTLMFilter.sameWorldOther(anchor, pc)
                        || filter != null && !filter.test(pc)) continue;
                final CIOClient client = pc.getNetConnection();
                if (client == null || !client.isConnected()) continue;
                final var socket = client.getConnection();
                if (shared == null) {
                    if (socket == null || !socket.isClientConnected()) continue;
                    shared = CAsyncSendConstBufferExceptSelf.forFormatted(
                            socket, anchor.getId(), format, opcode,
                            p0, p1, p2, p3);
                    if (shared == null) throw new IllegalStateException(
                            "pooled S-packet source unavailable: " + format);
                }
                shared.send(socket, pc.getId());
            }
        } finally {
            try {
                if (shared != null) shared.close();
            } finally {
                if (borrowed) world.releaseVisiblePlayerFast(recipients);
            }
        }
    }

"""
    update(files,BCAST,marker,marker+shared)

    marker="    @Override protected boolean exclude(int recipientId) { return recipientId == senderId(); }"
    extra="""    static CAsyncSendConstBufferExceptSelf forFormatted(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int senderId, int kind, int opcode,
            int p0, int p1, int p2, int p3) {
        final CAsyncSendConstBuffer.Source pooled = socket.createPooledFormattedConstBuffer(
                kind, opcode, p0, p1, p2, p3);
        if (pooled == null) return null;
        try {
            return new CAsyncSendConstBufferExceptSelf(pooled, senderId);
        } catch (RuntimeException | Error failure) {
            pooled.release();
            throw failure;
        }
    }

"""
    update(files,FUNCTOR,marker,extra+marker)

    marker="""    public boolean ownsConstBuffer(CAsyncSendConstBuffer.Source source) { return source.owner == _channel.server(); }"""
    extra="""    public CAsyncSendConstBuffer.Source createPooledFormattedConstBuffer(
            int kind, int opcode, int p0, int p1, int p2, int p3) {
        return _channel.server().createPooledFormattedConstBuffer(
                kind, opcode, p0, p1, p2, p3);
    }
"""
    update(files,SOCKET,marker,extra+marker)

    marker="""    @SuppressWarnings("preview")
    public void releaseConstBuffer(MemorySegment handle) {"""
    extra="""    /** Format selectors 3..8 all use the existing Native pooled Assemble. */
    @SuppressWarnings("preview")
    public CAsyncSendConstBuffer.Source createPooledFormattedConstBuffer(
            int kind, int opcode, int p0, int p1, int p2, int p3) {
        final int length = switch (kind) {
            case 3 -> 8;   // S_CHANGE_DIRECTION: cdc
            case 4 -> 6;   // S_SOUND_EFFECT: cch
            case 5 -> 9;   // S_EFFECT (short): cdh
            case 6 -> 9;   // S_REMOVE_OBJECT: cdh
            case 7 -> 15;  // S_EFFECT (full): cdhhd
            case 8 -> 10;  // S_EFFECT_LOC: chhhc
            default -> throw new IllegalArgumentException("unknown pooled L1 format: " + kind);
        };
        if (!enterNative()) return null;
        MemorySegment handle = MemorySegment.NULL;
        try {
            handle = (MemorySegment) createFormattedConstBuffer.invokeExact(
                    server, kind, opcode, p0, p1, p2, p3);
            if (handle.address() == 0) return null;
            final CAsyncSendConstBuffer.Source source = pooledSource(handle, length);
            constBuffers.incrementAndGet();
            return source;
        } catch (Throwable failure) {
            if (handle.address() != 0) {
                try { releaseConstBuffer.invokeExact(handle); }
                catch (Throwable releaseFailure) { failure.addSuppressed(releaseFailure); }
            }
            throw new IllegalStateException("Native pooled L1 format " + kind + " failed", failure);
        } finally {
            nativeCalls.decrementAndGet();
        }
    }

"""
    update(files,SERVER,marker,extra+marker)

    # The exact C++ branch is version 72 (native source pointer directly in
    # CIOBufferEx, with acquireSharedSource() for fixed ring exhaustion).
    data=files[CPP].decode("utf-8")
    find="""        return nullptr;
    }
    CIO_API const void* L1Interop_ConstBufferData"""
    # Replace only the last branch of L1Interop_CreateFormattedConstBuffer.
    start=data.index("CIO_API void* L1Interop_CreateFormattedConstBuffer(")
    end=data.index("CIO_API const void* L1Interop_ConstBufferData",start)
    part=data[start:end]
    assert part.count("        return nullptr;")==1,("expected final kind fallthrough",part[-800:])
    newbranches="""        if(kind==3){ // S_CHANGE_DIRECTION, same cdc schema as action
            const std::uint64_t args[3]={
                static_cast<unsigned>(opcode),static_cast<unsigned>(objectId),
                static_cast<unsigned>(a)};
            return AssemblePooled(server,"cdc",args,3);
        }
        if(kind==4){ // S_SOUND_EFFECT: opcode, zero mode, short soundId
            const std::uint64_t args[3]={
                static_cast<unsigned>(opcode),0u,static_cast<unsigned>(objectId)};
            return AssemblePooled(server,"cch",args,3);
        }
        if(kind==5||kind==6){ // S_EFFECT short / S_REMOVE_OBJECT
            const std::uint64_t args[3]={
                static_cast<unsigned>(opcode),static_cast<unsigned>(objectId),
                kind==5?static_cast<unsigned>(a):0u};
            return AssemblePooled(server,"cdh",args,3);
        }
        if(kind==7){ // Full skill S_EFFECT, with original two zero trailers
            const std::uint64_t args[5]={
                static_cast<unsigned>(opcode),static_cast<unsigned>(objectId),
                static_cast<unsigned>(a),0u,0u};
            return AssemblePooled(server,"cdhhd",args,5);
        }
        if(kind==8){ // S_EFFECT_LOC: x/y/gfx, with zero final byte
            const std::uint64_t args[5]={
                static_cast<unsigned>(opcode),static_cast<unsigned>(objectId),
                static_cast<unsigned>(a),static_cast<unsigned>(b),0u};
            return AssemblePooled(server,"chhhc",args,5);
        }
"""
    part=part.replace("        return nullptr;",newbranches+"        return nullptr;",1)
    assert "acquireSharedSource" in data and "AssemblePooled(server,\"chhhc\"" in part
    files[CPP]=(data[:start]+part+data[end:]).encode("utf-8")

    # ABI pin protects against mixing the new Java selectors with an old DLL.
    update(files,ABI,"0x000a0004","0x000a0005")
    update(files,CONFIG,"ABI = 0x000a0004","ABI = 0x000a0005")

    # Migrate ONLY existing broadcast methods. 1:1 senders and packet oracle
    # functions stay byte-identical. Preserve all original fast AOI variants.
    heading=P+"sc/packet/SC_ChangeHeading.java"
    update(files,heading,
"""        byte[] wire = java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_CHANGE_DIRECTION).putInt(objectId).put((byte) heading).array();
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, wire, fast, -1, null);""",
"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_HEADING,
                Opcodes.S_CHANGE_DIRECTION, objectId, heading, 0, 0, fast, -1, null);""")

    sound=P+"sc/packet/SC_Sound.java"
    update(files,sound,"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(sound), false, -1, null);""",
"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_SOUND,
                Opcodes.S_SOUND_EFFECT, sound, 0, 0, 0, false, -1, null);""")
    update(files,sound,"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(sound), true, -1, null);""",
"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_SOUND,
                Opcodes.S_SOUND_EFFECT, sound, 0, 0, 0, true, -1, null);""")

    only=P+"sc/packet/SC_OnlyEffect.java"
    for fast in ("false","true"):
        update(files,only,
f"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, effectId), {fast}, -1, null);""",
f"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_ONLY_EFFECT,
                Opcodes.S_EFFECT, objectId, effectId, 0, 0, {fast}, -1, null);""")

    remove=P+"sc/packet/SC_RemoveObject.java"
    update(files,remove,
"""        byte[] wire = java.nio.ByteBuffer.allocate(WIRE_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putShort((short) WIRE_SIZE).put((byte) Opcodes.S_REMOVE_OBJECT).putInt(objectId).putShort((short) 0).array();
        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, wire, true, -1, null);""",
"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_REMOVE_OBJECT,
                Opcodes.S_REMOVE_OBJECT, objectId, 0, 0, 0, true, -1, null);""")

    skill=P+"sc/packet/SC_SkillSound.java"
    update(files,skill,
"""        if (anchor != null) l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(anchor, sharedWire(objectId, gfxId), fast, -1, null);""",
"""        if (anchor != null) l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                anchor, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_SKILL_SOUND,
                Opcodes.S_EFFECT, objectId, gfxId, 0, 0, fast, -1, null);""")
    update(files,skill,
"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(c, sharedWire(objectId, gfxId), false, -1, pc -> pc.getLinMeff() != 2);""",
"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                c, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_SKILL_SOUND,
                Opcodes.S_EFFECT, objectId, gfxId, 0, 0, false, -1, pc -> pc.getLinMeff() != 2);""")
    update(files,skill,
"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send(c, sharedWire(objectId, gfxId), false, 50, null);""",
"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                c, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_SKILL_SOUND,
                Opcodes.S_EFFECT, objectId, gfxId, 0, 0, false, 50, null);""")

    loc=P+"sc/packet/SC_EffectLocation.java"
    cases=[
        ("anchor","false","-1","null","broadcast"),
        ("c","true","-1","null","broadcastFast"),
        ("c","false","-1","pc -> pc.getLinMeff() != 2","broadcastM"),
        ("c","false","50","null","broadcastWide"),
    ]
    for anchor,fast,radius,filter_,method in cases:
        update(files,loc,
f"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.send({anchor}, sharedWire(x, y, gfxId), {fast}, {radius}, {filter_});""",
f"""        l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendFormatted(
                {anchor}, l1j.server.server.model.network.core.broadcast.CSharedBroadcast.FORMAT_EFFECT_LOCATION,
                Opcodes.S_EFFECT_LOC, x, y, gfxId, 0, {fast}, {radius}, {filter_});""")

    touched={name for name,data in files.items() if old[name]!=data}
    expected=CHANGED-{"CTransport.dll"}
    assert touched==expected,("unexpected production change",touched^expected)
    assert all(files[n]==old[n] for n in old if n not in expected)
    assert "if (shared == null) throw new IllegalStateException" in files[BCAST].decode()
    assert "sourceOverflowRecord" in files["native/cio_core.hpp"].decode()
    for name,data in files.items():
        if name.startswith("src/") and name.endswith(".java"):
            dst=WORK/name
            dst.parent.mkdir(parents=True,exist_ok=True)
            dst.write_bytes(data)
    # Copy original native sources, replacing only exact patched files.
    for name,data in files.items():
        if name.startswith("native/"):
            dst=WORK/name
            dst.parent.mkdir(parents=True,exist_ok=True)
            dst.write_bytes(data)
    native_root=ROOT/"patches/ctransport_core_20261112/native"
    for src in native_root.iterdir():
        if src.is_file() and not (WORK/"native"/src.name).exists():
            shutil.copy2(src,WORK/"native"/src.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for directory in ("build","logs"):
        (WORK/directory).mkdir(exist_ok=True)
    (WORK/"original-hashes.json").write_text(json.dumps({
        "input_sha256":PIN,"modified_java":[x for x in sorted(touched) if x.endswith(".java")],
        "modified_cpp":[x for x in sorted(touched) if x.endswith((".cpp",".hpp"))],
        "old_entries":len(files),"original_java_count":1710},indent=2)+"\n")
    print("PHASE73_PREPARED",len(touched),"source files",len(SC),"packet classes")

def package():
    dll=WORK/"build/CTransport.dll"
    dllsha=hashlib.sha256(dll.read_bytes()).hexdigest()
    assert dllsha in (WORK/CONFIG).read_text(encoding="utf-8"),"DLL pinned checksum not updated"
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist()
        old={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    new=old.copy()
    for name in CHANGED-{"CTransport.dll"}:
        new[name]=(WORK/name).read_bytes()
    new["CTransport.dll"]=dll.read_bytes()
    touched={n for n in old if old[n]!=new[n]}
    assert touched==CHANGED,(sorted(touched),sorted(CHANGED))
    assert len(new)==1734
    assert sum(n.endswith(".java") for n in new)==1710
    OUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:
            z.writestr(info, b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None
        assert len(z.namelist())==1734
    audit={
        "phase":73,"baseline_sha256":PIN,
        "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "dll_sha256":dllsha,"native_abi":"0x000a0005",
        "production_changed":sorted(touched),
        "packet_classes":sorted(SC),
        "java_count":1710,"zip_entries":1734,
        "old_move_action_native_path_untouched":True,
        "pool_exhaustion_phase72_preserved":True,
        "no_new_queues_workers_classes":True,
        "existing_socket_fifo_cipher_preserved":True,
        "in_game_5000_soak_verified":False
    }
    (WORK/"audit.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n")
    print("PHASE73_NC_6_PACKET_ASSEMBLE_FULL_SRC_VERIFIED",json.dumps(audit,ensure_ascii=False))
if __name__=="__main__":
    if len(sys.argv)<3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()

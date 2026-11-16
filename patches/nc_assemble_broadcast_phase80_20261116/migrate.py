#!/usr/bin/env python3
"""80: remove the final four SC legacy broadcast helper call-sites.
Exact phase79 input; keep owner FIFO, cipher, source lifetime and recipients.
No new worker, queue or Java class; no shipping Native DLL/ABI modification.
"""
from pathlib import Path
import hashlib,json,re,shutil,sys,zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase79_FULL_SRC_20261116.zip"
PIN="a27401dc579e5c2086e1a7336ea55e3c0b26e3eff5e194171e1ad7050ad78731"
OUT=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase80_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else None
P="src/l1j/server/server/model/network/core/"
BC=P+"broadcast/CSharedBroadcast.java"
FILES={n:P+"sc/packet/SC_"+n+".java" for n in ("AreaAttackEffect","ChatPacket","DropItem","UseArrowSkill")}
CHANGED={BC}|set(FILES.values())
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"

def norm(files,key):
    raw=files[key].decode("utf-8")
    return raw.replace("\r\n","\n"),"\r\n" in raw

def put(files,key,s,crlf):
    files[key]=(s.replace("\n","\r\n") if crlf else s).encode("utf-8")

def update(files,key,old,new):
    s,crlf=norm(files,key)
    assert s.count(old)==1,(key,"anchor count",s.count(old),old[:120])
    put(files,key,s.replace(old,new,1),crlf)

def add_broadcast_utilities(files):
    marker="    public static void sendWrittenAll(NativeWireWriter writer) {"
    addition="""    // One Native pool source across explicit L1Object recipients. Reuse the
    // phase76 combat formatter; no AOI/self filter or duplicate suppression.
    public static void sendExtendedObjects(Iterable<? extends L1Object> objects,
            int kind,int opcode,int p0,int p1,int p2,int p3,int p4,int p5,
            int p6,int p7,int p8,int p9,int p10) {
        if(objects==null)return;
        CAsyncSendConstBufferExceptSelf shared=null;
        try {
            for(L1Object object:objects) {
                if(!(object instanceof L1PcInstance pc))continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket==null||!socket.isClientConnected())continue;
                if(shared==null) {
                    shared=CAsyncSendConstBufferExceptSelf.forExtended(
                            socket,Integer.MIN_VALUE,kind,opcode,
                            p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10);
                    if(shared==null)throw new IllegalStateException(
                            "Native explicit combat source unavailable");
                }
                shared.send(socket,pc.getId());
            }
        } finally {
            if(shared!=null)shared.close();
        }
    }

    // One lazily assembled immutable source; self is sent first, then every
    // original AOI member (including duplicates). No ExceptSelf suppression.
    private static CAsyncSendConstBuffer sendWrittenTo(
            CAsyncSendConstBuffer shared,L1PcInstance pc,NativeWireWriter writer) {
        if(pc==null)return shared;
        final CIOClient client=pc.getNetConnection();
        if(client==null||!client.isConnected())return shared;
        final var socket=client.getConnection();
        if(socket==null||!socket.isClientConnected())return shared;
        if(shared==null) {
            final CAsyncSendConstBuffer.Source source=
                    CAsyncSendConstBuffer.openWrittenSource(socket,writer);
            if(source==null)throw new IllegalStateException(
                    "Native self-and-visible source unavailable");
            try {
                shared=new CAsyncSendConstBuffer(source,Integer.MIN_VALUE,Integer.MAX_VALUE);
            } catch(RuntimeException|Error failure) {
                source.release();
                throw failure;
            }
        }
        shared.send(socket,pc.getId());
        return shared;
    }

    public static void sendWrittenSelfAndVisible(
            l1j.server.server.model.L1Character anchor,NativeWireWriter writer) {
        if(anchor==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            if(anchor instanceof L1PcInstance pc)
                shared=sendWrittenTo(shared,pc,writer);
            for(L1PcInstance viewer:L1World.getInstance().getVisiblePlayer(anchor))
                shared=sendWrittenTo(shared,viewer,writer);
        } finally {
            if(shared!=null)shared.close();
        }
    }

    // Ground item AOI: do not make the item 'known' until SEND was admitted.
    // Preserve the visible-list borrow, all duplicate entries and recipient FIFO.
    public static int sendWrittenGroundItem(
            l1j.server.server.model.Instance.L1ItemInstance item,
            boolean addKnownObject,NativeWireWriter writer) {
        if(item==null||writer==null)return 0;
        int viewers=0;
        CAsyncSendConstBuffer shared=null;
        final L1World world=L1World.getInstance();
        final List<L1PcInstance> visible=world.borrowVisiblePlayerFast(item);
        try {
            for(L1PcInstance pc:visible) {
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket==null||!socket.isClientConnected())continue;
                if(shared==null) {
                    final CAsyncSendConstBuffer.Source source=
                            CAsyncSendConstBuffer.openWrittenSource(socket,writer);
                    if(source==null)throw new IllegalStateException(
                            "Native ground item source unavailable");
                    try {
                        shared=new CAsyncSendConstBuffer(source,item.getId(),Integer.MAX_VALUE);
                    } catch(RuntimeException|Error failure) {
                        source.release();
                        throw failure;
                    }
                }
                if(shared.send(socket,pc.getId())) {
                    if(addKnownObject)pc.addKnownObject(item);
                    viewers++;
                }
            }
        } finally {
            try {if(shared!=null)shared.close();}
            finally {world.releaseVisiblePlayerFast(visible);}
        }
        return viewers;
    }

"""
    update(files,BC,marker,addition+marker)

def move_chat(files):
    key=FILES["ChatPacket"]
    old="""        final byte[] wire=sharedDirectAdmissionWire(Opcodes.S_MESSAGE,1,0,chat,false);
        if(wire!=null)
            """+FQ+"""
                    .sendRecipients(L1World.getInstance().getAllPlayers(),wire);"""
    new="""        final String safe=chat==null?"":chat;
        if(32L+4L*safe.length()>0xFFFFL)return;
        """+FQ+""".sendWrittenAll(
                b -> writeNativeChat(b,Opcodes.S_MESSAGE,1,0,safe,false));"""
    update(files,key,old,new)
    old="""        final byte[] wire=sharedDirectAdmissionWire(opcode,2,0,chat,false);
        if(wire!=null)
            """+FQ+"""
                    .sendRecipients(L1World.getInstance().getAllPlayers(),wire);"""
    new="""        final String safe=chat==null?"":chat;
        if(32L+4L*safe.length()>0xFFFFL)return;
        """+FQ+""".sendWrittenAll(
                b -> writeNativeChat(b,opcode,2,0,safe,false));"""
    update(files,key,old,new)
    old="""        final byte[] wire=sharedDirectAdmissionWire(opcode,0,source.getId(),
                name+": "+(chat==null?"":chat),true);
        if(wire==null)return;
        final L1World world=L1World.getInstance();
        final java.util.List<L1PcInstance> visible=world.borrowVisiblePlayerFast(source);
        try {
            """+FQ+"""
                    .sendRecipients(visible,wire);
        } finally {
            world.releaseVisiblePlayerFast(visible);
        }"""
    new="""        final String formatted=name+": "+(chat==null?"":chat);
        if(32L+4L*formatted.length()>0xFFFFL)return;
        final L1World world=L1World.getInstance();
        final java.util.List<L1PcInstance> visible=world.borrowVisiblePlayerFast(source);
        try {
            """+FQ+""".sendWrittenRecipients(visible,
                b -> writeNativeChat(b,opcode,0,source.getId(),formatted,true));
        } finally {
            world.releaseVisiblePlayerFast(visible);
        }"""
    update(files,key,old,new)
    marker="    /** Full immutable frame matching MODE_MSG_TYPE1, MODE_OPCODE_TYPE2,"
    writer="""    // Original sharedWire() is kept as a test oracle and for 1:1 callers.
    // The broadcast itself always writes directly into the pooled native slab.
    private static void writeNativeChat(java.nio.ByteBuffer b,int opcode,
            int type,int objectId,String text,boolean includeObjectId) {
        b.put((byte)opcode).put((byte)type);
        if(includeObjectId)b.putInt(objectId);
        """+FQ+""".writeMS949(b,text);
        b.put((byte)0);
    }

"""
    update(files,key,marker,writer+marker)

def move_area(files):
    key=FILES["AreaAttackEffect"]
    old="""        final byte[] wire=sharedWire(id,x,y,heading,seq,gfx,action,ids);
        if(wire==null)return;
        if(wire.length>16384) {
            final byte[] payload=java.util.Arrays.copyOfRange(wire,2,wire.length);
            if(caster instanceof L1PcInstance)
                sendLarge((L1PcInstance)caster,wire.length,payload);
            for(L1PcInstance viewer:L1World.getInstance().getVisiblePlayer(caster))
                sendLarge(viewer,wire.length,payload);
            return;
        }
        try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            // Preserve the old self-first emission even if the AOI query also
            // contains that player; duplicate recipients stay duplicated.
            if(caster instanceof L1PcInstance)
                sendShared(shared,(L1PcInstance)caster);
            for(L1PcInstance viewer:L1World.getInstance().getVisiblePlayer(caster))
                sendShared(shared,viewer);
        }"""
    new="""        final long length=26L+6L*ids.length;
        if(length>0xffffL)return;
        // The 16-KiB Native pool is fixed. Large multi-target frames retain
        // the existing explicit large-frame socket FIFO, not another SC
        // broadcast serializer. This case needs >=2727 targets at once.
        if(length>16384L) {
            final byte[] wire=sharedWire(id,x,y,heading,seq,gfx,action,ids);
            if(wire==null)return;
            final byte[] payload=java.util.Arrays.copyOfRange(wire,2,wire.length);
            if(caster instanceof L1PcInstance)
                sendLarge((L1PcInstance)caster,wire.length,payload);
            for(L1PcInstance viewer:L1World.getInstance().getVisiblePlayer(caster))
                sendLarge(viewer,wire.length,payload);
            return;
        }
        """+FQ+""".sendWrittenSelfAndVisible(caster,b -> {
            b.put((byte)Opcodes.S_ATTACK_MANY).put((byte)action).putInt(id);
            b.putShort((short)x).putShort((short)y).put((byte)heading);
            b.putInt(seq).putShort((short)gfx).put((byte)0)
             .putShort((short)0).putShort((short)ids.length);
            for(int targetId:ids) {
                b.putInt(targetId).put((byte)0x20)
                 .put((byte)ActionCodes.ACTION_Damage);
            }
            b.putShort((short)0);
        });"""
    update(files,key,old,new)
    s,crlf=norm(files,key)
    marker="    private static void sendShared("
    start=s.index(marker)
    end=s.index("\n    }",start)+len("\n    }")
    assert s.count(marker)==1
    put(files,key,s[:start]+s[end:],crlf)

def move_drop(files):
    key=FILES["DropItem"]
    s,crlf=norm(files,key)
    i=s.index("\t\tint viewers = 0;")
    j=s.index("\n}\n\n\tprivate static boolean send(",i)
    block=s[i:j]
    assert block.count("new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer")==1
    assert block.count("addKnownObject")>=2
    replacement="""        final String frozenName=itemName==null?"":itemName;
        final long required=(long)FIXED_WIRE_BYTES+((long)frozenName.length()<<1);
        if(required>16384L)return 0;
        return """+FQ+""".sendWrittenGroundItem(item,addKnownObject,b -> {
            b.put((byte)Opcodes.S_PUT_OBJECT).putShort((short)x)
             .putShort((short)y).putInt(objectId).putShort((short)groundGfxId);
            b.put((byte)0).put((byte)0).put((byte)lightRange).put((byte)0);
            b.putInt(count).put((byte)0).put((byte)0);
            """+FQ+""".writeMS949(b,frozenName);
            b.put((byte)0).put((byte)0).putInt(0).putInt(0);
            b.put((byte)255).put((byte)0).put((byte)0).put((byte)0);
            b.putShort((short)65535).putInt(0).put((byte)8).put((byte)0);
        });
"""
    put(files,key,s[:i]+replacement+s[j:],crlf)

def move_arrow(files):
    key=FILES["UseArrowSkill"]
    old="""		        try (var shared = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                sharedWire(attackerId,ox,oy,oh,actId,targetId,spellGfx,tx,ty,isHit,seq),
                Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for (L1Object o : receivers) {
                if (!(o instanceof L1PcInstance)) continue;
                final L1PcInstance pc=(L1PcInstance)o;
                final CIOClient client=pc.getNetConnection();
                if(client!=null && client.isConnected()) shared.send(client.getConnection(),pc.getId());
            }
        }"""
    new="""        """+FQ+""".sendExtendedObjects(
                receivers,"""+FQ+""".FORMAT_ARROWSKILL,Opcodes.S_ATTACK,
                attackerId,ox,oy,oh,actId,targetId,spellGfx,tx,ty,
                isHit?1:0,seq);"""
    update(files,key,old,new)

def make_codec_writers_testable(files):
    # Only the codec body is extracted; the broadcast uses this exact method,
    # and the Windows fixture can compare it to the frozen original bytes.
    key=FILES["AreaAttackEffect"]
    src,crlf=norm(files,key)
    start=src.index("        "+FQ+".sendWrittenSelfAndVisible(caster,b -> {")
    end=src.index("        });",start)+len("        });")
    call="""        """+FQ+""".sendWrittenSelfAndVisible(caster,
                b -> writeNativeArea(b,id,x,y,heading,seq,gfx,action,ids));"""
    src=src[:start]+call+src[end:]
    marker="    public static byte[] sharedWire("
    method="""    private static void writeNativeArea(java.nio.ByteBuffer b,
            int id,int x,int y,int heading,int seq,int gfx,int action,int[] ids) {
        b.put((byte)Opcodes.S_ATTACK_MANY).put((byte)action).putInt(id);
        b.putShort((short)x).putShort((short)y).put((byte)heading);
        b.putInt(seq).putShort((short)gfx).put((byte)0)
         .putShort((short)0).putShort((short)ids.length);
        for(int targetId:ids)
            b.putInt(targetId).put((byte)0x20)
             .put((byte)ActionCodes.ACTION_Damage);
        b.putShort((short)0);
    }

"""
    assert src.count(marker)==1
    put(files,key,src.replace(marker,method+marker,1),crlf)
    key=FILES["DropItem"]
    src,crlf=norm(files,key)
    start=src.index("        return "+FQ+".sendWrittenGroundItem(item,addKnownObject,b -> {")
    end=src.index("        });",start)+len("        });")
    call="""        return """+FQ+""".sendWrittenGroundItem(item,addKnownObject,
                b -> writeNativeGroundItem(b,x,y,objectId,groundGfxId,
                        lightRange,count,frozenName));"""
    src=src[:start]+call+src[end:]
    marker="    private static byte[] sharedWire("
    method="""    private static void writeNativeGroundItem(java.nio.ByteBuffer b,
            int x,int y,int objectId,int groundGfxId,int lightRange,int count,
            String frozenName) {
        b.put((byte)Opcodes.S_PUT_OBJECT).putShort((short)x)
         .putShort((short)y).putInt(objectId).putShort((short)groundGfxId);
        b.put((byte)0).put((byte)0).put((byte)lightRange).put((byte)0);
        b.putInt(count).put((byte)0).put((byte)0);
        """+FQ+""".writeMS949(b,frozenName);
        b.put((byte)0).put((byte)0).putInt(0).putInt(0);
        b.put((byte)255).put((byte)0).put((byte)0).put((byte)0);
        b.putShort((short)65535).putInt(0).put((byte)8).put((byte)0);
    }

"""
    assert src.count(marker)==1
    put(files,key,src.replace(marker,method+marker,1),crlf)

def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN,"phase79 baseline SHA mismatch"
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos=z.infolist()
        files={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    old=files.copy()
    assert len(files)==1734 and sum(x.endswith(".java") for x in files)==1710
    add_broadcast_utilities(files)
    move_chat(files);move_area(files);move_drop(files);move_arrow(files)
    make_codec_writers_testable(files)
    touched={n for n in old if old[n]!=files[n]}
    assert touched==CHANGED,("phase80 unexpected diff",sorted(touched^CHANGED))
    for n,data in files.items():
        if n.startswith("native/") or (n.startswith("src/") and n.endswith(".java")):
            dst=WORK/n;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(data)
    native_root=ROOT/"patches/ctransport_core_20261112/native"
    for src in native_root.iterdir():
        if src.is_file() and not (WORK/"native"/src.name).exists():
            shutil.copy2(src,WORK/"native"/src.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for dirname in ("build","logs"):(WORK/dirname).mkdir(exist_ok=True)
    print("PHASE80_FOUR_CUSTOM_SC_SENDERS_PREPARED",sorted(touched),flush=True)

def package():
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist()
        old={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    assert (WORK/"build/CTransport.dll").read_bytes()==old["CTransport.dll"]
    new=old.copy()
    for n in CHANGED:new[n]=(WORK/n).read_bytes()
    touched={n for n in old if new[n]!=old[n]}
    assert touched==CHANGED,sorted(touched^CHANGED)
    legacy=[]
    for n,data in new.items():
        if "/sc/packet/SC_" not in n or not n.endswith(".java"):continue
        s=data.decode("utf-8")
        for method in ("send","sendRecipients","sendAll","sendRawRecipients",
                       "sendRawArray","sendArray","sendObjectPlayers","sendRawSelfAndVisible"):
            if re.search(r"(?:CSharedBroadcast\s*\.\s*)"+re.escape(method)+r"\s*\(",s):
                legacy.append((n,method))
        if re.search(r"new\s+(?:l1j\.server\.server\.model\.network\.core\.broadcast\.)?CAsyncSendConstBuffer\s*\(",s):
            legacy.append((n,"new CAsyncSendConstBuffer"))
    assert not legacy,("SC old broadcast call sites remain",legacy)
    assert len(new)==1734 and sum(n.endswith(".java") for n in new)==1710
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:z.writestr(info,b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and len(z.namelist())==1734
    audit={"phase":80,"baseline_sha256":PIN,
        "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "dll_sha256":hashlib.sha256(old["CTransport.dll"]).hexdigest(),
        "native_abi":"0x000a0009","changed":sorted(touched),
        "java_count":1710,"zip_entries":1734,"sc_legacy_broadcast_remaining":0,
        "all_SC_java_files_scanned":True,"no_new_queues_workers_classes":True,
        "native_socket_fifo_cipher_preserved":True,
        "large_area_attack_existing_direct_FIFO_preserved":True,
        "5000_ingame_soak_verified":False}
    (WORK/"audit.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n")
    print("PHASE80_ALL_SC_OLD_SHARED_BROADCAST_REMOVED",json.dumps(audit),flush=True)

if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()

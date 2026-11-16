#!/usr/bin/env python3
"""76: combat SC fixed-wire native pooled Assemble; 73-75 lineage exact.
No per-recipient packet encoder, new queue, worker, new classes, or fallback.
"""
from pathlib import Path
import hashlib,json,re,shutil,sys,zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase75_FULL_SRC_20261116.zip"
PIN="d8fea60d98f48dd84385ccb0b16d49fbfae7d8c42467be129befc806b3f3bc4a"
OUT=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase76_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else None
P="src/l1j/server/server/model/network/core/"
SHARED=P+"broadcast/CSharedBroadcast.java"
FUNCTOR=P+"broadcast/CAsyncSendConstBufferExceptSelf.java"
SOCKET=P+"connection/CUserSocket.java"
SERVER=P+"transport/CIOServer.java"
CONFIG=P+"config/CIOConfig.java"
CPP="native/CTransport.cpp"
ABI="native/interop_abi.hpp"
PKTS=("ChangeShape","Attack","AttackCritical","AttackMissPacket",
      "AttackPacket","AttackPacketForNpc","AttackStatus",
      "UseArrowSkill","UseAttackSkill")
SC={name:P+"sc/packet/SC_"+name+".java" for name in PKTS}
CHANGED=set(SC.values())|{SHARED,FUNCTOR,SOCKET,SERVER,CONFIG,CPP,ABI,"CTransport.dll"}
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"
CONST={
"CHANGESHAPE":31,"ATTACKMISS":32,"ATTACKNPC":33,"ATTACKSTATUS":34,
"ATTACK":35,"CRITICAL1":36,"CRITICAL2":37,
"ATTACKPACKET1":38,"ATTACKPACKET2":39,"ATTACKPACKET3":40,
"ARROWSKILL":41,"ATTACKSKILL":42
}
# L1 wire templates are separately checked against original SC byte builders.
# p0..p10 are frozen primitive values, unused inputs are zero.
FMT={
31:("cdhccccccccc",["opcode","p0","p1","p2","0xffu","0xffu","0u","97u","98u","99u","100u","0u"],18),
32:("ccddcccdc",["opcode","p2","p0","p1","0u","0u","p3","0u","0u"],20),
33:("ccddhchhc",["opcode","p0","p1","p2","1u","p3","0u","0u","0u"],20),
34:("ccddcccd",["opcode","p3","p0","p2","1u","0u","p1","0u"],19),
35:("ccddhcdhchhhhdhch",["opcode","1u","p0","p1","p2","p3","p4","p5","0u","p6","p7","p8","p9","0u","0u","0u","0u"],39),
36:("ccddhchhcdh",["opcode","1u","p0","p1","1u","p2","0u","0u","2u","p3","0u"],26),
37:("ccddcccdhchhhhcccc",["opcode","p3","p0","p1","(p4 ? 6u : 0u)","0u","p2","p5","p6","0u","p7","p8","p9","p10","0u","0u","0u","0u"],34),
38:("ccddcccdc",["opcode","p3","p2","p1","0x4eu","0u","p4","0u","0u"],20),
39:("ccddhchhc",["opcode","p3","p1","p2","2u","p4","0u","0u","p5"],20),
40:("ccddccchhchhh",["opcode","p3","p1","p2","1u","0u","p4","0u","0u","p5","p6","0u","0u"],26),
41:("ccddcccdhchhhhccccc",["opcode","p4","p0","p5","(p9 ? 6u : 0u)","0u","p3","p10","p6","0u","p1","p2","p7","p8","0u","0u","0u","0u","0u"],35),
42:("ccddcccdhchhhhccccc",["opcode","p0","p1","p2","p3","0u","p4","p5","p6","6u","p7","p8","p9","p10","0u","0u","0u","0u","0u"],35),
}
WIDTH={"c":1,"h":2,"d":4,"Q":8}
assert set(FMT)==set(CONST.values())
for kind,(fmt,args,length) in FMT.items():
    assert all(c in WIDTH for c in fmt),kind
    assert len(fmt)==len(args),(kind,len(fmt),len(args))
    assert 2+sum(WIDTH[c] for c in fmt)==length,(kind,fmt,length)

def update(files,key,old,new):
    o=files[key].decode("utf-8")
    crlf="\r\n" in o
    s=o.replace("\r\n","\n")
    assert s.count(old)==1,(key,"bad replacement",s.count(old),old[:140])
    s=s.replace(old,new,1)
    files[key]=(s.replace("\n","\r\n") if crlf else s).encode("utf-8")

def exact_call(files,name,old,new):
    key=SC[name]
    update(files,key,FQ+".send("+old+");",FQ+".sendExtended("+new+");")

def params(*items):
    assert len(items)<=11
    return ", ".join(list(items)+["0"]*(11-len(items)))

def call(anchor,kind,opcode,values,fast):
    return f"{anchor}, {FQ}.FORMAT_{kind}, Opcodes.{opcode}, {params(*values)}, {fast}"

def drop_private(files,name,method):
    key=SC[name]
    orig=files[key].decode("utf-8")
    crlf="\r\n" in orig
    s=orig.replace("\r\n","\n")
    if s.count(method+"(")!=1:return False
    m=re.search(r"\bprivate static byte\[\]\s+"+re.escape(method)+r"\s*\(",s)
    assert m is not None,(name,method)
    a=s.rfind("\n",0,m.start())+1
    b=s.index("{",m.start())
    d=0;end=-1
    for i in range(b,len(s)):
        if s[i]=="{":d+=1
        elif s[i]=="}":
            d-=1
            if d==0:end=i+1;break
    assert end>b
    s=s[:a]+s[end:].lstrip("\n")
    files[key]=(s.replace("\n","\r\n") if crlf else s).encode("utf-8")
    return True

def migrate_sc(files):
    # Preserve caller ordering: self SEND and sequence-number increment remain
    # in exactly the same position, with only shared AOI encoder replaced.
    for fast in ("false","true"):
        exact_call(files,"ChangeShape",
            f"anchor, sharedWire(objectId, polyId, weaponState), {fast}, -1, null",
            call("anchor","CHANGESHAPE","S_POLYMORPH",("objectId","polyId","weaponState"),fast))
    exact_call(files,"Attack",
        "pc, sharedWire(attackerId, targetId, damage, heading, seq, effect, x, y, endX, endY), true, -1, null",
        call("pc","ATTACK","S_ATTACK",("attackerId","targetId","damage","heading","seq","effect","x","y","endX","endY"),"true"))
    exact_call(files,"AttackCritical",
        "anchor, sharedWireMode1(attackerId, targetId, heading, gfxId), true, -1, null",
        call("anchor","CRITICAL1","S_ATTACK",("attackerId","targetId","heading","gfxId"),"true"))
    exact_call(files,"AttackCritical",
        "anchor, sharedWireMode2(attackerId, targetId, heading, aid, isHit, seq, gfxId, targetX, targetY, chaX, chaY), true, -1, null",
        call("anchor","CRITICAL2","S_ATTACK",("attackerId","targetId","heading","aid","(isHit ? 1 : 0)","seq","gfxId","targetX","targetY","chaX","chaY"),"true"))
    exact_call(files,"AttackMissPacket",
        "attacker, sharedWire(attackerId, targetId, actId, heading), true, -1, null",
        call("attacker","ATTACKMISS","S_ATTACK",("attackerId","targetId","actId","heading"),"true"))
    exact_call(files,"AttackPacketForNpc",
        "anchor, sharedWire(type, npcObjectId, chaId, heading), true, -1, null",
        call("anchor","ATTACKNPC","S_ATTACK",("type","npcObjectId","chaId","heading"),"true"))
    for fast in ("false","true"):
        exact_call(files,"AttackStatus",
            f"anchor, sharedWire(attackerId, heading, receiverId, type), {fast}, -1, null",
            call("anchor","ATTACKSTATUS","S_ATTACK",("attackerId","heading","receiverId","type"),fast))
    k=SC["AttackPacket"]
    old=FQ+".send(cha, sharedWire(mode, chaId, objId, type, heading, attackType, gfx), true, -1, null);"
    new=("final int nativeKind = switch(mode) {\n"
         f"            case MODE_1 -> {FQ}.FORMAT_ATTACKPACKET1;\n"
         f"            case MODE_2 -> {FQ}.FORMAT_ATTACKPACKET2;\n"
         f"            case MODE_3 -> {FQ}.FORMAT_ATTACKPACKET3;\n"
         "            default -> throw new IllegalArgumentException(\"Unsupported attack mode \" + mode);\n"
         "        };\n"
         f"        {FQ}.sendExtended("
         f"cha, nativeKind, Opcodes.S_ATTACK, {params('0','chaId','objId','type','heading','attackType','gfx')}, true);")
    update(files,k,old,new)
    for anchor,receiver,tx,ty in [
      ("pc","targetObj","x","y"),("cha","targetObj","x","y"),
      ("anchor","targetId","tx","ty")]:
        orig=(f"{anchor}, sharedWire(attackerId,ox,oy,oh,actId,{receiver},spellGfx,"
              f"{tx},{ty},isHit,seq), true, -1, null")
        extended=call(anchor,"ARROWSKILL","S_ATTACK",
            ("attackerId","ox","oy","oh","actId",receiver,"spellGfx",tx,ty,
             "(isHit ? 1 : 0)","seq"),"true")
        exact_call(files,"UseArrowSkill",orig,extended)
    for anchor,act,who,target,x,y,tx,ty in [
        ("cha","fixedActionId","chaId","targetObj","myX","myY","x","y"),
        ("pc","fixedActionId","chaId","targetObj","myX","myY","x","y"),
        ("anchor","actionId","chaId","targetObjId","myX","myY","targetX","targetY")]:
        orig=(f"{anchor}, sharedWire({act},{who},{target},isHit,heading,seq,"
              f"spellGfx,{x},{y},{tx},{ty}), true, -1, null")
        extended=call(anchor,"ATTACKSKILL","S_ATTACK",
            (act,who,target,"isHit","heading","seq","spellGfx",x,y,tx,ty),"true")
        exact_call(files,"UseAttackSkill",orig,extended)
    for name,method in [("ChangeShape","sharedWire"),("Attack","sharedWire"),
      ("AttackCritical","sharedWireMode1"),("AttackCritical","sharedWireMode2"),
      ("AttackMissPacket","sharedWire"),("AttackPacket","sharedWire"),
      ("AttackPacketForNpc","sharedWire"),("AttackStatus","sharedWire"),
      ("UseArrowSkill","sharedWire"),("UseAttackSkill","sharedWire")]:
        drop_private(files,name,method)
    for name,key in SC.items():
        s=files[key].decode("utf-8")
        assert FQ+".send(" not in s,(name,"legacy broadcast call")
        assert FQ+".sendExtended(" in s,name

def prepared_native(files):
    # Exactly the NC direct pooled slab -> Assemble -> shared SEND policy,
    # with an L1-only extra primitive signature for attack wire schemas.
    native="""    CIO_API void* L1Interop_CreateExtendedConstBuffer(
            void* p,int kind,int opcode,
            int p0,int p1,int p2,int p3,int p4,int p5,
            int p6,int p7,int p8,int p9,int p10) {
        if(!p)return nullptr;
        auto* server=static_cast<Server*>(p);
        ObjectRef guard(server);
"""
    for kind,(fmt,args,length) in FMT.items():
        compiled=[]
        for a in args:
            if a.endswith("u") or "?" in a:
                compiled.append(a)
            else:compiled.append("static_cast<unsigned>("+a+")")
        native+=f'        if(kind=={kind}){{\n'
        native+=f'            const std::uint64_t args[{len(args)}]={{'+",".join(compiled)+"};\n"
        native+=f'            return AssemblePooled(server,"{fmt}",args,{len(args)});\n'
        native+="        }\n"
    native+="        return nullptr;\n    }\n\n"
    c=files[CPP].decode("utf-8")
    marker="CIO_API const void* L1Interop_ConstBufferData"
    assert c.count(marker)==1
    files[CPP]=c.replace(marker,native+marker).encode("utf-8")

def extra_code(files):
    constants="\n"+"\n".join(
        f"    public static final int FORMAT_{name} = {kind};"
        for name,kind in CONST.items())
    update(files,SHARED,"    public static final int FORMAT_TRUETARGETNEW = 30;",
        "    public static final int FORMAT_TRUETARGETNEW = 30;"+constants)
    sender="""    /**
     * L1 combat variants need up to eleven integer fields, still assembled
     * directly into one Native pool slab without any Java wire array.
     */
    public static void sendExtended(
            L1Object anchor, int kind, int opcode,
            int p0,int p1,int p2,int p3,int p4,int p5,
            int p6,int p7,int p8,int p9,int p10,boolean fast) {
        if(anchor==null)return;
        final L1World world=L1World.getInstance();
        final List<L1PcInstance> recipients=fast
                ? world.borrowVisiblePlayerFast(anchor)
                : world.getVisiblePlayer(anchor);
        CAsyncSendConstBufferExceptSelf shared=null;
        try {
            for(L1PcInstance pc:recipients) {
                if(!CTLMFilter.sameWorldOther(anchor,pc))continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(shared==null) {
                    if(socket==null||!socket.isClientConnected())continue;
                    shared=CAsyncSendConstBufferExceptSelf.forExtended(
                            socket,anchor.getId(),kind,opcode,
                            p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10);
                    if(shared==null)throw new IllegalStateException(
                            "Native pooled combat source unavailable: "+kind);
                }
                shared.send(socket,pc.getId());
            }
        } finally {
            try {
                if(shared!=null)shared.close();
            } finally {
                if(fast)world.releaseVisiblePlayerFast(recipients);
            }
        }
    }

"""
    update(files,SHARED,
        "    public static final int FORMAT_TRUETARGETNEW = 30;"+constants,
        "    public static final int FORMAT_TRUETARGETNEW = 30;"+constants+"\n"+sender)
    extra="""    static CAsyncSendConstBufferExceptSelf forExtended(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int senderId,int kind,int opcode,
            int p0,int p1,int p2,int p3,int p4,int p5,
            int p6,int p7,int p8,int p9,int p10) {
        final CAsyncSendConstBuffer.Source pooled=
                socket.createPooledExtendedConstBuffer(
                        kind,opcode,p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10);
        if(pooled==null)return null;
        try {
            return new CAsyncSendConstBufferExceptSelf(pooled,senderId);
        } catch(RuntimeException|Error failure) {
            pooled.release();
            throw failure;
        }
    }

"""
    update(files,FUNCTOR,
        "    @Override protected boolean exclude(int recipientId) { return recipientId == senderId(); }",
        extra+"    @Override protected boolean exclude(int recipientId) { return recipientId == senderId(); }")
    delegate="""    public CAsyncSendConstBuffer.Source createPooledExtendedConstBuffer(
            int kind,int opcode,int p0,int p1,int p2,int p3,int p4,int p5,
            int p6,int p7,int p8,int p9,int p10) {
        return _channel.server().createPooledExtendedConstBuffer(
                kind,opcode,p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10);
    }
"""
    update(files,SOCKET,
        "    public boolean ownsConstBuffer(CAsyncSendConstBuffer.Source source) { return source.owner == _channel.server(); }",
        delegate+"    public boolean ownsConstBuffer(CAsyncSendConstBuffer.Source source) { return source.owner == _channel.server(); }")
    update(files,SERVER,
        "private final MethodHandle createFormattedConstBuffer,pooledConstData;",
        "private final MethodHandle createFormattedConstBuffer,createExtendedConstBuffer,pooledConstData;")
    # 13 Java ints (kind + opcode + 11 payload fields)
    descriptor="FunctionDescriptor.of(P,P,"+",".join(["I"]*13)+")"
    binding=(
        '            createExtendedConstBuffer=fn("L1Interop_CreateExtendedConstBuffer",\n'
       f"                    {descriptor});\n")
    update(files,SERVER,
        '            pooledConstData=fn("L1Interop_ConstBufferData",FunctionDescriptor.of(P,P));',
        binding+'            pooledConstData=fn("L1Interop_ConstBufferData",FunctionDescriptor.of(P,P));')
    lengths="\n".join(f"            case {k} -> {v[2]};" for k,v in FMT.items())
    maker="""    /** Typed L1 combat primitives -> same native pooled Assemble source. */
    @SuppressWarnings("preview")
    public CAsyncSendConstBuffer.Source createPooledExtendedConstBuffer(
            int kind,int opcode,int p0,int p1,int p2,int p3,int p4,int p5,
            int p6,int p7,int p8,int p9,int p10) {
        final int length=switch(kind) {
""" + lengths + """
            default -> throw new IllegalArgumentException("unknown combat native format: "+kind);
        };
        if(!enterNative())return null;
        MemorySegment handle=MemorySegment.NULL;
        try {
            handle=(MemorySegment)createExtendedConstBuffer.invokeExact(
                    server,kind,opcode,p0,p1,p2,p3,p4,p5,p6,p7,p8,p9,p10);
            if(handle.address()==0)return null;
            final CAsyncSendConstBuffer.Source source=pooledSource(handle,length);
            constBuffers.incrementAndGet();
            return source;
        } catch(Throwable failure) {
            if(handle.address()!=0) {
                try { releaseConstBuffer.invokeExact(handle); }
                catch(Throwable secondary) { failure.addSuppressed(secondary); }
            }
            throw new IllegalStateException("Native L1 combat Assemble "+kind+" failed",failure);
        } finally {
            nativeCalls.decrementAndGet();
        }
    }

"""
    update(files,SERVER,
        '    @SuppressWarnings("preview")\n    public void releaseConstBuffer(MemorySegment handle) {',
        maker+'    @SuppressWarnings("preview")\n    public void releaseConstBuffer(MemorySegment handle) {')
    update(files,CONFIG,"ABI = 0x000a0007","ABI = 0x000a0008")
    update(files,ABI,"0x000a0007","0x000a0008")
    prepared_native(files)

def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN,"phase75 ZIP SHA mismatch"
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos=z.infolist()
        files={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    old=files.copy()
    assert len(files)==1734 and sum(x.endswith(".java") for x in files)==1710
    extra_code(files)
    migrate_sc(files)
    touched={n for n in files if files[n]!=old[n]}
    assert touched==CHANGED-{"CTransport.dll"},("unexpected modified source", sorted(touched ^ (CHANGED-{"CTransport.dll"})))
    assert "sourceOverflowRecord" in files["native/cio_core.hpp"].decode("utf-8")
    for n,b in files.items():
        if n.startswith("native/") or n.startswith("src/") and n.endswith(".java"):
            dst=WORK/n
            dst.parent.mkdir(parents=True,exist_ok=True)
            dst.write_bytes(b)
    root=ROOT/"patches/ctransport_core_20261112/native"
    for src in root.iterdir():
        if src.is_file() and not (WORK/"native"/src.name).exists():shutil.copy2(src,WORK/"native"/src.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for p in ("build","logs"):(WORK/p).mkdir(exist_ok=True)
    print("PHASE76_PREPARED",len(SC),"SC combat classes",len(FMT),"Native primitive schemas",flush=True)

def package():
    dll=WORK/"build/CTransport.dll"
    dllsha=hashlib.sha256(dll.read_bytes()).hexdigest()
    assert dllsha in (WORK/CONFIG).read_text(encoding="utf-8")
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist()
        old={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    new=old.copy()
    for n in CHANGED-{"CTransport.dll"}: new[n]=(WORK/n).read_bytes()
    new["CTransport.dll"]=dll.read_bytes()
    touched={n for n in new if old[n]!=new[n]}
    assert touched==CHANGED,(sorted(touched),sorted(CHANGED))
    assert len(new)==1734 and sum(n.endswith(".java") for n in new)==1710
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:z.writestr(info,b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and len(z.namelist())==1734
    audit={"phase":76,"baseline_sha256":PIN,
           "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
           "dll_sha256":dllsha,"native_abi":"0x000a0008",
           "packet_classes":list(SC),
           "production_changed":sorted(touched),
           "java_count":1710,"zip_entries":1734,
           "no_new_classes_queues_workers":True,
           "phase72_source_exhaustion_preserved":True,
           "phase73_75_prior_codecs_preserved":True,
           "socket_cipher_fifo_preserved":True,
           "remaining_legacy_sc_classes":10,
           "5000_ingame_soak_verified":False}
    (WORK/"audit.json").write_text(json.dumps(audit,indent=2,ensure_ascii=False)+"\n")
    print("PHASE76_FULL_SRC_VERIFIED",json.dumps(audit),flush=True)

if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()

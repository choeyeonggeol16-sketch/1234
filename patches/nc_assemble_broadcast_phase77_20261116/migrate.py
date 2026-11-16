#!/usr/bin/env python3
"""77: Native pooled writable slab path plus nine variable/text SC broadcaster classes.
Source pinned to phase76, CTransport pooled source refcount/FIFO unchanged.
Only new bridge APIs and broadcast methods change; 1:1 packet SEND is untouched.
"""
from pathlib import Path
import hashlib,json,shutil,sys,zipfile,re
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase76_FULL_SRC_20261116.zip"
PIN="f58702f25775e1010afbf7069b94833679ffe722eef8e27815ade9f53ed15346"
OUT=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase77_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else None
P="src/l1j/server/server/model/network/core/"
SHARED=P+"broadcast/CSharedBroadcast.java"
BASEBUF=P+"broadcast/CAsyncSendConstBuffer.java"
FUNCTOR=P+"broadcast/CAsyncSendConstBufferExceptSelf.java"
SOCKET=P+"connection/CUserSocket.java"
SERVER=P+"transport/CIOServer.java"
CONFIG=P+"config/CIOConfig.java"
CPP="native/CTransport.cpp"
ABI="native/interop_abi.hpp"
SC={n:P+"sc/packet/SC_"+n+".java" for n in (
    "ChangeName","CharTitle","DoActionShop","ExplosionNoti",
    "NpcChatPacket","NpcShopAction","PacketBox","RangeSkill","ReturnedStat")}
CHANGED=set(SC.values())|{SHARED,BASEBUF,FUNCTOR,SOCKET,SERVER,CONFIG,CPP,ABI,"CTransport.dll"}
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"

def change(files,key,old,new):
    original=files[key].decode("utf-8")
    crlf="\r\n" in original
    s=original.replace("\r\n","\n")
    assert s.count(old)==1,(key,"replacement count",s.count(old),old[:200])
    s=s.replace(old,new,1)
    files[key]=(s.replace("\n","\r\n") if crlf else s).encode("utf-8")

def remove_private(files,key,func):
    o=files[key].decode("utf-8");crlf="\r\n" in o;s=o.replace("\r\n","\n")
    if s.count(func+"(")!=1:return False
    m=re.search(r"\bprivate static byte\[\]\s+"+re.escape(func)+r"\s*\(",s)
    if not m:return False
    a=s.rfind("\n",0,m.start())+1;b=s.index("{",m.start());depth=0;end=-1
    for i in range(b,len(s)):
        if s[i]=="{":depth+=1
        elif s[i]=="}":
            depth-=1
            if depth==0:end=i+1;break
    assert end>b,(key,func)
    s=s[:a]+s[end:].lstrip("\n")
    files[key]=(s.replace("\n","\r\n") if crlf else s).encode("utf-8")
    return True

def install_core(files):
    native=r"""
    // Java encodes directly into a privately borrowed 16KiB native source,
    // then seals before first const ref is handed to the encrypted socket FIFO.
    CIO_API void* L1Interop_CreateWritableConstBuffer(void* p) {
        if(!p)return nullptr;
        auto* server=static_cast<Server*>(p);
        ObjectRef guard(server);
        if(!server->running.load()||server->stopping.load())return nullptr;
        auto* block=server->buffers.acquireSharedSource();
        if(!block)return nullptr;
        block->used=LargeBufferBytes;
        block->constSourceOwner=server;
        server->retain();
        return block;
    }
    CIO_API int L1Interop_SealWritableConstBuffer(void* p,int length) {
        if(!p||length<3||length>static_cast<int>(LargeBufferBytes))return 0;
        auto* block=static_cast<CIOBuffer*>(p);
        if(!block->constSourceOwner)return 0;
        block->data.data()[0]=static_cast<unsigned char>(length);
        block->data.data()[1]=static_cast<unsigned char>(length>>8);
        block->used=static_cast<unsigned>(length);
        return 1;
    }

"""
    old="CIO_API const void* L1Interop_ConstBufferData"
    change(files,CPP,old,native+old)
    change(files,CONFIG,"ABI = 0x000a0008","ABI = 0x000a0009")
    change(files,ABI,"0x000a0008","0x000a0009")
    change(files,SERVER,
        "private final MethodHandle createFormattedConstBuffer,createExtendedConstBuffer,pooledConstData;",
        "private final MethodHandle createFormattedConstBuffer,createExtendedConstBuffer,pooledConstData;\n"
        "    private final MethodHandle createWritableConstBuffer,sealWritableConstBuffer;")
    marker='            pooledConstData=fn("L1Interop_ConstBufferData",FunctionDescriptor.of(P,P));'
    binding=(
       '            createWritableConstBuffer=fn("L1Interop_CreateWritableConstBuffer",FunctionDescriptor.of(P,P));\n'
       '            sealWritableConstBuffer=fn("L1Interop_SealWritableConstBuffer",FunctionDescriptor.of(I,P,I));\n')
    change(files,SERVER,marker,binding+marker)
    newmethods="""    /** Borrow one Native pooled slab exclusively for synchronous Java encoding. */
    @SuppressWarnings("preview")
    public CAsyncSendConstBuffer.Source createPooledWritableConstBuffer() {
        if(!enterNative())return null;
        MemorySegment handle=MemorySegment.NULL;
        try {
            handle=(MemorySegment)createWritableConstBuffer.invokeExact(server);
            if(handle.address()==0)return null;
            final MemorySegment data=(MemorySegment)pooledConstData.invokeExact(handle);
            if(data.address()==0)throw new IllegalStateException("Writable pooled source has null data");
            final ByteBuffer writable=data.reinterpret(16384).asByteBuffer()
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            final CAsyncSendConstBuffer.Source source=
                    new CAsyncSendConstBuffer.Source(this,handle,writable,true);
            constBuffers.incrementAndGet();
            return source;
        } catch(Throwable failure) {
            if(handle.address()!=0) {
                try { releaseConstBuffer.invokeExact(handle); }
                catch(Throwable secondary) { failure.addSuppressed(secondary); }
            }
            throw new IllegalStateException("Native writable source acquire failed",failure);
        } finally { nativeCalls.decrementAndGet(); }
    }

    /** The unshared producer lease remains the only owner until seal completes. */
    @SuppressWarnings("preview")
    public void sealPooledWritableConstBuffer(CAsyncSendConstBuffer.Source source,int length) {
        if(source==null||source.owner!=this||source.mutableWire==null
                ||length<3||length>16384)
            throw new IllegalArgumentException("invalid writable Native source/length");
        nativeCalls.incrementAndGet();
        try {
            final int ok=(int)sealWritableConstBuffer.invokeExact(source.handle,length);
            if(ok!=1)throw new IllegalStateException("Native slab seal refused");
            source.wire.limit(length);
            source.mutableWire=null;
        } catch(Throwable failure) {
            throw new IllegalStateException("Native pooled source seal failed",failure);
        } finally { nativeCalls.decrementAndGet(); }
    }

"""
    marker='    @SuppressWarnings("preview")\n    public void releaseConstBuffer(MemorySegment handle) {'
    change(files,SERVER,marker,newmethods+marker)
    delegate="""    public CAsyncSendConstBuffer.Source createPooledWritableConstBuffer() {
        return _channel.server().createPooledWritableConstBuffer();
    }
    public void sealPooledWritableConstBuffer(CAsyncSendConstBuffer.Source source,int length) {
        _channel.server().sealPooledWritableConstBuffer(source,length);
    }
"""
    marker="    public boolean ownsConstBuffer(CAsyncSendConstBuffer.Source source) { return source.owner == _channel.server(); }"
    change(files,SOCKET,marker,delegate+marker)
    marker="        public final ByteBuffer wire;"
    change(files,BASEBUF,marker,marker+"\n        /** Never published before sealing; null after seal. */\n        public ByteBuffer mutableWire;")
    old="""        public Source(CIOServer owner, MemorySegment handle, ByteBuffer nativeBytes) {
            this.owner = owner;
            this.handle = handle;
            wire = nativeBytes.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        }"""
    new="""        public Source(CIOServer owner, MemorySegment handle, ByteBuffer nativeBytes) {
            this(owner,handle,nativeBytes,false);
        }
        public Source(CIOServer owner, MemorySegment handle, ByteBuffer nativeBytes,
                      boolean writableSource) {
            this.owner = owner;
            this.handle = handle;
            wire = nativeBytes.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
            mutableWire = writableSource ? nativeBytes : null;
        }"""
    change(files,BASEBUF,old,new)
    statichelper="""    /**
     * Build final L1 bytes IN the borrowed native slab, no byte[] and no
     * second native copy. The first reference is still owned by producer.
     */
    static Source openWrittenSource(CUserSocket target,CSharedBroadcast.NativeWireWriter writer) {
        if(target==null||writer==null)return null;
        final Source source=target.createPooledWritableConstBuffer();
        if(source==null)return null;
        try {
            final ByteBuffer out=source.mutableWire;
            if(out==null)throw new IllegalStateException("Native writable source lost");
            out.position(2);
            writer.write(out);
            final int length=out.position();
            target.sealPooledWritableConstBuffer(source,length);
            return source;
        } catch(RuntimeException|Error failure) {
            source.release();
            throw failure;
        }
    }

"""
    marker="    @Override public final synchronized void close() {"
    change(files,BASEBUF,marker,statichelper+marker)
    staticfactory="""    static CAsyncSendConstBufferExceptSelf forWritten(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int senderId,CSharedBroadcast.NativeWireWriter writer) {
        final CAsyncSendConstBuffer.Source source=
                CAsyncSendConstBuffer.openWrittenSource(socket,writer);
        if(source==null)return null;
        try {
            return new CAsyncSendConstBufferExceptSelf(source,senderId);
        } catch(RuntimeException|Error failure) {
            source.release();
            throw failure;
        }
    }

"""
    marker="    @Override protected boolean exclude(int recipientId) { return recipientId == senderId(); }"
    change(files,FUNCTOR,marker,staticfactory+marker)

    api="""    /**
     * Encodes once directly into an NC-style Native pooled CIOBufferEx.
     * The encoder is invoked only after a connected viewer is eligible.
     * A failed source acquire/encode/commit never silently drops a broadcast.
     */
    @FunctionalInterface
    public interface NativeWireWriter {
        void write(java.nio.ByteBuffer output);
    }

    private static final java.nio.charset.Charset MS949 =
            java.nio.charset.Charset.forName("MS949");

    /** Exact MS949 replacement behavior of legacy String.getBytes(MS949). */
    public static void writeMS949(java.nio.ByteBuffer out,String text) {
        final var encoder=MS949.newEncoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE);
        var result=encoder.encode(java.nio.CharBuffer.wrap(text==null?"":text),out,true);
        if(result.isOverflow())throw new java.nio.BufferOverflowException();
        if(result.isError())throw new IllegalArgumentException("MS949 encode error");
        result=encoder.flush(out);
        if(result.isOverflow())throw new java.nio.BufferOverflowException();
        if(result.isError())throw new IllegalArgumentException("MS949 flush error");
    }

    public static void sendWritten(L1Object anchor,NativeWireWriter writer,boolean fast,
            int radius,Predicate<L1PcInstance> filter) {
        if(anchor==null||writer==null)return;
        final L1World world=L1World.getInstance();
        final boolean borrowed=fast&&radius<0;
        final List<L1PcInstance> recipients=borrowed
                ? world.borrowVisiblePlayerFast(anchor)
                : radius<0?world.getVisiblePlayer(anchor)
                          :world.getVisiblePlayer(anchor,radius);
        CAsyncSendConstBufferExceptSelf shared=null;
        try {
            for(L1PcInstance pc:recipients) {
                if(!CTLMFilter.sameWorldOther(anchor,pc)
                        ||filter!=null&&!filter.test(pc))continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(shared==null) {
                    if(socket==null||!socket.isClientConnected())continue;
                    shared=CAsyncSendConstBufferExceptSelf.forWritten(
                            socket,anchor.getId(),writer);
                    if(shared==null)throw new IllegalStateException("Native SC writable source unavailable");
                }
                shared.send(socket,pc.getId());
            }
        } finally {
            try {
                if(shared!=null)shared.close();
            } finally {
                if(borrowed)world.releaseVisiblePlayerFast(recipients);
            }
        }
    }

    /** Literal recipient order, including duplicates and self: no AOI override. */
    public static void sendWrittenRecipients(
            Iterable<? extends L1PcInstance> recipients,NativeWireWriter writer) {
        if(recipients==null||writer==null)return;
        CAsyncSendConstBuffer shared=null;
        try {
            for(L1PcInstance pc:recipients) {
                if(pc==null)continue;
                final CIOClient client=pc.getNetConnection();
                if(client==null||!client.isConnected())continue;
                final var socket=client.getConnection();
                if(socket==null||!socket.isClientConnected())continue;
                if(shared==null) {
                    final CAsyncSendConstBuffer.Source source=
                            CAsyncSendConstBuffer.openWrittenSource(socket,writer);
                    if(source==null)throw new IllegalStateException("Native recipient source unavailable");
                    try {
                        shared=new CAsyncSendConstBuffer(source,Integer.MIN_VALUE,Integer.MAX_VALUE);
                    } catch(RuntimeException|Error failure) {
                        source.release();
                        throw failure;
                    }
                }
                shared.send(socket,pc.getId());
            }
        } finally {
            if(shared!=null)shared.close();
        }
    }

    public static void sendWrittenAll(NativeWireWriter writer) {
        sendWrittenRecipients(L1World.getInstance().getAllPlayers(),writer);
    }

"""
    marker="    public static void send(L1Object anchor, byte[] wire, boolean fast, int radius,"
    change(files,SHARED,marker,api+marker)

def wire_writer(body):
    return "b -> { "+body+" }"

def replace_send(files,name,old,new):
    change(files,SC[name],old,new)

def migrate_sc(files):
    name="ChangeName"
    for fast in ("false","true"):
        old=(f"        final byte[] wire=sharedWire(objectId, name);\n"
             f"        if(wire==null)return;\n"
             f"        {FQ}.send(anchor, wire, {fast}, -1, null);")
        body="b.put((byte)Opcodes.S_CHANGE_DESC).putInt(objectId); "+FQ+".writeMS949(b,name); b.put((byte)0);"
        replace_send(files,name,old,f"        {FQ}.sendWritten(anchor, {wire_writer(body)}, {fast}, -1, null);")
    remove_private(files,SC[name],"sharedWire")
    name="CharTitle"
    old=(f"        final byte[] wire=sharedWire(objectId, title);\n"
         f"        if(wire==null)return;\n"
         f"        {FQ}.send(anchor, wire, false, -1, null);")
    body="b.put((byte)Opcodes.S_TITLE).putInt(objectId); "+FQ+".writeMS949(b,title); b.put((byte)0);"
    replace_send(files,name,old,f"        {FQ}.sendWritten(anchor, {wire_writer(body)}, false, -1, null);")
    remove_private(files,SC[name],"sharedWire")
    name="DoActionShop"
    old=(f"        final byte[] wire=sharedWire(id,action,message);\n"
         f"        if(wire==null)return;\n"
         f"        {FQ}.send(anchor, wire, false, -1, null);")
    o=files[SC[name]].decode("utf-8");crlf="\r\n" in o;s=o.replace("\r\n","\n")
    assert s.count(old)==2,(name,s.count(old))
    first=f"        {FQ}.sendWritten(anchor, {wire_writer('b.put((byte)Opcodes.S_ACTION).putInt(id).put((byte)action); if(message!=null)b.put(message);')}, false, -1, null);"
    second=f"        {FQ}.sendWritten(anchor, {wire_writer('b.put((byte)Opcodes.S_ACTION).putInt(id).put((byte)action); '+FQ+'.writeMS949(b,message); b.put((byte)0);')}, false, -1, null);"
    s=s.replace(old,first,1).replace(old,second,1)
    files[SC[name]]=(s.replace("\n","\r\n") if crlf else s).encode("utf-8")
    # Both private overloads are exclusively used for broadcast and are now dead.
    for typ in ("byte[]","String"):
        key=SC[name];o=files[key].decode("utf-8");crlf="\r\n" in o;s=o.replace("\r\n","\n")
        needle=f"private static byte[] sharedWire(int id,int action,{typ} message)"
        assert s.count(needle)==1,(name,needle)
        start=s.index(needle);a=s.rfind("\n",0,start)+1;b=s.index("{",start)
        d=0;end=-1
        for i in range(b,len(s)):
            if s[i]=="{":d+=1
            elif s[i]=="}":
                d-=1
                if d==0:end=i+1;break
        assert end>b
        s=s[:a]+s[end:].lstrip("\n")
        files[key]=(s.replace("\n","\r\n") if crlf else s).encode("utf-8")
    name="ExplosionNoti"
    old=f"{FQ}.send(anchor, sharedWire(objectId,mills), false, -1, null);"
    body=("b.put((byte)Opcodes.S_EXTENDED_PROTOBUF).putShort((short)0x0332); "
          "b.put((byte)0x08); writeVar(b,objectId); b.put((byte)0x10); "
          "writeVar(b,mills); b.putShort((short)0);")
    replace_send(files,name,old,f"{FQ}.sendWritten(anchor, {wire_writer(body)}, false, -1, null);")
    remove_private(files,SC[name],"sharedWire")
    name="NpcShopAction"
    old=(f"        final byte[] wire=sharedWire(objectId, action, shopName);\n"
         f"        if(wire==null)return;\n"
         f"        {FQ}.send(anchor, wire, false, -1, null);")
    body=("b.put((byte)Opcodes.S_ACTION).putInt(objectId).put((byte)action); "
          "final String text=shopName==null?\"\":shopName; "
          "for(int i=0;i<text.length();i++)b.putChar(text.charAt(i)); b.putShort((short)0);")
    replace_send(files,name,old,f"        {FQ}.sendWritten(anchor, {wire_writer(body)}, false, -1, null);")
    remove_private(files,SC[name],"sharedWire")
    name="RangeSkill"
    old=(f"        final byte[] wire=sharedWire(id,x,y,frozenHeading,seq,gfx,action,type,ids,frozenCount);\n"
         f"        if(wire!=null) {{\n"
         f"            {FQ}.send(caster, wire, true, -1, null);\n"
         "        }")
    body=("b.put((byte)Opcodes.S_ATTACK_MANY).put((byte)action).putInt(id)"
          ".putShort((short)x).putShort((short)y).put((byte)frozenHeading)"
          ".putInt(seq).putShort((short)gfx).put((byte)type)"
          ".putShort((short)0).putShort((short)frozenCount); "
          "for(int i=0;i<frozenCount;i++)b.putInt(ids[i]).put((byte)32).put((byte)action);")
    new=(f"        if(frozenCount>=0 && 24L+6L*frozenCount<=0xFFFFL) {{\n"
         f"            {FQ}.sendWritten(caster, {wire_writer(body)}, true, -1, null);\n"
         "        }")
    replace_send(files,name,old,new)
    remove_private(files,SC[name],"sharedWire")
    name="ReturnedStat"
    old=(f"        {FQ}.sendRecipients(\n"
         "                L1World.getInstance().getVisiblePlayer(source),\n"
         "                emblemWire(objId, emblemId));")
    body="b.put((byte)Opcodes.S_VOICE_CHAT).put((byte)0x3c).putInt(objId).putInt(emblemId);"
    new=(f"        {FQ}.sendWrittenRecipients(\n"
         f"                L1World.getInstance().getVisiblePlayer(source), {wire_writer(body)});")
    replace_send(files,name,old,new)
    remove_private(files,SC[name],"emblemWire")
    name="NpcChatPacket"
    old=(f"        {FQ}.sendRecipients(\n"
         "                L1World.getInstance().getVisiblePlayer(source),\n"
         "                sharedWire(cha.getId(),0,message));")
    body=("b.put((byte)Opcodes.S_SAY_CODE).put((byte)0).putInt(cha.getId()); "
          f"{FQ}.writeMS949(b,message); b.put((byte)0);")
    replace_send(files,name,old,f"        {FQ}.sendWrittenRecipients(\n                L1World.getInstance().getVisiblePlayer(source), {wire_writer(body)});")
    old=f"            {FQ}.sendRecipients(visible,wire);"
    body=(f"b.put((byte)Opcodes.S_SAY_CODE).put((byte)color).putInt(npc.getId()); "
          f"{FQ}.writeMS949(b,formatted); b.put((byte)0);")
    new=f"            {FQ}.sendWrittenRecipients(visible, {wire_writer(body)});"
    replace_send(files,name,old,new)
    # Former wire build is no longer needed for this borrow path.
    replace_send(files,name,"        final byte[] wire=sharedWire(npc.getId(),color,formatted);\n","")
    for visible in ("getVisiblePlayer(source)","getVisiblePlayer(source,50)"):
        old=(f"        {FQ}.sendRecipients(\n"
             f"                L1World.getInstance().{visible},\n"
             "                sharedWire(npc.getId(),color,formatted));")
        new=(f"        {FQ}.sendWrittenRecipients(\n"
             f"                L1World.getInstance().{visible}, {wire_writer(body)});")
        replace_send(files,name,old,new)
    # Public sharedNpcWire()/sharedWire remain API-compatible for non-AOI uses.
    name="PacketBox"
    old=f"        {FQ}.sendAll(sharedGreenWire(message));"
    body=(f"b.put((byte)Opcodes.S_EVENT).put((byte)GREEN_MESSAGE).put((byte)2); "
          f"{FQ}.writeMS949(b,message); b.put((byte)0);")
    replace_send(files,name,old,f"        {FQ}.sendWrittenAll({wire_writer(body)});")
    old=(f"        {FQ}.sendRecipients(\n"
         "                L1World.getInstance().getVisiblePlayers(mapId),\n"
         "                sharedGreenWire(message));")
    replace_send(files,name,old,f"        {FQ}.sendWrittenRecipients(\n                L1World.getInstance().getVisiblePlayers(mapId), {wire_writer(body)});")
    for name,key in SC.items():
        s=files[key].decode("utf-8")
        assert FQ+".send(" not in s and FQ+".sendRecipients(" not in s and FQ+".sendAll(" not in s,(name,"legacy")
        assert FQ+".sendWritten" in s,name

def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN,"phase76 input SHA mismatch"
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos=z.infolist()
        files={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    old=files.copy()
    assert len(files)==1734 and sum(n.endswith(".java") for n in files)==1710
    install_core(files)
    migrate_sc(files)
    touched={n for n in files if files[n]!=old[n]}
    assert touched==CHANGED-{"CTransport.dll"},sorted(touched^(CHANGED-{"CTransport.dll"}))
    assert "sourceOverflowRecord" in files["native/cio_core.hpp"].decode("utf-8")
    for n,b in files.items():
        if n.startswith("native/") or (n.startswith("src/") and n.endswith(".java")):
            dst=WORK/n;dst.parent.mkdir(parents=True,exist_ok=True);dst.write_bytes(b)
    native_root=ROOT/"patches/ctransport_core_20261112/native"
    for s in native_root.iterdir():
        if s.is_file() and not (WORK/"native"/s.name).exists():shutil.copy2(s,WORK/"native"/s.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for n in ("build","logs"):(WORK/n).mkdir(exist_ok=True)
    print("PHASE77_PREPARED",len(SC),"variable/text SC classes, Native mutable pooled source",flush=True)

def package():
    dll=WORK/"build/CTransport.dll"
    dllsha=hashlib.sha256(dll.read_bytes()).hexdigest()
    assert dllsha in (WORK/CONFIG).read_text(encoding="utf-8")
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist()
        old={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    new=old.copy()
    for n in CHANGED-{"CTransport.dll"}:new[n]=(WORK/n).read_bytes()
    new["CTransport.dll"]=dll.read_bytes()
    touched={n for n in new if new[n]!=old[n]}
    assert touched==CHANGED,(sorted(touched),sorted(CHANGED))
    assert len(new)==1734 and sum(n.endswith(".java") for n in new)==1710
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:z.writestr(info,b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and len(z.namelist())==1734
    audit={"phase":77,"baseline_sha256":PIN,
        "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "dll_sha256":dllsha,"native_abi":"0x000a0009",
        "packet_classes":list(SC),"production_changed":sorted(touched),
        "java_count":1710,"zip_entries":1734,
        "native_pool_source_exhaustion_preserved":True,
        "prior_AOI_cipher_FIFO_preserved":True,"no_new_workers_queues":True,
        "remaining_legacy_sc_classes":1,
        "native_mutable_source_max_bytes":16384,
        "game_5000_soak_verified":False}
    (WORK/"audit.json").write_text(json.dumps(audit,indent=2,ensure_ascii=False)+"\n")
    print("PHASE77_FULL_SRC_VERIFIED",json.dumps(audit),flush=True)

if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()

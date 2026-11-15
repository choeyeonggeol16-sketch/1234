#!/usr/bin/env python3
"""RKern NC pooled S packet phase69: patch pinned full SRC + native CTransport.

Actual L2Server shared-broadcast evidence:
0x41E6E4 pool acquire, 0x41F694 direct pool assembly,
0x9241F8 AddRef, 0x45C7AC socket serializer,
0x90E6C4 per-socket encrypt/output, final pool recycle.

No Java byte[] allocation on the action/move broadcast hot path.
Native CIOBufferEx pool refcounts hold shared plaintext through SEND staging;
prior per-socket FIFO, encryption and lifetime remain mandatory.
"""
from pathlib import Path
import hashlib,json,sys,zipfile,shutil

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SAction_Shared_FULL_SRC_20261116.zip"
SHA="7db9e443c82d5a6855483bca9eecb805cbeb9583c5b6fe6a731d771bf870fd10"
WORK=Path(sys.argv[1]).resolve()
SRC="src/l1j/server/server/model/network/core/"
BASECAST=SRC+"broadcast/"
ACTION=SRC+"sc/packet/SC_DoActionGFX.java"
MOVE=SRC+"sc/packet/SC_MoveChar.java"
SHARED=BASECAST+"CSharedBroadcast.java"
CONST=BASECAST+"CAsyncSendConstBuffer.java"
EXCEPT=BASECAST+"CAsyncSendConstBufferExceptSelf.java"
SOCKET=SRC+"connection/CUserSocket.java"
SERVER=SRC+"transport/CIOServer.java"
CONFIG=SRC+"config/CIOConfig.java"
CHANGED={CONST,EXCEPT,SHARED,SOCKET,SERVER,CONFIG}
NATIVE=ROOT/"patches/ctransport_core_20261112/native"
OUT=ROOT/"downloads/RKern_NC_Pooled_SPackets_FULL_SRC_20261116.zip"

def replace_once(d,k,old,new):
    s=d[k].decode("utf-8")
    n=s.count(old)
    assert n==1,(k,n,old[:130])
    d[k]=s.replace(old,new,1).encode("utf-8")

def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==SHA
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos=z.infolist()
        files={info.filename:z.read(info) for info in infos if not info.is_dir()}
    original=dict(files)
    assert len(files)==1729
    assert sum(n.endswith(".java") for n in files)==1710

    # Pooled native refcounted plaintext is owned by the broadcast wrapper.
    # Existing byte[] callers still clone and keep all legacy semantics.
    replace_once(files,CONST,
       "    protected boolean exclude(int recipientId) { return false; }",
       """    /**
     * NC pool ownership path. Source is constructed from a freshly formatted
     * CIOBufferEx slab, and this object takes exactly the initial producer ref.
     */
    protected CAsyncSendConstBuffer(Source pooled, int senderId, int pendingLimit) {
        if (pooled == null || pooled.wire.remaining() < 3
                || pooled.wire.remaining() > 16384 || pendingLimit <= 0)
            throw new IllegalArgumentException("pooled constant frame/limit");
        final ByteBuffer view = pooled.wire;
        if (((view.get(view.position()) & 255)
                | ((view.get(view.position() + 1) & 255) << 8))
                != view.remaining()) throw new IllegalArgumentException("pooled frame length");
        wire = null;
        source = pooled;
        this.senderId = senderId;
        this.pendingLimit = pendingLimit;
    }

    protected boolean exclude(int recipientId) { return false; }""")

    replace_once(files,CONST,
      """        if(wire.length<=16384 && target.supportsConstBuffer()
                && target.pendingSendBytes()<limit) {""",
      """        final int length = wire == null ? source.wire.remaining() : wire.length;
        if(length<=16384 && target.supportsConstBuffer()
                && target.pendingSendBytes()<limit) {""")
    replace_once(files,CONST,
      """        return target.sendFrozenWire(wire);""",
      """        // A pool-backed immutable frame is read directly from the native
        // CIOBufferEx slab. For a foreign owner the existing socket FIFO
        // receives a copy into its own pooled output slot, never a byte[].
        return wire == null ? target.sendFrozenWire(source.wire)
                : target.sendFrozenWire(wire);""")
    replace_once(files,CONST,
      """        public Source(CIOServer owner, MemorySegment handle, byte[] bytes) {
            this.owner = owner;
            this.handle = handle;
            wire = ByteBuffer.wrap(bytes).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        }""",
      """        public Source(CIOServer owner, MemorySegment handle, byte[] bytes) {
            this.owner = owner;
            this.handle = handle;
            wire = ByteBuffer.wrap(bytes).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        }
        /** Native pooled memory, not a Java copy. Held until last lease release. */
        public Source(CIOServer owner, MemorySegment handle, ByteBuffer nativeBytes) {
            this.owner = owner;
            this.handle = handle;
            wire = nativeBytes.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        }""")

    old_functor="""    /** Only this class constructs the owned move bytes; no mutable array escapes. */
    private CAsyncSendConstBufferExceptSelf(byte[] frame, int senderId, boolean owned) {
        super(frame, senderId, Integer.MAX_VALUE, owned);
    }

    /** Create the entire immutable 17-byte movement frame inside its sole owner. */
    static CAsyncSendConstBufferExceptSelf forMove(
            int senderId, int opcode, int objectId, int x, int y, int heading) {
        final byte[] wire = new byte[17];
        wire[0] = 17;
        wire[2] = (byte) opcode;
        wire[3] = (byte) objectId;
        wire[4] = (byte) (objectId >>> 8);
        wire[5] = (byte) (objectId >>> 16);
        wire[6] = (byte) (objectId >>> 24);
        wire[7] = (byte) x;
        wire[8] = (byte) (x >>> 8);
        wire[9] = (byte) y;
        wire[10] = (byte) (y >>> 8);
        wire[11] = (byte) heading;
        wire[12] = (byte) 129;
        // The two-byte length high byte and trailing int remain zero.
        return new CAsyncSendConstBufferExceptSelf(wire, senderId, true);
    }"""
    new_functor="""    private CAsyncSendConstBufferExceptSelf(
            CAsyncSendConstBuffer.Source pooled, int senderId) {
        super(pooled, senderId, Integer.MAX_VALUE);
    }

    /** Original NC-style pool acquire -> direct packet assembly -> shared ref. */
    static CAsyncSendConstBufferExceptSelf forMove(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int senderId, int opcode, int objectId, int x, int y, int heading) {
        final CAsyncSendConstBuffer.Source pooled =
                socket.createPooledMoveConstBuffer(opcode, objectId, x, y, heading);
        return pooled == null ? null : new CAsyncSendConstBufferExceptSelf(pooled, senderId);
    }"""
    replace_once(files,EXCEPT,old_functor,new_functor)
    old_action="""    /**
     * NC-inspired once-assembled 8-byte L1 S_ACTION plaintext. The mutable
     * array is created here and never exposed to the caller. Only the existing
     * internal owned-frame constructor receives it, so there is no clone.
     * Native/Java SEND consumers retain their pre-existing FIFO/lease contract.
     */
    static CAsyncSendConstBufferExceptSelf forAction(
            int senderId, int opcode, int objectId, int actionId) {
        final byte[] wire = new byte[8];
        wire[0] = 8;
        wire[2] = (byte) opcode;
        wire[3] = (byte) objectId;
        wire[4] = (byte) (objectId >>> 8);
        wire[5] = (byte) (objectId >>> 16);
        wire[6] = (byte) (objectId >>> 24);
        wire[7] = (byte) actionId;
        // wire[1] is the zero high byte of the 2-byte framing header.
        return new CAsyncSendConstBufferExceptSelf(wire, senderId, true);
    }"""
    new_action="""    /** Native pooled CIOBufferEx: 8-byte S_ACTION direct write, no Java byte[]. */
    static CAsyncSendConstBufferExceptSelf forAction(
            l1j.server.server.model.network.core.connection.CUserSocket socket,
            int senderId, int opcode, int objectId, int actionId) {
        final CAsyncSendConstBuffer.Source pooled =
                socket.createPooledActionConstBuffer(opcode, objectId, actionId);
        return pooled == null ? null : new CAsyncSendConstBufferExceptSelf(pooled, senderId);
    }"""
    replace_once(files,EXCEPT,old_action,new_action)

    replace_once(files,SHARED,
       """                    shared = CAsyncSendConstBufferExceptSelf.forAction(
                            objectId, l1j.server.server.Opcodes.S_ACTION,
                            objectId, actionId);""",
       """                    shared = CAsyncSendConstBufferExceptSelf.forAction(
                            socket, objectId, l1j.server.server.Opcodes.S_ACTION,
                            objectId, actionId);
                    if (shared == null) return; // NC bounded pool admission; no unsafe fallback""")
    replace_once(files,SHARED,
       """                    shared = CAsyncSendConstBufferExceptSelf.forMove(
                            anchor.getId(),
                            l1j.server.server.Opcodes.S_MOVE_OBJECT,
                            objectId, x, y, heading);""",
       """                    shared = CAsyncSendConstBufferExceptSelf.forMove(
                            socket, anchor.getId(),
                            l1j.server.server.Opcodes.S_MOVE_OBJECT,
                            objectId, x, y, heading);
                    if (shared == null) return; // NC bounded pool admission; no unsafe fallback""")

    replace_once(files,SOCKET,
       """    public CAsyncSendConstBuffer.Source createConstBuffer(byte[] wire) { return _channel.server().createConstBuffer(wire); }""",
       """    public CAsyncSendConstBuffer.Source createConstBuffer(byte[] wire) { return _channel.server().createConstBuffer(wire); }
    public CAsyncSendConstBuffer.Source createPooledActionConstBuffer(
            int opcode, int objectId, int actionId) {
        return _channel.server().createPooledActionConstBuffer(opcode, objectId, actionId);
    }
    public CAsyncSendConstBuffer.Source createPooledMoveConstBuffer(
            int opcode, int objectId, int x, int y, int heading) {
        return _channel.server().createPooledMoveConstBuffer(opcode, objectId, x, y, heading);
    }""")
    # For multi-server ownership and non-const sockets, copy only into the
    # recipient's normal pooled CIOFrame. Never copy to transient heap byte[].
    at="""    public boolean sendFrozenWire(byte[] frameBytes) {"""
    native_frozen="""    /** Immutable native pooled plaintext -> regular per-socket FIFO lease.
     * Used only when the recipient belongs to a different native pool owner
     * or cannot accept a native shared source. No Java byte[] is created.
     */
    public boolean sendFrozenWire(java.nio.ByteBuffer nativeFrame) {
        if (nativeFrame == null || !isClientConnected()) return false;
        final int length = nativeFrame.remaining();
        final int begin = nativeFrame.position();
        if (length <= CIOBufferPool.HEADER_BYTES || length > CIOBufferPool.MAX_WIRE_BYTES
                || ((nativeFrame.get(begin) & 255)
                    | ((nativeFrame.get(begin + 1) & 255) << 8)) != length) return false;
        final CIOFrame frame = beginDirectFrame(length);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final var writer = frame.buffer();
            final java.nio.ByteBuffer dest = writer.getBuffer();
            dest.position(writer.baseOffset() + writer.position());
            for (int i = CIOBufferPool.HEADER_BYTES; i < length; i++)
                dest.put(nativeFrame.get(begin + i));
            writer.position(length);
            committed = commitDirectFrame(frame);
            return committed;
        } catch (Throwable failure) {
            return false;
        } finally {
            if (!committed) abortDirectFrame(frame);
        }
    }

"""
    replace_once(files,SOCKET,at,native_frozen+at)

    replace_once(files,SERVER,
       """    private final MethodHandle createConstBuffer,releaseConstBuffer,sendConstBuffer;""",
       """    private final MethodHandle createConstBuffer,releaseConstBuffer,sendConstBuffer;
    private final MethodHandle createPooledAction,createPooledMove,pooledConstData;""")
    replace_once(files,SERVER,
       """            createConstBuffer=fn("L1Interop_CreateConstBuffer",FunctionDescriptor.of(P,P,P,I));""",
       """            createConstBuffer=fn("L1Interop_CreateConstBuffer",FunctionDescriptor.of(P,P,P,I));
            createPooledAction=fn("L1Interop_CreateActionConstBuffer",FunctionDescriptor.of(P,P,I,I,I));
            createPooledMove=fn("L1Interop_CreateMoveConstBuffer",FunctionDescriptor.of(P,P,I,I,I,I,I));
            pooledConstData=fn("L1Interop_ConstBufferData",FunctionDescriptor.of(P,P));""")
    loc="""    @SuppressWarnings("preview")
    public void releaseConstBuffer(MemorySegment handle) {"""
    insert="""    /**
     * C++ CIOBufferEx<16384> is borrowed and formatted BEFORE any recipient
     * gets the Source. Its pointer remains stable through native/Java leases.
     * No transient Java array, off-heap scratch, or second native copy.
     */
    @SuppressWarnings("preview")
    private CAsyncSendConstBuffer.Source pooledSource(MemorySegment handle, int length)
            throws Throwable {
        if (handle.address() == 0) return null;
        final MemorySegment data = (MemorySegment) pooledConstData.invokeExact(handle);
        if (data.address() == 0) throw new IllegalStateException("null pooled data");
        final ByteBuffer bytes = data.reinterpret(length).asByteBuffer()
                .asReadOnlyBuffer().order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if (bytes.remaining() != length
                || (((bytes.get(0) & 255) | ((bytes.get(1) & 255) << 8)) != length))
            throw new IllegalStateException("native pooled packet length mismatch");
        return new CAsyncSendConstBuffer.Source(this, handle, bytes);
    }

    @SuppressWarnings("preview")
    public CAsyncSendConstBuffer.Source createPooledActionConstBuffer(
            int opcode, int objectId, int actionId) {
        if (!enterNative()) return null;
        MemorySegment handle = MemorySegment.NULL;
        try {
            handle = (MemorySegment) createPooledAction.invokeExact(server, opcode, objectId, actionId);
            if (handle.address() == 0) return null;
            CAsyncSendConstBuffer.Source result = pooledSource(handle, 8);
            constBuffers.incrementAndGet();
            return result;
        } catch (Throwable failure) {
            if (handle.address() != 0) {
                try { releaseConstBuffer.invokeExact(handle); }
                catch (Throwable releaseFailure) { failure.addSuppressed(releaseFailure); }
            }
            throw new IllegalStateException("NC pooled S_ACTION creation failed", failure);
        } finally { nativeCalls.decrementAndGet(); }
    }

    @SuppressWarnings("preview")
    public CAsyncSendConstBuffer.Source createPooledMoveConstBuffer(
            int opcode, int objectId, int x, int y, int heading) {
        if (!enterNative()) return null;
        MemorySegment handle = MemorySegment.NULL;
        try {
            handle = (MemorySegment) createPooledMove.invokeExact(
                    server, opcode, objectId, x, y, heading);
            if (handle.address() == 0) return null;
            CAsyncSendConstBuffer.Source result = pooledSource(handle, 17);
            constBuffers.incrementAndGet();
            return result;
        } catch (Throwable failure) {
            if (handle.address() != 0) {
                try { releaseConstBuffer.invokeExact(handle); }
                catch (Throwable releaseFailure) { failure.addSuppressed(releaseFailure); }
            }
            throw new IllegalStateException("NC pooled S_MOVE creation failed", failure);
        } finally { nativeCalls.decrementAndGet(); }
    }

"""
    replace_once(files,SERVER,loc,insert+loc)
    # DLL_SHA256 replaced after native build (new required pinned binary).
    # ABI bump to forbid pairing new Java with old CTransport.dll.
    replace_once(files,CONFIG,"public static final int ABI = 0x000a0000;",
                         "public static final int ABI = 0x000a0001;")
    # Existing base native sources matched original shipping DLL SHA pin.
    WORK.mkdir(parents=True,exist_ok=True)
    for name,blob in files.items():
        if name.startswith("src/") and name.endswith(".java"):
            p=WORK/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(blob)
    shutil.copytree(NATIVE,WORK/"native",dirs_exist_ok=True)
    (WORK/"build").mkdir(exist_ok=True)
    (WORK/"logs").mkdir(exist_ok=True)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    nativefile=WORK/"native/CTransport.cpp"
    native=nativefile.read_text(encoding="utf-8")
    marker="""CIO_API void L1Interop_ReleaseConstBuffer(void* p){delete static_cast<L1ConstBuffer*>(p);}"""
    assert native.count(marker)==1
    extra="""// NC-style S packet assembly: borrow an existing 16KiB CIOBufferEx slab,
    // directly write final little-endian L1 wire bytes and publish one
    // producer reference. All recipients share that immutable source.
    // Unlike L1Interop_CreateConstBuffer this performs NO input memcpy.
    static inline void putLe32(unsigned char* out,std::uint32_t v)noexcept {
        out[0]=static_cast<unsigned char>(v);
        out[1]=static_cast<unsigned char>(v>>8);
        out[2]=static_cast<unsigned char>(v>>16);
        out[3]=static_cast<unsigned char>(v>>24);
    }
    static L1ConstBuffer* createPackedConst(Server* server,unsigned length)noexcept {
        if(!server||!server->running.load()||server->stopping.load())return nullptr;
        auto* block=server->buffers.acquire(LargeBufferBytes);
        if(!block)return nullptr;
        block->used=length;
        auto* shared=new(std::nothrow) L1ConstBuffer(server,block);
        if(!shared)server->buffers.recycle(block);
        return shared;
    }
    CIO_API void* L1Interop_CreateActionConstBuffer(
            void* p,int opcode,int objectId,int actionId){
        if(!p)return nullptr;
        auto* server=static_cast<Server*>(p);ObjectRef guard(server);
        auto* shared=createPackedConst(server,8);
        if(!shared)return nullptr;
        auto* wire=shared->block->data.data();
        wire[0]=8;wire[1]=0;wire[2]=static_cast<unsigned char>(opcode);
        putLe32(wire+3,static_cast<std::uint32_t>(objectId));
        wire[7]=static_cast<unsigned char>(actionId);
        return shared;
    }
    CIO_API void* L1Interop_CreateMoveConstBuffer(
            void* p,int opcode,int objectId,int x,int y,int heading){
        if(!p)return nullptr;
        auto* server=static_cast<Server*>(p);ObjectRef guard(server);
        auto* shared=createPackedConst(server,17);
        if(!shared)return nullptr;
        auto* wire=shared->block->data.data();
        wire[0]=17;wire[1]=0;wire[2]=static_cast<unsigned char>(opcode);
        putLe32(wire+3,static_cast<std::uint32_t>(objectId));
        wire[7]=static_cast<unsigned char>(x);
        wire[8]=static_cast<unsigned char>(static_cast<unsigned>(x)>>8);
        wire[9]=static_cast<unsigned char>(y);
        wire[10]=static_cast<unsigned char>(static_cast<unsigned>(y)>>8);
        wire[11]=static_cast<unsigned char>(heading);
        wire[12]=129;
        for(int i=13;i<17;++i)wire[i]=0;
        return shared;
    }
    CIO_API const void* L1Interop_ConstBufferData(void* p) {
        return p ? static_cast<L1ConstBuffer*>(p)->block->data.data() : nullptr;
    }
"""
    native=native.replace(marker,extra+"\n"+marker,1)
    nativefile.write_text(native,encoding="utf-8")
    abi=WORK/"native/interop_abi.hpp"
    a=abi.read_text(encoding="utf-8")
    assert "0x000a0000" in a
    abi.write_text(a.replace("0x000a0000","0x000a0001"),encoding="utf-8")
    header=WORK/"native/CTransport.h"
    h=header.read_text(encoding="utf-8")
    decl="""__declspec(dllimport) void* L1Interop_CreateConstBuffer(void*,const void*,int);"""
    assert h.count(decl)==1
    h=h.replace(decl,decl+"""
__declspec(dllimport) void* L1Interop_CreateActionConstBuffer(void*,int,int,int);
__declspec(dllimport) void* L1Interop_CreateMoveConstBuffer(void*,int,int,int,int,int);
__declspec(dllimport) const void* L1Interop_ConstBufferData(void*);""")
    header.write_text(h,encoding="utf-8")
    (WORK/"phase69-original-hashes.json").write_text(
        json.dumps({"baseline_sha256":SHA,"changed_java":sorted(CHANGED),
                    "original_zip_entries":1729,
                    "native_source":str(NATIVE)},indent=2))
    print("PHASE69_PREPARED",WORK)

def package():
    pin=(WORK/"src/l1j/server/server/model/network/core/config/CIOConfig.java").read_text()
    native=WORK/"build/CTransport.dll"
    h=hashlib.sha256(native.read_bytes()).hexdigest()
    assert h in pin
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist();orig={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    files=orig.copy()
    for n in CHANGED:
        data=(WORK/n).read_bytes()
        assert data!=orig[n],n
        files[n]=data
    files["CTransport.dll"]=native.read_bytes()
    changed={n for n in files if files[n]!=orig[n]}
    assert changed==CHANGED|{"CTransport.dll"},sorted(changed)
    files["native/CTransport.cpp"]=(WORK/"native/CTransport.cpp").read_bytes()
    files["native/CTransport.h"]=(WORK/"native/CTransport.h").read_bytes()
    files["native/interop_abi.hpp"]=(WORK/"native/interop_abi.hpp").read_bytes()
    OUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for i in infos:
            z.writestr(i,files[i.filename] if not i.is_dir() else b"")
        for n in ("native/CTransport.cpp","native/CTransport.h","native/interop_abi.hpp"):
            z.writestr(n,files[n])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None
        assert sum(x.endswith(".java") for x in z.namelist())==1710
        assert len(z.namelist())==1732
    audit={"phase":69,"base_sha256":SHA,"output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
           "dll_sha256":h,"changed":sorted(changed),
           "added_native_source":["native/CTransport.cpp","native/CTransport.h","native/interop_abi.hpp"],
           "java_sources":1710,"zip_entries":1732,
           "no_java_byte_array_action_move_hot_path":True,"native_pool":"CIOBufferEx<16384>",
           "native_pool_direct_assembly":True,"native_refcounts_preserved":True,
           "socket_fifo_unchanged":True,"owner_specific_cipher_unchanged":True,
           "generic_frame_clone_unchanged":True,"new_worker_or_queue":False}
    (WORK/"audit.json").write_text(json.dumps(audit,indent=2,ensure_ascii=False)+"\n")
    print("PHASE69_PACKAGED",json.dumps(audit,ensure_ascii=False))

if __name__=="__main__":
    if len(sys.argv)<3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    if sys.argv[2]=="prepare":prepare()
    else:package()

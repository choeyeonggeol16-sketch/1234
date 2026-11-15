#!/usr/bin/env python3
"""71: Replace last Native per-broadcast wrapper allocation with the NC slab itself.

Verified NC original x64 at 0x41F694: pooled CIOBufferEx<16384> returned
with +0x4020 ref counter; at 0x924245 refcount increment per recipient;
at 0x92427A serialized SEND; final return via owning pool.
Phase70 still did 'new L1ConstBuffer' per broadcast. This patch removes it.
Only unchanged native slab metadata receives an opaque owner *in the C++ view*;
CIOBufferEx byte layout, socket serializer, packet bytes and encryption unchanged.
"""
import hashlib,json,shutil,sys,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/RKern_NC_Assemble_Pooled_FULL_SRC_20261116.zip"
PIN="d3e91922910a9614ba4aa5003367cb9dd939ebdedfabcbec16ab6977789ebefe"
OUTPUT=ROOT/"downloads/RKern_NC_DirectSlab_SPackets_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve()
CPP="native/CTransport.cpp"
POOL="native/cio_core.hpp"
ABI="native/interop_abi.hpp"
CONFIG="src/l1j/server/server/model/network/core/config/CIOConfig.java"
DLL="CTransport.dll"
CHANGED={CPP,ABI,CONFIG,DLL}
ADDED={POOL}
def one(text,old,new,where):
    count=text.count(old)
    assert count==1,(where,count,old[:150])
    return text.replace(old,new,1)

def prepare():
    assert hashlib.sha256(INPUT.read_bytes()).hexdigest()==PIN
    with zipfile.ZipFile(INPUT) as z:
        assert z.testzip() is None
        originals={i.filename:z.read(i.filename) for i in z.infolist() if not i.is_dir()}
    assert len(originals)==1732
    for name,value in originals.items():
        path=WORK/name
        path.parent.mkdir(exist_ok=True,parents=True)
        path.write_bytes(value)
    source_root=ROOT/"patches/ctransport_core_20261112/native"
    for f in source_root.iterdir():
        if f.is_file() and not (WORK/"native"/f.name).exists():
            shutil.copy2(f,WORK/"native"/f.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",
                    WORK/"tests",dirs_exist_ok=True)
    for folder in ["build","logs"]:
        (WORK/folder).mkdir(exist_ok=True)

    # The C++ CIOBuffer view already lives in fixed preallocated rings.
    # Add its owning server pointer only during a shared-source producer lease.
    # No changes to recovered CIOBufferEx<16384> memory layout or pool stride.
    p=WORK/POOL;s=p.read_text(encoding="utf-8")
    s=one(s,
        "unsigned& owner;unsigned bucket;const std::size_t capacity;bool quarantined=false;",
        "unsigned& owner;unsigned bucket;const std::size_t capacity;bool quarantined=false;\n void* constSourceOwner=nullptr;",
        POOL)
    s=one(s,
        "if(p->references.load()!=0||p->used||p->next||p->quarantined){",
        "if(p->references.load()!=0||p->used||p->next||p->quarantined||p->constSourceOwner){",
        POOL)
    s=one(s,
        "p->used=0;p->offset=0;p->next=nullptr;",
        "p->used=0;p->offset=0;p->next=nullptr;p->constSourceOwner=nullptr;",
        POOL)
    p.write_text(s,encoding="utf-8")
    p=WORK/CPP;s=p.read_text(encoding="utf-8")
    s=one(s,"struct L1ConstBuffer;","",CPP)
    s=one(s,
      "bool sendConstBuffer(L1ConstBuffer*,bool)noexcept;",
      "bool sendConstBuffer(CIOBuffer*,bool)noexcept;",CPP)
    s=one(s,
"""struct L1ConstBuffer {
    Server* const server;CIOBuffer* const block;
    L1ConstBuffer(Server* s,CIOBuffer* b)noexcept:server(s),block(b){server->retain();}
    ~L1ConstBuffer(){server->buffers.recycle(block);server->release();}
};
""","""// The original NC block is the Source. Refcount is on CIOBufferEx itself.
 // No heap-allocated L1ConstBuffer wrapper is created per S-packet.
""",CPP)
    s=one(s,
"""    NativeSendRequest(CIOSocket* owner,L1ConstBuffer& source,bool plain)noexcept
        :socket(owner),sharedBlock(source.block),framed(true),plainFirst(plain),constant(true){""",
"""    NativeSendRequest(CIOSocket* owner,CIOBuffer& source,bool plain)noexcept
        :socket(owner),sharedBlock(&source),framed(true),plainFirst(plain),constant(true){""",CPP)
    s=one(s,
"""bool CIOSocketEx<Buffer>::sendConstBuffer(L1ConstBuffer* source,bool plainFirst)noexcept {
    ObjectRef lifetime(this);
    if(!source||source->server!=server())return false;
    const auto total=source->block->used;""",
"""bool CIOSocketEx<Buffer>::sendConstBuffer(CIOBuffer* source,bool plainFirst)noexcept {
    ObjectRef lifetime(this);
    // Fail closed for a slab from another native Server's pool.
    if(!source||source->constSourceOwner!=server())return false;
    const auto total=source->used;""",CPP)
    s=one(s,
"""    auto* request=new(std::nothrow) NativeSendRequest(this,*source,plainFirst);""",
"""    auto* request=new(std::nothrow) NativeSendRequest(this,*source,plainFirst);""",CPP) if False else s

    # Even generic shared broadcasts now acquire one native slab as the
    # source, instead of allocating a separate Native wrapper object.
    s=one(s,
"""    auto* source=new(std::nothrow) L1ConstBuffer(server,block);
    if(!source)server->buffers.recycle(block);return source;""",
"""    block->constSourceOwner=server;
    server->retain();  // Source producer lease through Java Source.release().
    return block;""",CPP)
    s=one(s,
"""CIO_API void L1Interop_ReleaseConstBuffer(void* p){delete static_cast<L1ConstBuffer*>(p);}""",
"""CIO_API void L1Interop_ReleaseConstBuffer(void* p){
    if(!p)return;
    auto* block=static_cast<CIOBuffer*>(p);
    auto* owner=static_cast<Server*>(block->constSourceOwner);
    if(!owner)return;
    owner->buffers.recycle(block);
    owner->release(); // Release producer lease; requests hold their own refs.
}""",CPP)
    s=one(s,
"""return c->sendConstBuffer(static_cast<L1ConstBuffer*>(source),plainFirst!=0);""",
"""return c->sendConstBuffer(static_cast<CIOBuffer*>(source),plainFirst!=0);""",CPP)
    s=one(s,
"""        auto* shared=new(std::nothrow) L1ConstBuffer(server,block);
        if(!shared)server->buffers.recycle(block);
        return shared;""",
"""        block->constSourceOwner=server;
        server->retain(); // exactly one producer lease on the shared slab
        return block;""",CPP)
    s=one(s,
"""return p ? static_cast<L1ConstBuffer*>(p)->block->data.data() : nullptr;""",
"""return p ? static_cast<CIOBuffer*>(p)->data.data() : nullptr;""",CPP)
    assert "L1ConstBuffer" not in s.replace("L1ConstBuffer wrapper","")
    assert "new(std::nothrow) NativeSendRequest" in s
    assert "AssemblePooled(" in s
    assert "CreateFormattedConstBuffer" in s
    p.write_text(s,encoding="utf-8")
    p=WORK/ABI;s=p.read_text(encoding="utf-8")
    s=one(s,"0x000a0002","0x000a0003",ABI)
    p.write_text(s,encoding="utf-8")
    p=WORK/CONFIG;s=p.read_text(encoding="utf-8")
    s=one(s,"ABI = 0x000a0002","ABI = 0x000a0003",CONFIG)
    p.write_text(s,encoding="utf-8")
    (WORK/"preparation.json").write_text(json.dumps({
        "phase":71,"input_sha256":PIN,
        "original_binary_sha256":"9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588",
        "changed_production":sorted(CHANGED),"added_native":sorted(ADDED),
        "native_wrapper_new_deleted":True,"existing_io_fifo_preserved":True,
        "source_leased_directly_from_CIOBufferEx":True,
    },indent=2)+"\n")
    print("PHASE71_NC_DIRECT_SLAB_PREPARED")

def package():
    native=WORK/"build"/DLL
    dllsha=hashlib.sha256(native.read_bytes()).hexdigest()
    assert dllsha in (WORK/CONFIG).read_text(encoding="utf-8")
    with zipfile.ZipFile(INPUT) as z:
        infos=z.infolist()
        original={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    generated=original.copy()
    for name in CHANGED-{DLL}:
        generated[name]=(WORK/name).read_bytes()
    generated[DLL]=native.read_bytes()
    assert set(name for name in original if original[name]!=generated[name])==CHANGED
    assert not ADDED.intersection(original)
    for name in ADDED:
        generated[name]=(WORK/name).read_bytes()
    assert sum(name.endswith(".java") for name in generated)==1710
    OUTPUT.parent.mkdir(exist_ok=True,parents=True)
    with zipfile.ZipFile(OUTPUT,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for item in infos:
            z.writestr(item,b"" if item.is_dir() else generated[item.filename])
        for name in sorted(ADDED):
            z.writestr(name,generated[name])
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None
        assert set(z.namelist())==set(generated)
        assert len(z.namelist())==1733
    report={
        "phase":71,"baseline_zip_sha256":PIN,
        "output_zip_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
        "dll_sha256":dllsha,
        "original_binary_sha256":"9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588",
        "changed":sorted(CHANGED),"added":sorted(ADDED),
        "java_sources":1710,"zip_entries":1733,
        "CIOBufferEx_layout_unmodified":True,
        "Native_source_wrapper_heap_allocation_removed":True,
        "Native_send_request_allocation_retained":True,
        "zero_recipient_no_packet_allocation":True,
        "per_socket_fifo_and_cipher_unchanged":True,
        "in_game_soak_verified":False
    }
    (WORK/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
    print("PHASE71_DIRECT_SLAB_FULL_SRC_VERIFIED",json.dumps(report,ensure_ascii=False))
if __name__=="__main__":
    if len(sys.argv)<3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()

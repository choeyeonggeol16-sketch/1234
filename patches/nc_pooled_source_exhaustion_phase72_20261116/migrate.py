#!/usr/bin/env python3
"""Phase72: Native shared S packet source pool depletion must not drop movement.

Preserve phase71 NC 0x41e6e4/0x41f694 pooled Assemble and native
ref-counting SEND. When one fixed per-owner 16KiB ring is fully borrowed,
the SAME CIOSource buffer object contract leases an elastic 16KiB slab
for just that in-flight source, releasing it at final reference decrement.
Not a parallel packet encoder, SEND fallback, queue, worker or per-viewer wire.
True system OOM is explicit in Java; no finite-memory implementation can
guarantee delivery once no memory and no backpressure remain.
"""
import hashlib, json, shutil, sys, zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_DirectSlab_SPackets_FULL_SRC_20261116.zip"
PIN="e04cdcf7df4a1bb30a6d6ae1b93d0d09cb4125f830e7b99f3b55ec833ff6d2d0"
OUT=ROOT/"downloads/RKern_NC_PoolExhaustion_NoSilentDrop_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve()
POOL="native/cio_core.hpp"
CPP="native/CTransport.cpp"
ABI="native/interop_abi.hpp"
CONFIG="src/l1j/server/server/model/network/core/config/CIOConfig.java"
BROADCAST="src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java"
DLL="CTransport.dll"
TEST="native/nc_source_pool_overflow_test.cpp"
CHANGED={POOL,CPP,ABI,CONFIG,BROADCAST,DLL}
ADDED={TEST}

def once(s,old,new,path):
    n=s.count(old)
    assert n==1,("patch anchor",path,n,old[:130])
    return s.replace(old,new,1)

def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        originals={i.filename:z.read(i.filename) for i in z.infolist() if not i.is_dir()}
    assert len(originals)==1733
    assert sum(n.endswith(".java") for n in originals)==1710
    for name, blob in originals.items():
        dst=WORK/name
        dst.parent.mkdir(parents=True,exist_ok=True)
        dst.write_bytes(blob)
    # C++ build/testing support files from the original source baseline.
    native_root=ROOT/"patches/ctransport_core_20261112/native"
    for src in native_root.iterdir():
        if src.is_file() and not (WORK/"native"/src.name).exists():
            shutil.copy2(src,WORK/"native"/src.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    (WORK/"build").mkdir(exist_ok=True)
    (WORK/"logs").mkdir(exist_ok=True)

    p=WORK/POOL;s=p.read_text(encoding="utf-8")
    s=once(s,
        "unsigned& owner;unsigned bucket;const std::size_t capacity;bool quarantined=false;\n void* constSourceOwner=nullptr;",
        "unsigned& owner;unsigned bucket;const std::size_t capacity;bool quarantined=false;\n void* constSourceOwner=nullptr;\n void* sourceOverflowRecord=nullptr;",
        POOL)
    marker=" std::unique_ptr<Ring[]>rings_{new Ring[OwnerCount*2]};"
    overflow=""" // Native packet SOURCE-only elastic slabs: fixed regular rings NEVER grow.
 // A source that cannot obtain its owner's regular 16KiB slot can still use
 // the identical CIOBuffer refcount and SEND path. No extra SEND path exists.
 // The extra slab is freed on the final producer/request reference, not cached.
 struct SourceOverflowSlab {
   CIOBufferEx<16384> raw;
   CIOBuffer view;
   SourceOverflowSlab()noexcept:view(raw,~0u,1){
     view.sourceOverflowRecord=this;
     view.references.store(1,std::memory_order_release);
   }
 };
 std::atomic<std::uint64_t> sourceOverflowActive_{0},
                            sourceOverflowCreated_{0},
                            sourceOverflowFailures_{0};
"""
    s=once(s,marker,overflow+marker,POOL)
    marker=" static void retain(CIOBuffer* p)noexcept{if(p)p->references.fetch_add(1,std::memory_order_relaxed);}"
    acquire=""" // Only shared immutable S-packet plaintext uses this elastic source path.
 // Normal SEND/RECV allocations preserve the old fixed-ring policy.
 CIOBuffer* acquireSharedSource()noexcept{
   if(auto* block=acquire(LargeBufferBytes))return block;
   auto* overflow=new(std::nothrow) SourceOverflowSlab();
   if(!overflow){++sourceOverflowFailures_;return nullptr;}
   ++sourceOverflowCreated_;++sourceOverflowActive_;
   return &overflow->view;
 }
 std::uint64_t sourceOverflowActive()const noexcept{return sourceOverflowActive_.load();}
 std::uint64_t sourceOverflowCreated()const noexcept{return sourceOverflowCreated_.load();}
 std::uint64_t sourceOverflowFailures()const noexcept{return sourceOverflowFailures_.load();}
"""
    s=once(s,marker,acquire+marker,POOL)
    s=once(s,
        "   if(previous!=1)return;\n   if(p->owner>=OwnerCount||p->bucket>1){++corruptions_;return;}",
        """   if(previous!=1)return;
   // Same refcount contract as fixed CIOBufferEx slabs. A live producer
   // Source or queued NativeSendRequest prevents this final release.
   if(p->sourceOverflowRecord){
     auto* record=static_cast<SourceOverflowSlab*>(p->sourceOverflowRecord);
     sourceOverflowActive_.fetch_sub(1,std::memory_order_acq_rel);
     delete record;
     return;
   }
   if(p->owner>=OwnerCount||p->bucket>1){++corruptions_;return;}""",
        POOL)
    s=once(s,
        "if(p->references.load()!=0||p->used||p->next||p->quarantined||p->constSourceOwner){",
        "if(p->references.load()!=0||p->used||p->next||p->quarantined||p->constSourceOwner||p->sourceOverflowRecord){",
        POOL)
    s=once(s,
        "p->used=0;p->offset=0;p->next=nullptr;p->constSourceOwner=nullptr;",
        "p->used=0;p->offset=0;p->next=nullptr;p->constSourceOwner=nullptr;p->sourceOverflowRecord=nullptr;",
        POOL)
    assert "sourceOverflowActive()" in s and "CIOBufferEx<16384> raw" in s
    p.write_text(s,encoding="utf-8")

    p=WORK/CPP;s=p.read_text(encoding="utf-8")
    old="server->buffers.acquire(LargeBufferBytes)"
    assert s.count(old)==2,("expected exactly 2 Native const source allocation callsites",s.count(old))
    s=s.replace(old,"server->buffers.acquireSharedSource()")
    # No SEND, socket ownership, cipher, source Release or output pool logic changed.
    assert "AssemblePooled(" in s and "L1Interop_CreateFormattedConstBuffer" in s
    assert "L1Interop_ReleaseConstBuffer" in s
    assert "L1Interop_CreateConstBuffer" in s
    p.write_text(s,encoding="utf-8")

    # Do not silently suppress a broadcast if both the fixed ring AND OS
    # allocation fail (or the server has been closed). Failing to publish an
    # object position is a protocol-integrity fault, not a successful send.
    p=WORK/BROADCAST;s=p.read_text(encoding="utf-8")
    s=once(s,
      "if (shared == null) return; // NC bounded pool admission; no unsafe fallback",
      "if (shared == null) throw new IllegalStateException(\"S_ACTION shared Native source unavailable (allocation or shutdown)\");",
      BROADCAST)
    s=once(s,
      "if (shared == null) return; // NC bounded pool admission; no unsafe fallback",
      "if (shared == null) throw new IllegalStateException(\"S_MOVE shared Native source unavailable (allocation or shutdown)\");",
      BROADCAST)
    p.write_text(s,encoding="utf-8")

    p=WORK/ABI;s=p.read_text(encoding="utf-8")
    s=once(s,"0x000a0003","0x000a0004",ABI)
    p.write_text(s,encoding="utf-8")
    p=WORK/CONFIG;s=p.read_text(encoding="utf-8")
    s=once(s,"ABI = 0x000a0003","ABI = 0x000a0004",CONFIG)
    p.write_text(s,encoding="utf-8")
    # A test file lives under native/ in the ZIP so the exact pool source and
    # test can be rebuilt without relying on the repo's patch scripts.
    test=ROOT/"patches/nc_pooled_source_exhaustion_phase72_20261116/test_native_overflow.cpp"
    assert test.exists(),test
    shutil.copy2(test,WORK/TEST)
    print("PHASE72_NATIVE_ELASTIC_SOURCE_PREPARED",WORK)

def package():
    dll=WORK/"build"/DLL
    dllsha=hashlib.sha256(dll.read_bytes()).hexdigest()
    assert dllsha in (WORK/CONFIG).read_text(encoding="utf-8")
    assert "ABI = 0x000a0004" in (WORK/CONFIG).read_text(encoding="utf-8")
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist()
        original={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    out=original.copy()
    for name in CHANGED-{DLL}:
        out[name]=(WORK/name).read_bytes()
    out[DLL]=dll.read_bytes()
    changed={n for n in original if original[n]!=out[n]}
    assert changed==CHANGED,(changed,CHANGED)
    assert not (ADDED & set(original))
    for name in ADDED:out[name]=(WORK/name).read_bytes()
    assert sum(n.endswith(".java") for n in out)==1710
    assert set(out)==set(original)|ADDED
    OUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for item in infos:
            z.writestr(item,b"" if item.is_dir() else out[item.filename])
        for name in sorted(ADDED):z.writestr(name,out[name])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None
        assert len(z.namelist())==1734
    audit={
      "phase":72,"baseline_sha256":PIN,
      "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
      "dll_sha256":dllsha,"native_abi":"0x000a0004",
      "changed":sorted(changed),"added":sorted(ADDED),
      "java_count":1710,"zip_entries":1734,
      "new_worker":False,"new_send_queue":False,"packet_specific_encoder":False,
      "source_path":"CIOBufferPool.acquireSharedSource",
      "regular_ring_fixed":True,"fixed_ring_exhaustion":"elastic per-source native CIOBufferEx; retain/recycle through existing path",
      "genuine_os_allocation_failure":"explicit Java exception, not silent skip",
      "unbounded_guarantee":False,
      "io_cipher_fifo_unchanged":True,
      "all_other_files_byte_identical":True,
      "game_5000_soak_verified":False
    }
    (WORK/"audit.json").write_text(json.dumps(audit,indent=2,ensure_ascii=False)+"\n")
    print("PHASE72_NATIVE_SHARED_SOURCE_COMPLETE",json.dumps(audit,ensure_ascii=False))
if __name__=="__main__":
    if len(sys.argv)<3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()

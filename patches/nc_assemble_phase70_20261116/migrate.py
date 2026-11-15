#!/usr/bin/env python3
"""Phase70. NC binary-grounded shared S-packet assembler (NOT per-opcode writer).

Source of truth: original SHA256 9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588
at original addresses 0x41e6e4,0x41f694,0x70429c,0x421c18,0x57e1d8,
0x9241f8,0x45c7ac,0x90e6c4.
The FFM boundary is an L1-specific interop adapter. Never mislabel the ABI
wrapper an original NC exported symbol. Existing L1 packet bytes remain L1.
"""
import hashlib,json,re,shutil,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_Pooled_SPackets_FULL_SRC_20261116.zip"
PIN="f02337601b96e3ffeae8d31c55fc52512c4e9d111b854c8c92a5442e10f4050a"
OUT=ROOT/"downloads/RKern_NC_Assemble_Pooled_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve()
P="src/l1j/server/server/model/network/core/"
SERVER=P+"transport/CIOServer.java"
CONFIG=P+"config/CIOConfig.java"
NATIVE="native/CTransport.cpp"
HEADER="native/CTransport.h"
ABI="native/interop_abi.hpp"
CHANGED={SERVER,CONFIG,NATIVE,HEADER,ABI,"CTransport.dll"}

def once(text,old,new,name):
    count=text.count(old)
    assert count==1,("anchor mismatch",name,count,old[:120])
    return text.replace(old,new,1)

def prepared():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN,"base ZIP mismatch"
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i.filename) for i in z.infolist() if not i.is_dir()}
    assert len(files)==1732 and sum(n.endswith(".java") for n in files)==1710
    for name,value in files.items():
        target=WORK/name
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes(value)
    # Required MSVC build helpers from the matching Native source snapshot.
    source_root=ROOT/"patches/ctransport_core_20261112/native"
    for f in source_root.iterdir():
        if f.is_file() and not (WORK/"native"/f.name).exists():
            shutil.copy2(f,WORK/"native"/f.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",
                    WORK/"tests",dirs_exist_ok=True)
    (WORK/"build").mkdir(exist_ok=True)
    (WORK/"logs").mkdir(exist_ok=True)
    native_file=WORK/NATIVE
    src=native_file.read_text(encoding="utf-8")
    start=src.index("    static inline void putLe32(")
    end=src.index("    CIO_API const void* L1Interop_ConstBufferData(",start)
    assert "L1Interop_CreateActionConstBuffer" in src[start:end]
    assert "L1Interop_CreateMoveConstBuffer" in src[start:end]
    replacement="""    // Original NC 0x41f694 calls 0x41e6e4 for one pooled CIOBufferEx,
    // then 0x70429c Assemble directly into payload+2 and writes the header.
    // This is the L1 interop reconstruction of that *shared* assembly path:
    // no heap byte[], no intermediate vector, no per-packet Native encoder.
    // This L1 adapter accepts the integer-only c/C/h/d/Q/p/f subset used by
    // the two existing L1 packet types. Original NC also has s/S/b support;
    // those are intentionally not exposed through unsafe FFM pointers.
    static L1ConstBuffer* AssemblePooled(
            Server* server,const char* format,
            const std::uint64_t* args,unsigned argc)noexcept {
        if(!server||!format||!args||!server->running.load()||
                server->stopping.load())return nullptr;
        auto* block=server->buffers.acquire(LargeBufferBytes);
        if(!block)return nullptr;
        unsigned n=2,consumed=0;
        unsigned char* const bytes=block->data.data();
        for(const char* f=format;*f;++f){
            unsigned width=0;
            switch(*f){
                case 'c':case 'C':width=1;break;
                case 'h':width=2;break;
                case 'd':width=4;break;
                case 'Q':case 'p':case 'f':width=8;break;
                default:continue; // original Assemble ignores unknown format chars
            }
            if(consumed>=argc||width>LargeBufferBytes-n){
                server->buffers.recycle(block);
                return nullptr;
            }
            const std::uint64_t value=args[consumed++];
            for(unsigned j=0;j<width;++j)
                bytes[n+j]=static_cast<unsigned char>(value>>(8*j));
            n+=width;
        }
        if(n<=2||consumed!=argc){
            server->buffers.recycle(block);
            return nullptr;
        }
        bytes[0]=static_cast<unsigned char>(n);
        bytes[1]=static_cast<unsigned char>(n>>8);
        block->used=n;
        auto* shared=new(std::nothrow) L1ConstBuffer(server,block);
        if(!shared)server->buffers.recycle(block);
        return shared;
    }

    // FFM adapter: tags describe L1 format schemas, but both packets use the
    // SAME original-style pool -> Assemble -> frame length -> shared ref path.
    // L1 packet formats remain unchanged, since L2 opcodes aren't L1 opcodes.
    CIO_API void* L1Interop_CreateFormattedConstBuffer(
            void* p,int kind,int opcode,int objectId,int a,int b,int c){
        if(!p)return nullptr;
        auto* server=static_cast<Server*>(p);
        ObjectRef guard(server);
        if(kind==1){
            const std::uint64_t args[3]={
                static_cast<unsigned>(opcode),static_cast<unsigned>(objectId),
                static_cast<unsigned>(a)};
            return AssemblePooled(server,"cdc",args,3);
        }
        if(kind==2){
            const std::uint64_t args[7]={
                static_cast<unsigned>(opcode),static_cast<unsigned>(objectId),
                static_cast<unsigned>(a),static_cast<unsigned>(b),
                static_cast<unsigned>(c),129u,0u};
            return AssemblePooled(server,"cdhhccd",args,7);
        }
        return nullptr;
    }
"""
    src=src[:start]+replacement+src[end:]
    assert "L1Interop_CreateActionConstBuffer" not in src
    assert "L1Interop_CreateMoveConstBuffer" not in src
    assert "AssemblePooled(" in src
    native_file.write_text(src,encoding="utf-8")
    header=WORK/HEADER
    text=header.read_text(encoding="utf-8")
    text=once(text,
        """__declspec(dllimport) void* L1Interop_CreateActionConstBuffer(void*,int,int,int);
__declspec(dllimport) void* L1Interop_CreateMoveConstBuffer(void*,int,int,int,int,int);""",
        """__declspec(dllimport) void* L1Interop_CreateFormattedConstBuffer(void*,int,int,int,int,int,int);""",
        HEADER)
    header.write_text(text,encoding="utf-8")
    abi=WORK/ABI
    text=abi.read_text(encoding="utf-8")
    text=once(text,"0x000a0001","0x000a0002",ABI)
    abi.write_text(text,encoding="utf-8")
    config=WORK/CONFIG
    text=config.read_text(encoding="utf-8")
    text=once(text,"ABI = 0x000a0001","ABI = 0x000a0002",CONFIG)
    config.write_text(text,encoding="utf-8")
    server=WORK/SERVER
    text=server.read_text(encoding="utf-8")
    text=once(text,
        "private final MethodHandle createPooledAction,createPooledMove,pooledConstData;",
        "private final MethodHandle createFormattedConstBuffer,pooledConstData;",
        SERVER)
    text=once(text,
        """            createPooledAction=fn("L1Interop_CreateActionConstBuffer",FunctionDescriptor.of(P,P,I,I,I));
            createPooledMove=fn("L1Interop_CreateMoveConstBuffer",FunctionDescriptor.of(P,P,I,I,I,I,I));""",
        """            createFormattedConstBuffer=fn("L1Interop_CreateFormattedConstBuffer",
                    FunctionDescriptor.of(P,P,I,I,I,I,I,I));""",SERVER)
    text=once(text,
        "handle = (MemorySegment) createPooledAction.invokeExact(server, opcode, objectId, actionId);",
        """handle = (MemorySegment) createFormattedConstBuffer.invokeExact(
                    server, 1, opcode, objectId, actionId, 0, 0);""",SERVER)
    text=once(text,
        """handle = (MemorySegment) createPooledMove.invokeExact(
                    server, opcode, objectId, x, y, heading);""",
        """handle = (MemorySegment) createFormattedConstBuffer.invokeExact(
                    server, 2, opcode, objectId, x, y, heading);""",SERVER)
    server.write_text(text,encoding="utf-8")
    assert "createPooledAction.invokeExact" not in text and "createPooledMove.invokeExact" not in text
    print("PHASE70_ORIGINAL_NC_ASSEMBLE_PREPARED",WORK)

def package():
    dll=WORK/"build/CTransport.dll"
    config=(WORK/CONFIG).read_text(encoding="utf-8")
    dllsha=hashlib.sha256(dll.read_bytes()).hexdigest()
    assert dllsha in config and "ABI = 0x000a0002" in config
    with zipfile.ZipFile(BASE) as z:
        info=z.infolist()
        orig={i.filename:z.read(i.filename) for i in info if not i.is_dir()}
    actual={name:(WORK/name).read_bytes() for name in CHANGED if name!="CTransport.dll"}
    actual["CTransport.dll"]=dll.read_bytes()
    assert set(actual)==CHANGED
    for name in orig:
        if name not in CHANGED:actual[name]=orig[name]
    assert set(actual)==set(orig)
    changed={name for name in orig if actual[name]!=orig[name]}
    assert changed==CHANGED,("unexpected changed",changed,CHANGED)
    assert sum(n.endswith(".java") for n in actual)==1710
    OUT.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for item in info:
            z.writestr(item, b"" if item.is_dir() else actual[item.filename])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None
        assert z.namelist()==[i.filename for i in info]
    report={
        "phase":70,"base_sha256":PIN,
        "output_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "dll_sha256":dllsha,"changed":sorted(changed),
        "source_count_java":1710,"zip_entries":len(info),
        "original_binary_sha256":"9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588",
        "verified_nc_broadcast_call_chain":[
           "0x41e6e4","0x41f694","0x70429c","0x421c18","0x57e1d8",
           "0x9241f8","0x45c7ac","0x90e6c4"],
        "nc_equivalent_policy":{
            "native_pooled_assembler":True,
            "direct_write_into_pooled_block":True,
            "original_nc_format_semantics_integer_subset":True,
            "single_immutable_source_shared":True,
            "per_socket_fifo_and_cipher":True,
            "source_refcount_to_pool":True,
            "l1_wire_fields_preserved":True,
            "no_per_packet_native_assembly_function":True,
            "no_java_byte_arrays_in_broadcast_hotpath":True,
            "all_original_nc_l2_packet_opcodes_used":False,
            "nc_exact_l2_movement_action_callsite_implemented":False
        }
    }
    (WORK/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
    print("PHASE70_POOLED_ASSEMBLE_VERIFIED_ZIP",json.dumps(report,ensure_ascii=False))

if __name__=="__main__":
    if len(sys.argv)<3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate70.py WORK prepare|package")
    if sys.argv[2]=="prepare":prepared()
    else:package()

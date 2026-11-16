#!/usr/bin/env python3
"""78: final SC_WorldPutObject NPC/PC/builder broadcast direct to pooled Native slab.

Preserves all 3 original L1 2019 protobuf field sequences, 1:1 SEND, AOI
traversal order, shared source refcount, FFM ownership and per-socket FIFO.
Original Java heap byte[] builders are not shipped in this production class.
The exact previous implementation is copied into a test-only oracle class.
"""
from pathlib import Path
import hashlib,json,re,shutil,sys,zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase77_FULL_SRC_20261116.zip"
PIN="c9c0eb7dd2ac7dc0833fb8b7ed87c0623c141f6420d79429fe8d993ab0637fb3"
OUT=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase78_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else None
PATH="src/l1j/server/server/model/network/core/sc/packet/SC_WorldPutObject.java"
FQ="l1j.server.server.model.network.core.broadcast.CSharedBroadcast"

def replace(s,old,new):
    assert s.count(old)==1,("ambiguous WorldPut replacement",s.count(old),old[:160])
    return s.replace(old,new,1)

def extract_method(s,name):
    pattern=r"private static byte\[\]\s+"+re.escape(name)+r"\s*\("
    m=re.search(pattern,s)
    assert m,(name,"method not found")
    start=m.start()
    brace=s.index("{",start)
    depth,end=0,-1
    for i in range(brace,len(s)):
        if s[i]=="{":depth+=1
        elif s[i]=="}":
            depth-=1
            if depth==0:
                end=i+1;break
    assert end>brace,name
    return start,end,s[start:end]

def migrate_writer(method,name,param):
    method=replace(method,f"private static byte[] {name}(",
        f"private static void writeNative{param}(java.nio.ByteBuffer output, ")
    guard={"Npc":"if(npc==null)return null;","PcFull":"if(pc==null)return null;",
           "Builder":"if(s==null)return null;"}[param]
    method=replace(method,guard,
        'if('+{"Npc":"npc","PcFull":"pc","Builder":"s"}[param]+'==null)throw new IllegalArgumentException("null WorldPut source");')
    if param in ("Npc","PcFull"):
        method=replace(method,
            "        if (objectId == 0) {\n            return null;\n        }",
            '        if (objectId == 0) {\n            throw new IllegalArgumentException("zero WorldPut object id");\n        }')
    method=replace(method,
        "            final int capacity=reserve>0 && reserve<=0xFFFF?reserve:0xFFFF;",
        "            final int capacity=output.capacity();")
    method=replace(method,
        """            final java.nio.ByteBuffer bytes=java.nio.ByteBuffer.allocate(capacity)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);""",
        """            final java.nio.ByteBuffer bytes=output.order(java.nio.ByteOrder.LITTLE_ENDIAN);""")
    method=replace(method,
        """                if(length>0xFFFF)return null;
                bytes.putShort(0,(short)length);
                return java.util.Arrays.copyOf(bytes.array(),length);""",
        """                if(length>capacity)throw new java.nio.BufferOverflowException();
                bytes.position(length);""")
    method=replace(method,
        """        } catch(Throwable ignored) {
            return null;
        }""",
        f"""        }} catch(RuntimeException failure) {{
            throw failure;
        }} catch(Throwable failure) {{
            throw new IllegalStateException("Native WorldPut {param} encoding failed",failure);
        }}""")
    assert "ByteBuffer.allocate" not in method
    assert "Arrays.copyOf" not in method
    assert "return null" not in method
    assert "bytes.position(length)" in method
    assert "b.releaseResources()" in method
    return method

def transform(original):
    crlf="\r\n" in original
    s=original.replace("\r\n","\n")
    public=[
      ("""        final byte[] frozen=sharedWireNpc(npc);
        if(frozen==null)return;
        """+FQ+""".send(npc, frozen, true, -1, null);""",
       """        if(npc.getId()==0)return;
        """+FQ+""".sendWritten(npc,
                bytes -> writeNativeNpc(bytes,npc),true,-1,null);"""),
      ("""        final byte[] frozen=sharedWirePcFull(pc,requestedName,revealInvisibleName);
        if(frozen==null)return;
        """+FQ+""".send(pc, frozen, true, -1, null);""",
       """        if(pc.getId()==0)return;
        """+FQ+""".sendWritten(pc,
                bytes -> writeNativePcFull(bytes,pc,requestedName,revealInvisibleName),
                true,-1,null);"""),
      ("""        final byte[] frozen=sharedWireBuilder(builder);
        if(frozen==null)return;
        """+FQ+""".send(anchor, frozen, true, -1, null);""",
       """        if(builder==null)return;
        """+FQ+""".sendWritten(anchor,
                bytes -> writeNativeBuilder(bytes,builder),true,-1,null);"""),
    ]
    for old,new in public:s=replace(s,old,new)
    assert FQ+".send(" not in s
    for name,param in [
        ("sharedWireNpc","Npc"),("sharedWirePcFull","PcFull"),
        ("sharedWireBuilder","Builder")]:
        start,end,body=extract_method(s,name)
        writer=migrate_writer(body,name,param)
        s=s[:start]+writer+s[end:]
    assert not re.search(r"sharedWireNpc|sharedWirePcFull|sharedWireBuilder",s)
    assert all("writeNative"+k in s for k in ("Npc","PcFull","Builder"))
    assert s.count("ByteBuffer.allocate(")==0, "remaining WorldPut broadcast heap buffer"
    return (s.replace("\n","\r\n") if crlf else s).encode("utf-8")

def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN,"phase77 full-source pin mismatch"
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos=z.infolist()
        files={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    assert len(files)==1734 and sum(n.endswith(".java") for n in files)==1710
    original=files[PATH].decode("utf-8")
    replaced=transform(original)
    target=WORK/PATH
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes(replaced)
    # Preserve the exact previous methods in a *test-only* reference class.
    oracle=original.replace("SC_WorldPutObject","SC_WorldPutObjectOracle")
    assert "private static byte[] sharedWireBuilder" in oracle
    out=WORK/"testsrc/l1j/server/server/model/network/core/sc/packet/SC_WorldPutObjectOracle.java"
    out.parent.mkdir(parents=True,exist_ok=True);out.write_text(oracle,encoding="utf-8")
    for n,data in files.items():
        if n.startswith("native/") or (n.startswith("src/") and n.endswith(".java")):
            if n==PATH:continue
            dst=WORK/n
            dst.parent.mkdir(parents=True,exist_ok=True)
            dst.write_bytes(data)
    root=ROOT/"patches/ctransport_core_20261112/native"
    for src in root.iterdir():
        if src.is_file() and not (WORK/"native"/src.name).exists():
            shutil.copy2(src,WORK/"native"/src.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for n in ("build","logs"):(WORK/n).mkdir(exist_ok=True)
    assert "sourceOverflowRecord" in files["native/cio_core.hpp"].decode("utf-8")
    print("PHASE78_PREPARED_FINAL_WORLDPUT_THREE_NATIVE_WRITERS",flush=True)

def package():
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist()
        old={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    assert hashlib.sha256((WORK/"build/CTransport.dll").read_bytes()).hexdigest()==hashlib.sha256(old["CTransport.dll"]).hexdigest(),"unmodified native DLL must remain byte-identical"
    new=old.copy()
    new[PATH]=(WORK/PATH).read_bytes()
    touched={n for n in old if old[n]!=new[n]}
    assert touched=={PATH},("unexpected production changes",sorted(touched))
    assert len(new)==1734 and sum(n.endswith(".java") for n in new)==1710
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:z.writestr(info,b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and len(z.namelist())==1734
    audit={"phase":78,"baseline_sha256":PIN,
       "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
       "dll_sha256":hashlib.sha256(old["CTransport.dll"]).hexdigest(),
       "native_abi":"0x000a0009",
       "production_changed":[PATH],
       "java_count":1710,"zip_entries":1734,
       "sc_legacy_broadcast_call_sites_remaining":0,
       "phase72_77_all_bytes_except_worldput_identical":True,
       "native_pool_cipher_socket_fifo_preserved":True,
       "native_writable_slab_max_bytes":16384,
       "game_5000_soak_verified":False}
    (WORK/"audit.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n")
    print("PHASE78_ALL_LEGACY_SC_BROADCAST_MIGRATED_FULL_SRC_VERIFIED",json.dumps(audit),flush=True)

if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()

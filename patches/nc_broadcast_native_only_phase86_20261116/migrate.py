#!/usr/bin/env python3
"""Phase86 native-only CAsyncSendConstBuffer: retire old Java array broadcaster.

Pinned and byte-for-byte unchanged Phase85 baseline. Keep Native pooled
source refcount, FIFO, foreign-owner sendFrozenWire(ByteBuffer), per-user crypto.
"""
from pathlib import Path
import hashlib,json,zipfile,shutil,sys
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase85_FULL_SRC_20261116.zip"
PIN="79c3d805ad1b5cbd850945470191c193588e02f9c98c64123e28dd07444b431e"
OUT=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase86_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else None
P="src/l1j/server/server/model/network/core/broadcast/"
AS=P+"CAsyncSendConstBuffer.java"
ES=P+"CAsyncSendConstBufferExceptSelf.java"
CHANGED=(AS,ES)
def rep(s,old,new,count=1):
    n=s.count(old)
    assert n==count,(n,count,repr(old[:170]))
    return s.replace(old,new,count)
def convert_as(s):
    s=rep(s,"    private final byte[] wire;\n","")
    first="    public CAsyncSendConstBuffer(byte[] frame, int senderId, int pendingLimit) {"
    last="    /**\n     * NC pool ownership path."
    start=s.index(first);end=s.index(last,start)
    s=s[:start]+s[end:]
    s=rep(s,"        this.wire=null;\n","")
    s=rep(s,"        wire = null;\n","")
    s=rep(s,"        final int length = wire == null ? source.wire.remaining() : wire.length;",
             "        final int length = source.wire.remaining();")
    old="""            if(source==null) {
                try {
                    source=target.createConstBuffer(wire);
                } catch (RuntimeException failure) {
                    // Resource acquisition failed before any SEND was staged.
                    // The same frozen bytes remain available to the FIFO path.
                }
            }
"""
    s=rep(s,old,"")
    s=rep(s,"        return wire == null ? target.sendFrozenWire(source.wire)\n                : target.sendFrozenWire(wire);",
             "        return target.sendFrozenWire(source.wire);")
    s=rep(s,"""        if(writer!=null && writer.wireLength()>16384)
            return CSharedBroadcast.sendLargeDirectFrame(target,writer.wireLength(),writer);
""","""        final int limit=recipientId==senderId?Integer.MAX_VALUE:pendingLimit;
        final int exactWriterBytes=writer==null?-1:writer.wireLength();
        if(writer!=null && exactWriterBytes>16384)
            return CSharedBroadcast.sendLargeDirectFrame(target,exactWriterBytes,writer);
        // When the receiver's pending-byte cap is already full, never allocate
        // a Native *shared* source merely to use the direct FIFO alternative.
        // This branch only applies when writer provided an exact frame length.
        if(target.pendingSendBytes()>=limit)
            return false;
""")
    s=rep(s,"""        final int limit=recipientId==senderId?Integer.MAX_VALUE:pendingLimit;
        final int length = source.wire.remaining();""",
        "        final int length = source.wire.remaining();")
    assert "private final byte[] wire" not in s
    assert "createConstBuffer(wire)" not in s
    assert "CAsyncSendConstBuffer(byte[]" not in s
    assert "target.sendFrozenWire(wire)" not in s
    return s
def convert_es(s):
    s=rep(s,"""    public CAsyncSendConstBufferExceptSelf(byte[] frame, int senderId, int pendingLimit) {
        super(frame, senderId, pendingLimit);
    }
""","")
    anchor="    private CAsyncSendConstBufferExceptSelf(\n"
    assert s.count(anchor)==1
    native="""    /** Source-only constructor: the original payload is assembled in Native memory. */
    public CAsyncSendConstBufferExceptSelf(CSharedBroadcast.NativeWireWriter writer,
            int senderId,int pendingLimit) {
        super(writer,senderId,pendingLimit);
    }

"""
    s=s.replace(anchor,native+anchor,1)
    assert "CAsyncSendConstBufferExceptSelf(byte[]" not in s
    return s

def load():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos=z.infolist();files={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    assert len(files)==1734 and sum(n.endswith(".java") for n in files)==1710
    return infos,files

def change(files):
    for path,fn in ((AS,convert_as),(ES,convert_es)):
        raw=files[path].decode("utf-8")
        crlf="\r\n" in raw
        updated=fn(raw.replace("\r\n","\n"))
        assert updated!=raw.replace("\r\n","\n")
        files[path]=(updated.replace("\n","\r\n") if crlf else updated).encode("utf-8")

def prepare(files):
    for n,data in files.items():
        if n.startswith("native/") or (n.startswith("src/") and n.endswith(".java")):
            p=WORK/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    native=ROOT/"patches/ctransport_core_20261112/native"
    for p in native.iterdir():
        if p.is_file() and not (WORK/"native"/p.name).exists():shutil.copy2(p,WORK/"native"/p.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for name in ("build","logs"):(WORK/name).mkdir(exist_ok=True)
    print("PHASE86_NATIVE_ONLY_SOURCE_CONSTRUCTORS_PREPARED",CHANGED,flush=True)

def package(infos,old,new):
    assert (WORK/"build/CTransport.dll").read_bytes()==old["CTransport.dll"]
    for n in CHANGED:new[n]=(WORK/n).read_bytes()
    delta={n for n in old if old[n]!=new[n]}
    assert delta==set(CHANGED),delta
    assert len(new)==1734 and sum(n.endswith(".java") for n in new)==1710
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for i in infos:z.writestr(i,b"" if i.is_dir() else new[i.filename])
    with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and len(z.namelist())==1734
    report={"phase":86,"baseline_sha256":PIN,
       "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
       "dll_sha256":hashlib.sha256(old["CTransport.dll"]).hexdigest(),
       "changed":list(CHANGED),"java_count":1710,"zip_entries":1734,
       "old_byte_frame_constructors_removed":True,
       "no_legacy_array_source_branch":True,
       "new_classes_workers_queues":0,"5000_ingame_soak_verified":False}
    (WORK/"audit86.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
    print("PHASE86_VERIFIED_NATIVE_ONLY_FULL_SRC",report["full_src_sha256"],flush=True)

if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("usage: migrate.py WORK prepare|package")
    infos,old=load();new=old.copy();change(new)
    if sys.argv[2]=="prepare":prepare(new)
    else:package(infos,old,new)

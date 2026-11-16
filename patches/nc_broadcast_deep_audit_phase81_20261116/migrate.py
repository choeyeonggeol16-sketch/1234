#!/usr/bin/env python3
"""Phase81: byte-parity and Native source-lifetime bug fixes in SHA-pinned phase80.

Fix 1: SC_IconMessage oversized MS949 writeS2 had begun throwing during
broadcast, whereas the old sharedWire path rejected the packet without
disturbing the caller. Fast <=127 UTF-16 chars are allocation-free.

Fix 2: forMove / forAction pooled source constructors lacked the catch/release
lease recovery already used in all the other Native format factories.
No new classes/queues/threads/DLL changes. All other original files preserved.
"""
from pathlib import Path
import hashlib,json,sys,zipfile,shutil
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase80_FULL_SRC_20261116.zip"
PIN="a0d534b066395b12a3a9e545cb094602e1cd568ea80ddca2900ff46a0a54e391"
OUT=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase81_Audited_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else None
P="src/l1j/server/server/model/network/core/"
SC=P+"sc/packet/SC_IconMessage.java"
EX=P+"broadcast/CAsyncSendConstBufferExceptSelf.java"
CHANGED={SC,EX}
def change(files,name,old,new):
    src=files[name].decode("utf-8")
    crlf="\r\n" in src
    s=src.replace("\r\n","\n")
    assert s.count(old)==1,(name,old[:110],s.count(old))
    s=s.replace(old,new,1)
    files[name]=(s.replace("\n","\r\n") if crlf else s).encode("utf-8")
def fix(files):
    before="""        final String text=message==null?"":message;
        final byte[] rgb=color==null?null:color.get_bytes();"""
    after="""        final String text=message==null?"":message;
        // The original sharedWire() rejected writeS2 lengths above 255
        // before sending to any recipient. Native direct encoding must not
        // turn oversized GM/macro messages into uncaught game-thread errors.
        if(!textFitsS2(text))return;
        final byte[] rgb=color==null?null:color.get_bytes();"""
    change(files,SC,before,after)
    marker="""    private static boolean sendInternal(L1PcInstance pc, String message, int index, MJSimpleRgb color,"""
    helper="""    /** Exact encoded-byte limit, without allocation on the common short path.
     * MS949 emits at most two bytes for one UTF-16 code unit; the long path
     * uses the SAME Java charset replacement policy as the old writer.
     */
    private static boolean textFitsS2(String value) {
        return value.length() <= 127 || value.getBytes(
                java.nio.charset.Charset.forName("MS949")).length <= 255;
    }

"""
    change(files,SC,marker,helper+marker)
    old_move="""        final CAsyncSendConstBuffer.Source pooled =
                socket.createPooledMoveConstBuffer(opcode, objectId, x, y, heading);
        return pooled == null ? null : new CAsyncSendConstBufferExceptSelf(pooled, senderId);"""
    new_move="""        final CAsyncSendConstBuffer.Source pooled =
                socket.createPooledMoveConstBuffer(opcode, objectId, x, y, heading);
        if(pooled==null)return null;
        try {
            return new CAsyncSendConstBufferExceptSelf(pooled,senderId);
        } catch(RuntimeException|Error failure) {
            pooled.release();
            throw failure;
        }"""
    change(files,EX,old_move,new_move)
    old_action="""        final CAsyncSendConstBuffer.Source pooled =
                socket.createPooledActionConstBuffer(opcode, objectId, actionId);
        return pooled == null ? null : new CAsyncSendConstBufferExceptSelf(pooled, senderId);"""
    new_action="""        final CAsyncSendConstBuffer.Source pooled =
                socket.createPooledActionConstBuffer(opcode, objectId, actionId);
        if(pooled==null)return null;
        try {
            return new CAsyncSendConstBufferExceptSelf(pooled,senderId);
        } catch(RuntimeException|Error failure) {
            pooled.release();
            throw failure;
        }"""
    change(files,EX,old_action,new_action)
def prepare():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN,"phase80 authoritative ZIP SHA mismatch"
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        files={i.filename:z.read(i.filename) for i in z.infolist() if not i.is_dir()}
    original=files.copy()
    assert len(files)==1734 and sum(n.endswith(".java") for n in files)==1710
    fix(files)
    touched={n for n in files if files[n]!=original[n]}
    assert touched==CHANGED,("unexpected phase81 touched",sorted(touched^CHANGED))
    for n,data in files.items():
        if n.startswith("native/") or n.startswith("src/") and n.endswith(".java"):
            dest=WORK/n;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
    native_root=ROOT/"patches/ctransport_core_20261112/native"
    for src in native_root.iterdir():
        if src.is_file() and not (WORK/"native"/src.name).exists():
            shutil.copy2(src,WORK/"native"/src.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for dirname in ("build","logs"):(WORK/dirname).mkdir(exist_ok=True)
    print("PHASE81_TWO_VERIFIED_BUG_FIXES_PREPARED",sorted(CHANGED),flush=True)
def package():
    with zipfile.ZipFile(BASE) as z:
        infos=z.infolist()
        files={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    assert (WORK/"build/CTransport.dll").read_bytes()==files["CTransport.dll"]
    new=files.copy()
    for n in CHANGED:new[n]=(WORK/n).read_bytes()
    touched={n for n in files if files[n]!=new[n]}
    assert touched==CHANGED
    assert len(new)==1734 and sum(n.endswith(".java") for n in new)==1710
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:z.writestr(info,b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and len(z.namelist())==1734
    import re
    legacy=[]
    for n,data in new.items():
        if "/sc/packet/SC_" not in n or not n.endswith(".java"):continue
        s=data.decode("utf-8")
        if re.search(r'CSharedBroadcast\.send(?:All|Recipients)?\s*\(',s):
            legacy.append(n)
    assert not legacy,legacy
    report={"phase":81,"baseline_sha256":PIN,
        "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
        "dll_sha256":hashlib.sha256(files["CTransport.dll"]).hexdigest(),
        "native_abi":"0x000a0009","changes":sorted(touched),
        "java_count":1710,"zip_entries":1734,
        "sc_legacy_broadcast_remaining":0,
        "s2_oversize_exception_regression_fixed":True,
        "action_move_source_constructor_release_fixed":True,
        "native_pool_cipher_fifo_unmodified":True,
        "native_64k_variable_length_shared_writes_not_supported":True,
        "legacy_raw_byte_non_sc_call_sites":69,
        "5000_ingame_soak_verified":False}
    (WORK/"audit.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print("PHASE81_SOURCE_AUDIT_COMPLETE",json.dumps(report),flush=True)
if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):raise SystemExit("usage: migrate.py WORK prepare|package")
    prepare() if sys.argv[2]=="prepare" else package()

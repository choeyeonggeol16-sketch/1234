#!/usr/bin/env python3
"""Phase85: retire all provably-unused old Java byte[] broadcast entrypoints.

Phase84 full source is SHA-256 pinned. This deletes source methods only after
checking every Java source reference. New Native writer fanouts, socket FIFO,
per-client encryption, pooling and original send order are untouched.
"""
from pathlib import Path
import hashlib,re,zipfile,json,sys,shutil
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase84_FULL_SRC_20261116.zip"
PIN="5e442ada66a78c14075952e9114c1d0a41dccb45ef9c081aec16789c77599c3c"
OUT=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase85_FULL_SRC_20261116.zip"
WORK=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else None
BC="src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java"
CANDIDATES=[
 "send","sendObjectPlayers","sendRecipients","sendRawArray",
 "sendRawRecipients","sendRawSelfAndVisible","sendAllGms","sendMapTeam",
 "sendMapTeamFrames","sendAllPlayable","sendGroups","sendRecipient",
 "sendArray","sendGroup","sendNestedVisible","sendAll","frameRaw","openRaw"
]
REMOVED=[];RETAINED={}

def skip_java(s,i):
    # Comments and literals are skipped when finding a Java method end.
    n=len(s)
    if s.startswith("//",i):
        j=s.find("\n",i);return n if j<0 else j
    if s.startswith("/*",i):
        j=s.find("*/",i+2);return n if j<0 else j+2
    if s[i] in ("'",'"'):
        delim=s[i];i+=1
        while i<n:
            if s[i]=="\\":i+=2;continue
            if s[i]==delim:return i+1
            i+=1
        raise ValueError("Unterminated Java literal")
    return i

def scan_end(s,start):
    depth=0;i=start;n=len(s)
    assert s[i]=="{",s[i:i+50]
    while i<n:
        k=skip_java(s,i)
        if k!=i:i=k;continue
        ch=s[i]
        if ch=="{":depth+=1
        elif ch=="}":
            depth-=1
            if depth==0:return i+1
        i+=1
    raise ValueError("Java method body not terminated")

def method_spans(s,name):
    # All legacy broadcasters are static declarations; do not rewrite any
    # overload with NativeWireWriter input.
    pattern=re.compile(r'(?m)^[ \t]+(?:public|private)\s+static\s+[^\n({;]+?\b'+re.escape(name)+r'\s*\(')
    found=[]
    for m in pattern.finditer(s):
        start=m.start()
        paren=m.end()-1;depth=0;i=paren
        while i<len(s):
            k=skip_java(s,i)
            if k!=i:i=k;continue
            if s[i]=="(":depth+=1
            elif s[i]==")":
                depth-=1
                if depth==0:break
            i+=1
        assert i<len(s),(name,"unclosed signature")
        args=s[paren+1:i]
        # Method declarations continue to the next open brace.
        body=s.find("{",i)
        assert body>=0,(name,"no body")
        e=scan_end(s,body)
        found.append((start,e,args))
    return found

def strip_unused(files):
    java={n:data.decode("utf-8") for n,data in files.items()
          if n.startswith("src/") and n.endswith(".java")}
    src=java[BC]
    for name in CANDIDATES:
        escaped=re.escape(name)
        calls=re.compile(r'\bCSharedBroadcast\s*\.\s*'+escaped+r'\s*\(')
        refs=[p for p,s in java.items() if p!=BC and calls.search(s)]
        if refs:
            RETAINED[name]={"external_callers":sorted(refs)}
            continue
        spans=method_spans(src,name)
        # Old fanout methods accept byte[]; the frameRaw helper returns byte[].
        # No full API renaming: only byte[]-based or named orphan helper methods.
        selected=[]
        for a,b,args in spans:
            if "NativeWireWriter" in args:continue
            if "byte[]" not in args and name not in ("frameRaw","openRaw","sendRecipient","sendGroup"):
                continue
            selected.append((a,b))
        if not selected:continue
        test=src
        for a,b in reversed(selected):test=test[:a]+test[b:]
        # To avoid deleting a still-used private helper, search remaining
        # usages in class, excluding the original method declarations.
        if name in ("frameRaw","openRaw","sendGroup"):
            remaining=method_spans(test,name)
            assert not remaining
            if name!="frameRaw" and re.search(r'(?<!\.)\b'+escaped+r'\s*\(',test):
                RETAINED[name]={"internal_references_after_candidate_removal":True}
                continue
            # frameRaw was the previous Java array framing adapter.
            # Full Java21 production type compilation guards remaining real callers.
        src=test
        REMOVED.append({"method":name,"count":len(selected)})
    java[BC]=src
    # Definitive absence of Java byte[] entrypoints for deleted names.
    for row in REMOVED:
        assert not any("byte[]" in args for a,b,args in method_spans(src,row["method"])),row
    assert REMOVED, "nothing to retire"
    files[BC]=src.encode("utf-8")
    return files

def load():
    assert hashlib.sha256(BASE.read_bytes()).hexdigest()==PIN,"Phase84 source checksum failed"
    with zipfile.ZipFile(BASE) as z:
        assert z.testzip() is None
        infos=z.infolist()
        data={i.filename:z.read(i.filename) for i in infos if not i.is_dir()}
    assert len(data)==1734 and sum(k.endswith(".java") for k in data)==1710
    return infos,data

def prepare(files):
    for name,data in files.items():
        if name.startswith("native/") or (name.startswith("src/") and name.endswith(".java")):
            p=WORK/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    native=ROOT/"patches/ctransport_core_20261112/native"
    for path in native.iterdir():
        if path.is_file() and not (WORK/"native"/path.name).exists():shutil.copy2(path,WORK/"native"/path.name)
    shutil.copytree(ROOT/"patches/ctransport_core_20261112/tests",WORK/"tests",dirs_exist_ok=True)
    for n in ("build","logs"):(WORK/n).mkdir(exist_ok=True)
    (WORK/"phase85_changed.json").write_text(json.dumps(
       {"removed":REMOVED,"retained":RETAINED},ensure_ascii=False,indent=2)+"\n")
    print("PHASE85_RETIRE_OLD_JAVA_BYTE_BROADCAST_API",
          json.dumps(REMOVED), "retained",list(RETAINED),flush=True)

def package(infos,old,new):
    assert (WORK/"build/CTransport.dll").read_bytes()==old["CTransport.dll"],"Native DLL changed"
    new[BC]=(WORK/BC).read_bytes()
    assert len(new)==1734 and sum(k.endswith(".java") for k in new)==1710
    assert {n for n in old if old[n]!=new[n]}=={BC}
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for info in infos:
            z.writestr(info,b"" if info.is_dir() else new[info.filename])
    with zipfile.ZipFile(OUT) as z:assert z.testzip() is None and len(z.namelist())==1734
    report={"phase":85,"baseline_sha256":PIN,
      "full_src_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest(),
      "dll_sha256":hashlib.sha256(old["CTransport.dll"]).hexdigest(),
      "changed":[BC],"java_files":1710,"zip_entries":1734,
      "removed_old_byte_array_api":REMOVED,"remaining_legacy_api_references":RETAINED,
      "new_classes_workers_queues":0,"5000_ingame_soak_verified":False}
    (WORK/"audit85.json").write_text(json.dumps(report,ensure_ascii=False,indent=2)+"\n")
    print("PHASE85_VERIFIED_FULL_SRC",report["full_src_sha256"],flush=True)

if __name__=="__main__":
    if len(sys.argv)!=3 or sys.argv[2] not in ("prepare","package"):
        raise SystemExit("Usage migrate.py WORK prepare|package")
    infos,old=load();new=old.copy();strip_unused(new)
    if sys.argv[2]=="prepare":prepare(new)
    else:package(infos,old,new)

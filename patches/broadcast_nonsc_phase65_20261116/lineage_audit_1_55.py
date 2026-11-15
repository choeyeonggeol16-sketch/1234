#!/usr/bin/env python3
"""Independent phase0→phase55 historical ZIP/lineage/CRC audit.

Does not trust migration scripts' claims: verifies downloaded, committed full
archives, each Java source delta, every zip member CRC, any resource changes,
native DLL identity, the final branch relationship, and complete phase coverage.
"""
from __future__ import annotations
import collections,hashlib,json,pathlib,re,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
OUT=ROOT/"reports/broadcast_nonsc_phase65_20261116"
OUT.mkdir(parents=True,exist_ok=True)
def archive(n):
    if n==0:return ROOT/"downloads/CTransport_SharedBroadcast_NoLegacy_FULL_SRC_20261115.zip"
    if n==1:return ROOT/"downloads/RKern_Broadcast_FullAudit_Central2_FULL_SRC_20261115.zip"
    return ROOT/f"downloads/RKern_Broadcast_NonSC_Phase{n}_FULL_SRC_202611{'16' if n>=50 else '15'}.zip"
def digest(b):return hashlib.sha256(b).hexdigest()
def blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def index(z):
    m={}
    for x in z.infolist():
        if x.is_dir():continue
        assert x.filename not in m,("duplicate ZIP entry",x.filename)
        assert not (x.filename.startswith("/") or ".." in pathlib.PurePosixPath(x.filename).parts),x.filename
        m[x.filename]=(x.CRC,x.file_size)
    return m

zips=[archive(i) for i in range(56)]
for a in zips: assert a.exists(),("missing historical ZIP",str(a))
rows=[];prior=None;prior_index=None;changeset=set();previous_native=None
all_binaries=set();rare_non_java=[]
for i,a in enumerate(zips):
    with zipfile.ZipFile(a) as curr:
        idx=index(curr)
        assert curr.testzip() is None,("CRC failure",i)
        assert sum(k.endswith(".java") for k in idx) in (1710,1711),(i,"java",sum(k.endswith(".java") for k in idx))
        assert "CTransport.dll" in idx,(i,"native DLL missing")
        nsha=digest(curr.read("CTransport.dll"))
        if previous_native is None:previous_native=nsha
        assert nsha==previous_native,(i,"native DLL unexpectedly changed")
        if i==0:
            row={"phase":0,"source":str(a.relative_to(ROOT)),"zip_sha256":digest(a.read_bytes()),
                 "git_blob_sha1":blob(a.read_bytes()),"files":len(idx),
                 "java":sum(k.endswith(".java") for k in idx),"native_sha256":nsha}
        else:
            assert prior_index is not None
            removed=sorted(prior_index.keys()-idx.keys())
            added=sorted(idx.keys()-prior_index.keys())
            common=prior_index.keys()&idx.keys()
            likely=[k for k in common if prior_index[k]!=idx[k]]
            # verify byte changes, not just ZIP metadata
            with zipfile.ZipFile(prior) as old:
                changed=sorted(k for k in likely if old.read(k)!=curr.read(k))
                checksum_collisions=[k for k in common if prior_index[k]==idx[k] and k in (
                    "CTransport.dll","src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java")]
                for k in checksum_collisions:
                    assert old.read(k)==curr.read(k),(i,"identical crc yet changed",k)
            nonjava=[k for k in changed+added+removed if not k.endswith(".java")]
            if nonjava:rare_non_java.append({"phase":i,"files":nonjava})
            changeset.update(changed+added+removed)
            row={
                "phase":i,"source":str(a.relative_to(ROOT)),
                "zip_sha256":digest(a.read_bytes()),"git_blob_sha1":blob(a.read_bytes()),
                "files":len(idx),"java":sum(k.endswith(".java") for k in idx),
                "modified":changed,"added":added,"removed":removed,
                "changed_java":len([k for k in changed+added+removed if k.endswith(".java")]),
                "nonjava_deltas":nonjava,"native_sha256":nsha
            }
        rows.append(row)
    prior=a;prior_index=idx

assert len(rows)==56
final=archive(55);latest=ROOT/"downloads/RKern_Broadcast_NonSC_Phase64_FULL_SRC_20261116.zip"
with zipfile.ZipFile(final) as z55,zipfile.ZipFile(latest) as z64:
    assert z55.testzip() is None and z64.testzip() is None
    x=index(z55);y=index(z64)
    assert "CTransport.dll" in x and z55.read("CTransport.dll")==z64.read("CTransport.dll")
    cumulative_changed_to64=sorted(k for k in x.keys()&y.keys() if
        x[k]!=y[k] and z55.read(k)!=z64.read(k))
    assert all(k.endswith(".java") for k in cumulative_changed_to64)
    assert not (set(x.keys())^set(y.keys()))
report={
 "audited_phases":list(range(1,56)),
 "phase_count":55,
 "all_zip_crc_ok":True,
 "every_step_compared_against_immediate_predecessor":True,
 "native_dll_unchanged_across_all_phases":True,
 "native_sha256":previous_native,
 "per_phase":rows,
 "unique_paths_modified_across_phase1_55":len(changeset),
 "files_changed_at_least_once":sorted(changeset),
 "non_java_deltas":rare_non_java,
 "phase55_to64_content_changes":cumulative_changed_to64,
 "limits":"ZIP hashes and byte deltas do not prove packet, gameplay, ordering or thread safety."
}
(OUT/"PHASE1_55_LINEAGE.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n",encoding="utf8")
(OUT/"PHASE1_55_DELTA.txt").write_text("\n".join(
    f"PHASE{i}: java_count={r['java']} modified={len(r.get('modified',[]))} "
    f"added={len(r.get('added',[]))} removed={len(r.get('removed',[]))}\n"+
    "\n".join("  "+n for n in r.get("modified",[])+r.get("added",[])+r.get("removed",[]))
    for i,r in enumerate(rows) if i>0)+"\n",encoding="utf8")
print("PHASE65_LINEAGE_AUDIT_PASS",json.dumps({
 "phase_count":55,"unique_files":len(changeset),"nonjava_stages":rare_non_java,
 "native":previous_native,"final_phase55_java":rows[-1]["java"],
 "phase55_to64_changes":len(cumulative_changed_to64),
 "per_phase_deltas":[f"{r['phase']}:{r.get('changed_java',0)}" for r in rows[1:]]
},ensure_ascii=False))

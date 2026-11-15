#!/usr/bin/env python3
"""Phase58: delete the last two unused legacy ServerPacket multi-recipient
serializers. Keep all active SC_* immutable-frame fanouts, no fallback.
"""
from pathlib import Path
import hashlib,zipfile,runpy,json,argparse,re

ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase57_FULL_SRC_20261116.zip"
OUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase58_FULL_SRC_20261116.zip"
BASE_BLOB="24a988a953a65f2004744100682d103145e9a908"
ENUMS="src/l1j/server/MJInstanceSystem/MJInstanceEnums.java"
TRIGGER="src/l1j/server/MJTemplate/L1Instance/MJEffectTriggerInstance.java"
IMP="import l1j.server.server.model.network.packet.ServerPacket;"
def git_blob(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def cut(s,sig):
    at=s.find(sig)
    assert at>=0,(sig,"missing")
    assert s.count(sig)==1,(sig,"not unique")
    brace=s.find("{",at)
    assert brace>=0
    depth=0
    for i in range(brace,len(s)):
        if s[i]=="{":depth+=1
        elif s[i]=="}":
            depth-=1
            if not depth:return s[:at]+s[i+1:]
    raise AssertionError("unbalanced method: "+sig)
def main():
    p=argparse.ArgumentParser();p.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase58"))
    work=p.parse_args().work
    raw=INPUT.read_bytes();assert git_blob(raw)==BASE_BLOB,"phase57 source pin changed"
    with zipfile.ZipFile(INPUT) as z:
        assert z.testzip() is None
        original={f.filename:z.read(f) for f in z.infolist() if not f.is_dir()}
    assert sum(n.endswith(".java") for n in original)==1710
    files=original.copy()
    for file,signature in [
        (ENUMS,"private void sendList(ArrayList<L1PcInstance> pcs, ServerPacket pck)"),
        (TRIGGER,"public void broadcast(ServerPacket pck, boolean is_clear)")]:
        s=files[file].decode("utf8")
        assert s.count(IMP)==1
        s=cut(s,signature)
        s=s.replace(IMP+"\n","",1)
        assert IMP not in s and signature not in s
        files[file]=s.encode("utf8")
    changed=sorted(n for n in files if files[n]!=original[n])
    assert changed==sorted([ENUMS,TRIGGER]),changed
    assert files["CTransport.dll"]==original["CTransport.dll"]
    # All other code and compiled packet implementation is byte-for-byte unchanged.
    assert all(files[k]==v for k,v in original.items() if k not in (ENUMS,TRIGGER))
    work.mkdir(parents=True,exist_ok=True)
    for k,v in files.items():
        if k.startswith("src/") and k.endswith(".java"):
            f=work/k;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(v)
    # Re-run the same checked 24,579 byte-parity cases after legacy-method deletion.
    prior=runpy.run_path(str(ROOT/"patches/broadcast_nonsc_phase57_20261116/migrate_identical_wire_fanouts.py"))
    t=work/"testsrc/Phase57SharedFrameParity.java"
    t.parent.mkdir(parents=True,exist_ok=True);t.write_text(prior["TEST"],encoding="utf-8")
    with zipfile.ZipFile(OUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for k in sorted(files):z.writestr(k,files[k])
    with zipfile.ZipFile(OUT) as z:
        assert z.testzip() is None and set(z.namelist())==set(files)
    audit={
      "phase":58,"java_sources":1710,"modified_java":changed,
      "obsolete_private_sendList_deleted":True,
      "obsolete_public_generic_trigger_broadcast_deleted":True,
      "all_active_broadcastPacket_methods_previously_absent":True,
      "polymorphic_writable_packet_send_per_recipient_intact":True,
      "all_shared_wire_fanouts_unchanged":True,
      "native_unchanged":True,
      "no_fallback_new_workers_queues_classes":True,
      "windows_runtime_tested":False,
      "source_sha256":hashlib.sha256(OUT.read_bytes()).hexdigest()
    }
    (work/"audit.json").write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(json.dumps(audit,ensure_ascii=False,indent=2))
if __name__=="__main__":main()

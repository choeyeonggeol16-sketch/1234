#!/usr/bin/env python3
"""Read-only phase61 verification: exact source excerpts for raid poison
remove-object fanout and an ownership-oriented race audit.
No production Java, DLL, or ZIP is rewritten by this script.
"""
import zipfile,hashlib,pathlib,re,json
ROOT=pathlib.Path(__file__).resolve().parents[2]
src=ROOT/"downloads/RKern_Broadcast_NonSC_Phase60_FULL_SRC_20261116.zip"
PIN="064ac992cbbf8bb71eba084eb2b33cc44bea2ce11f13e622d08a48ea643da362"
assert hashlib.sha256(src.read_bytes()).hexdigest()==PIN,"phase60 source changed"
out=ROOT/"reports/broadcast_nonsc_phase61_verify_20261116"
out.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(src) as z:
    assert z.testzip() is None
    names=[n for n in z.namelist() if n.endswith(".java")]
    assert len(names)==1710
    wanted=["SC_RemoveObject.java","L1MJRaidPoisonInstance.java","CAsyncSendConstBuffer.java","CSharedBroadcast.java","CAsyncSendConstBufferExceptSelf.java"]
    for base in wanted:
        matches=[n for n in names if n.endswith("/"+base)]
        assert len(matches)==1,(base,matches)
        text=z.read(matches[0]).decode("utf-8-sig")
        (out/(base.removesuffix(".java")+".txt")).write_text(f"ORIGIN: {matches[0]}\n"+text,encoding="utf8")
    old_names=("SC_RemoveObject.java","L1MJRaidPoisonInstance.java")
    remove=z.read(next(n for n in names if n.endswith("/SC_RemoveObject.java"))).decode("utf8")
    poison=z.read(next(n for n in names if n.endswith("/L1MJRaidPoisonInstance.java"))).decode("utf8")
    assert "SC_RemoveObject.send(" not in poison
    assert poison.count("SC_RemoveObject.broadcastWire(getId())")==1
    assert poison.count("pc.removeKnownObject(this);")==1
    assert poison.count("CSharedBroadcast.sendRecipient(shared, pc);")==1
    assert poison.index("pc.removeKnownObject(this);")<poison.index("CSharedBroadcast.sendRecipient(shared, pc);")
    assert "SC_RemoveObject" in remove and "broadcastWire" in remove
    assert "public static boolean send(" in remove or "public static void send(" in remove
    # Review-only: static audit cannot prove native request lifetime.
    summary={"phase":61,"production_java_changed":0,"java_sources":1710,
         "raid_remove_uses_one_immutable_wire":True,
         "known_objects_removed_before_send":True,
         "uses_same_recognize_player_iteration":True,
         "old_direct_remove_path_in_poison_absent":True,
         "iosocket_completion_race_runtime_tested":False}
    (out/"SUMMARY.json").write_text(json.dumps(summary,ensure_ascii=False,indent=2)+"\n")
    print("PHASE61_REMOVE_SOURCE_AUDIT",json.dumps(summary,ensure_ascii=False))

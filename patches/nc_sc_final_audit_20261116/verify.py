#!/usr/bin/env python3
"""Final independent SC broadcast audit against immutable Phase74/Phase78 ZIPs."""
from pathlib import Path
import hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[2]
Z74=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase74_FULL_SRC_20261116.zip"
Z78=ROOT/"downloads/RKern_NC_SPacket_Assemble_Phase78_FULL_SRC_20261116.zip"
SHA74="4f5d0f8cdb1850693aa0b36ef1fba5c609292233f6c7558102f047287a24b3d8"
SHA78="07bfc9631c56a9ea4dc6a6bce60824b0ce2a9065b0b8c64ced4451863c1fb943"
assert hashlib.sha256(Z74.read_bytes()).hexdigest()==SHA74
assert hashlib.sha256(Z78.read_bytes()).hexdigest()==SHA78
with zipfile.ZipFile(Z74) as a,zipfile.ZipFile(Z78) as b:
    assert a.testzip() is None and b.testzip() is None
    old={n:a.read(n) for n in a.namelist() if not n.endswith("/")}
    new={n:b.read(n) for n in b.namelist() if not n.endswith("/")}
assert old.keys()==new.keys() and len(old)==1734
SC={n for n in new if "/sc/packet/SC_" in n and n.endswith(".java")}
assert len(SC)==195,len(SC)
before={}
after={}
for path in sorted(SC):
    for arr,dst in ((old,before),(new,after)):
        src=arr[path].decode("utf-8")
        calls=re.findall(r"CSharedBroadcast\.(\w+)\s*\(",src)
        if calls:dst[path]=sorted(set(calls))
oldlegacy={"send","sendRecipients","sendRawArray","sendArray",
    "sendAll","sendRawRecipients","sendObjectPlayers","sendRawSelfAndVisible"}
previous={p for p,methods in before.items() if set(methods)&oldlegacy}
remaining={p:methods for p,methods in after.items() if set(methods)&oldlegacy}
all_methods={}
for path,methods in after.items():
    for method in methods:all_methods[method]=all_methods.get(method,0)+1
expected_java={
    "SC_CharVisualUpdate","SC_Dexup","SC_Door","SC_Lawful","SC_Liquor",
    "SC_PinkName","SC_Resurrection","SC_SkillBrave","SC_SkillHaste",
    "SC_SkillIconAura","SC_SkillIconBlessOfEva","SC_SkillIconShield",
    "SC_SkillIconWindShackle","SC_SkillIconWisdomPotion","SC_Strup","SC_TrueTargetNew",
    "SC_ChangeShape","SC_Attack","SC_AttackCritical","SC_AttackMissPacket",
    "SC_AttackPacket","SC_AttackPacketForNpc","SC_AttackStatus",
    "SC_UseArrowSkill","SC_UseAttackSkill",
    "SC_ChangeName","SC_CharTitle","SC_DoActionShop","SC_ExplosionNoti",
    "SC_NpcChatPacket","SC_NpcShopAction","SC_PacketBox","SC_RangeSkill",
    "SC_ReturnedStat","SC_WorldPutObject",
}
changed={n for n in old if old[n]!=new[n]}
changed_sc={Path(n).stem for n in changed if n in SC}
assert changed_sc==expected_java,(sorted(changed_sc^expected_java))
# All other production files outside the known NC broadcast stack must match Phase74.
allowed={
    "CTransport.dll","native/CTransport.cpp","native/interop_abi.hpp",
    "src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java",
    "src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBuffer.java",
    "src/l1j/server/server/model/network/core/broadcast/CAsyncSendConstBufferExceptSelf.java",
    "src/l1j/server/server/model/network/core/connection/CUserSocket.java",
    "src/l1j/server/server/model/network/core/transport/CIOServer.java",
    "src/l1j/server/server/model/network/core/config/CIOConfig.java",
}
assert changed <= (SC|allowed),sorted(changed-(SC|allowed))
report={
    "from_phase":74,"through_phase":78,
    "phase74_sha256":SHA74,"phase78_sha256":SHA78,
    "total_SC_classes":len(SC),
    "sc_broadcast_classes_before":len(before),
    "sc_broadcast_classes_after":len(after),
    "original_legacy_path_classes":len(previous),
    "legacy_path_classes_remaining":len(remaining),
    "legacy_remaining":{Path(p).name:m for p,m in remaining.items()},
    "current_shared_helper_methods_by_sc_class_count":all_methods,
    "total_changed_SC_classes":len(changed_sc),
    "all_35_changed_verified":True,
    "unexpected_changes":[],"java_count":sum(n.endswith(".java") for n in new),
    "zip_entries":len(new),"zip_crc_pass":True,
    "source_candidate_classes":[Path(p).name for p in sorted(previous)],
    "changed_non_sc":sorted(changed-SC)
}
dst=ROOT/"reports/nc_sc_final_audit_20261116"
dst.mkdir(parents=True,exist_ok=True)
(dst/"audit.json").write_text(json.dumps(report,indent=2,ensure_ascii=False)+"\n")
print("FINAL_SC_AUDIT",json.dumps(report,ensure_ascii=False))

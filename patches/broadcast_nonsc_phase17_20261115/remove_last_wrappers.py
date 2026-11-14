#!/usr/bin/env python3
"""Phase17: remove final compatibility wrappers and route active callers
directly to one-frame native shared broadcasts. Preserve exact recipient
snapshots, original global/system/map messages and raw byte semantics.
No old serializer, no alternative backend, no new classes or queues.
"""
from pathlib import Path
import zipfile,hashlib,json,argparse
ROOT=Path(__file__).resolve().parents[2]
INPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase16_FULL_SRC_20261115.zip"
OUTPUT=ROOT/"downloads/RKern_Broadcast_NonSC_Phase17_FULL_SRC_20261115.zip"
PIN="a17b0d8771a9cab86e6fe1bde5941e3e10fe736d"
BASE="src/l1j/server/"
CHAR=BASE+"server/model/L1Character.java"
WORLD=BASE+"server/model/L1World.java"
CALC=BASE+"server/model/item/Exp/CalcExp.java"
TP=BASE+"server/model/item/teleport/L1Teleport.java"
ATTR=BASE+"server/model/network/core/cs/packet/CS_Attr.java"
COMBAT=BASE+"MJCombatSystem/MJCombatObserver.java"
ACCEPT=BASE+"MJTemplate/DateSchedulerModel/Acceptor/AcceptorToNpcListenerAdapter.java"
AREA=BASE+"MJCombatSystem/Games/MJBattleTournamentArea.java"
OBSERVER=BASE+"MJCombatSystem/Games/MJBattleTournamentObserver.java"
TARGETS={CHAR,WORLD,CALC,TP,ATTR,COMBAT,ACCEPT,AREA,OBSERVER}

def blobsha(b):return hashlib.sha1(b"blob "+str(len(b)).encode()+b"\0"+b).hexdigest()
def replace(src,old,new,count):
 assert src.count(old)==count,(old,src.count(old),count)
 return src.replace(old,new)
def remove_method(src,sig):
 assert src.count(sig)==1,(sig,src.count(sig))
 a=src.index(sig);b=src.index("{",a);level=0
 for i in range(b,len(src)):
  if src[i]=="{":level+=1
  elif src[i]=="}":
   level-=1
   if level==0:return src[:a]+src[i+1:]
 raise AssertionError("unterminated "+sig)
def patch(files):
 def edit(path,func):
  files[path]=func(files[path].decode("utf8")).encode("utf8")
 def calc(src):return replace(src,"pc.broadcastPacket(data);",
     "l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRawRecipients("
     "l1j.server.server.model.L1World.getInstance().getVisiblePlayer(pc),data);",1)
 edit(CALC,calc)
 def attr(src):return replace(src,"pc.broadcastPacket(data);",
     "l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRawRecipients("
     "l1j.server.server.model.L1World.getInstance().getVisiblePlayer(pc),data);",1)
 edit(ATTR,attr)
 edit(TP,lambda src:replace(src,
     "pc.broadcastPacket(SC_WORLD_PUT_OBJECT_NOTI.newInstance(petNpc).encode());",
     "l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRawRecipients("
     "l1j.server.server.model.L1World.getInstance().getVisiblePlayer(pc),"
     "SC_WORLD_PUT_OBJECT_NOTI.newInstance(petNpc).encode());",1))
 edit(COMBAT,lambda src:replace(src,
     "L1World.getInstance().broadcastPacketToAll(message);",
     "l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.broadcast(message);",1))
 edit(ACCEPT,lambda src:replace(src,
     "L1World.getInstance().broadcastPacketToAll(_opened_message);",
     "l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.broadcast(_opened_message);",1))
 edit(AREA,lambda src:replace(src,
     "L1World.getInstance().broadcast_map(_map_id, s);",
     "l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.broadcastRecipients("
     "L1World.getInstance().getVisiblePlayers(_map_id),s);",1))
 edit(OBSERVER,lambda src:replace(src,
     "L1World.getInstance().broadcast_map(_copyMapId, String.format(",
     "l1j.server.server.model.network.core.sc.packet.SC_SystemMessage.broadcastRecipients("
     "L1World.getInstance().getVisiblePlayers(_copyMapId), String.format(",2))
 edit(CHAR,lambda src:remove_method(src,"public void broadcastPacket(byte[] data) {"))
 def world(src):
  src=remove_method(src,"public void broadcastPacketToAll(String message) {")
  return remove_method(src,"public void broadcast_map(int map_id, String message){")
 edit(WORLD,world)
 # We retain no legacy methods in L1Character / L1World; only SC_* and raw shared.
 assert "broadcastPacket(" not in files[CHAR].decode("utf8")
 assert "broadcastPacketToAll(" not in files[WORLD].decode("utf8")
 assert "broadcast_map(" not in files[WORLD].decode("utf8")
def main():
 ap=argparse.ArgumentParser();ap.add_argument("--work",type=Path,default=Path("/tmp/broadcast-phase17"));args=ap.parse_args()
 raw=INPUT.read_bytes()
 assert blobsha(raw)==PIN,"phase16 pinned whole-source mismatch"
 with zipfile.ZipFile(INPUT) as z:
  assert z.testzip() is None
  files={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
 previous=files.copy()
 assert sum(n.endswith(".java") for n in files)==1711 and "CTransport.dll" in files
 patch(files)
 modified=sorted(p for p in files if files[p]!=previous[p])
 assert modified==sorted(TARGETS),modified
 assert files["CTransport.dll"]==previous["CTransport.dll"]
 assert set(files)==set(previous)
 args.work.mkdir(parents=True,exist_ok=True)
 for path,bytes_ in files.items():
  if path.startswith("src/") and path.endswith(".java"):
   target=args.work/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(bytes_)
 OUTPUT.parent.mkdir(parents=True,exist_ok=True)
 with zipfile.ZipFile(OUTPUT,"w",zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for path,bytes_ in sorted(files.items()):z.writestr(path,bytes_)
 with zipfile.ZipFile(OUTPUT) as z:
  assert z.testzip() is None and set(z.namelist())==set(files)
 report={"phase":17,"input_blob":PIN,
     "output_sha256":hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),
     "java_sources":1711,"modified_java":modified,
     "remaining_legacy_character_broadcast_API":0,
     "remaining_legacy_world_broadcast_API":0,
     "raw_packets_shared":3,"global_system_messages_shared":2,
     "map_system_messages_shared":3,
     "native_unchanged":True,"no_legacy_fallback":True,"windows_runtime_tested":False}
 (args.work/"audit.json").write_text(json.dumps(report,indent=2)+"\n")
 print(json.dumps(report,indent=2))
if __name__=="__main__":main()

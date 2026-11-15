#!/usr/bin/env python3
from pathlib import Path
import zipfile,re,hashlib
src=Path(__file__).resolve().parents[2]/"downloads/RKern_Broadcast_NonSC_Phase31_FULL_SRC_20261115.zip"
with zipfile.ZipFile(src) as z:
 assert z.testzip() is None
 files=z.namelist()
 for short,terms in [
 ("L1Clan.java",["public void broadcast(", "createOnlineMembers(", "getOnlineClanMember()"]),
 ("L1Party.java",["broadcastRootMent(", "broadcastRaw(", "sendPartyListTo("]),
 ("Announcecycle.java",["SC_SystemMessage.send(pc, msg)"]),
 ("StadiumObject.java",["SC_PacketBox.send(pc", "SC_SystemMessage.send(pc"]),
 ("Queen.java",["SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE"]),
 ("L1ChatParty.java",["SC_ServerMessage.send(member,"]),
 ("MJRaidMessage.java",["sendSystemMessage(ArrayList","sendGreenMessage(ArrayList"]),
 ("SC_ServerMessage.java",["sharedWire(int type,"]),
 ("SC_SystemMessage.java",["sharedWire("]),
 ("L1World.java",["broadcastServerMessage("]),
 ("CAsyncSendConstBuffer.java",["send(CUserSocket target"])
 ]:
  paths=[n for n in files if n.startswith("src/") and n.endswith("/"+short)]
  for p in paths:
   lines=z.read(p).decode("utf8").splitlines()
   hits=[i for i,l in enumerate(lines) if any(t in l for t in terms)]
   print("\n============ FILE",p,"HITS",len(hits))
   covered=set()
   for i in hits[:18]:
    lo=max(0,i-14);hi=min(len(lines),i+39)
    print("--- line",i+1)
    for j in range(lo,hi):
     if j not in covered:print(f"{j+1:4} {lines[j]}")
     covered.add(j)
 # Explicit call sites of legacy API
 rx=re.compile(r"\.broadcastRootMent\s*\(|\bclan\.broadcast\s*\(|\bgetClan\(\)\.broadcast\s*\(|\bbroadcastRootMent\s*\(")
 for p in files:
  if not p.startswith("src/") or not p.endswith(".java"):continue
  lines=z.read(p).decode("utf8").splitlines()
  for i,l in enumerate(lines):
   if rx.search(l) and not l.strip().startswith("//"):
    print("LEGACY_USE",p,i+1,l.strip())

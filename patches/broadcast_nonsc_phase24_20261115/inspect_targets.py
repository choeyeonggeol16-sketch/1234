from pathlib import Path
import zipfile
root=Path(__file__).resolve().parents[2]
with zipfile.ZipFile(root/"downloads/RKern_Broadcast_NonSC_Phase23_FULL_SRC_20261115.zip") as z:
    names=z.namelist()
    for short in ("BossRaid.java","Queen1.java","Azmodan.java","HadinTrap.java",
                  "Demon.java","MJRaidMessage.java","L1Racing.java"):
        matches=[n for n in names if n.startswith("src/") and n.endswith("/"+short)]
        for path in matches:
            lines=z.read(path).decode("utf8").splitlines()
            print("===TARGET",path,"LINES",len(lines))
            hits=[i for i,l in enumerate(lines) if "SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE" in l or "SC_SystemMessage.send(member" in l]
            for h in hits:
                print("---",h+1)
                for j in range(max(0,h-14),min(len(lines),h+14)):
                    print("%4d %s"%(j+1,lines[j]))

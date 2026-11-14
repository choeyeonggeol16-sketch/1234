#!/usr/bin/env python3
"""Snapshot exact source text for core broadcast dispatchers; the ZIP is canonical."""
import pathlib,zipfile,hashlib,re,collections,json
ROOT=pathlib.Path(__file__).resolve().parents[2]
SRC=ROOT/'downloads/CTransport_SharedBroadcast_NoLegacy_FULL_SRC_20261115.zip'
files=[
'src/l1j/server/server/model/L1Character.java',
'src/l1j/server/server/model/L1World.java',
'src/l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java',
'src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java',
'src/l1j/server/server/model/Instance/L1PcInstance.java',
'src/l1j/server/server/model/network/packet/ServerPacket.java',
'src/l1j/server/server/model/network/packet/WritablePacket.java',
]
def interesting(path,line):
    if path.endswith('L1Character.java'):
        return re.search('broadcastPacket|wideBroadcast|packet.send|sendPackets',line,re.I)
    if path.endswith('L1World.java'):
        return re.search('broadcast_map|broadcastPacketToAll|broadcastServerMessage|getVisiblePlayers',line,re.I)
    if path.endswith('SC_SystemMessage.java'):
        return re.search('broadcast|sharedWire|private static byte|send\\(',line)
    if path.endswith('L1PcInstance.java'):
        return re.search('sendPackets\\(|public boolean sendPackets|sendPacket\\(',line)
    return True
out=['# Core broadcast source snapshots from exact full SRC','','Compiled baseline packet-classes-55 no-legacy, SHA256 006f9c53143461f0bff5b5d0e6edd588644ef09efe4ca7b791a4724426d18efb','']
with zipfile.ZipFile(SRC) as z:
    for path in files:
        s=z.read(path).decode('utf8')
        ls=s.splitlines()
        spans=set()
        for i,line in enumerate(ls):
            if interesting(path,line):
                before=10 if 'Instance/L1PcInstance' in path else 5
                after=18 if 'L1Character' in path or 'L1World' in path else 12
                if path.endswith('ServerPacket.java') or path.endswith('WritablePacket.java'):before=0;after=1
                for j in range(max(0,i-before),min(len(ls),i+after)):spans.add(j)
        if path.endswith('CSharedBroadcast.java'):spans=set(range(len(ls)))
        if len(spans)>360:spans=set(sorted(spans)[:360])
        out+=['','## '+path,'', 'Total lines '+str(len(ls)), 'SHA256 '+hashlib.sha256(s.encode()).hexdigest(),'']
        prev=-2
        for j in sorted(spans):
            if j!=prev+1:out+=['----']
            out.append(str(j+1)+': '+ls[j])
            prev=j
        print('FOCUS',path,'lines',len(ls),'sampled',len(spans))
dest=ROOT/'reports/broadcast_full_audit_20261115/FOCUS_SOURCES.txt'
dest.parent.mkdir(parents=True,exist_ok=True)
dest.write_text('\n'.join(out)+'\n')

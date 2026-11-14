#!/usr/bin/env python3
"""Phase5: move team-restricted map chat to one shared immutable wire."""
import argparse,hashlib,json,pathlib,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[2]
INPUT=ROOT/'downloads/RKern_Broadcast_NonSC_Phase4_FULL_SRC_20261115.zip'
OUTPUT=ROOT/'downloads/RKern_Broadcast_NonSC_Phase5_FULL_SRC_20261115.zip'
EXPECTED_BLOB='47588e2fbef3990f042080efa9b4eabe090c93ae'
SHARED='src/l1j/server/server/model/network/core/broadcast/CSharedBroadcast.java'
TOURNAMENT='src/l1j/server/MJCombatSystem/Games/MJBattleTournamentArea.java'
SYSTEM='src/l1j/server/server/model/network/core/sc/packet/SC_SystemMessage.java'

ADD_METHOD='''
    /** An identical system-message frame for one combat team on a single map.
     * Retain the original getVisiblePlayers iteration and team visibility.
     */
    public static void sendMapTeam(int mapId, int teamId, byte[] wire) {
        if (wire == null) return;
        try (var shared=new CAsyncSendConstBuffer(
                wire,Integer.MIN_VALUE,Integer.MAX_VALUE)) {
            for (L1PcInstance pc : L1World.getInstance().getVisiblePlayers(mapId)) {
                if (pc==null || pc.get_current_combat_team_id()!=teamId) continue;
                final CIOClient client=pc.getNetConnection();
                if (client!=null && client.isConnected())
                    shared.send(client.getConnection(),pc.getId());
            }
        }
    }

'''
NEW_TOURNAMENT='''private void broadcast(String s){
        if (_round_id >= 4) {
            L1World.getInstance().broadcast_map(_map_id, s);
        } else {
            l1j.server.server.model.network.core.broadcast.CSharedBroadcast
                    .sendMapTeam(_map_id, _team_id, SC_SystemMessage.sharedWire(s));
        }
    }'''

def method_bounds(s,signature):
    if s.count(signature)!=1:raise AssertionError('Expected unique method '+signature)
    start=s.index(signature);br=s.find('{',start);depth=0
    for idx in range(br,len(s)):
        if s[idx]=='{':depth+=1
        elif s[idx]=='}':
            depth-=1
            if depth==0:return start,idx+1,s[start:idx+1]
    raise AssertionError('Method not balanced: '+signature)

def patch_shared(s):
    if s.count('    /** Global broadcast includes the sender and uses the normal socket FIFO. */')!=1:
        raise AssertionError('Unexpected CSharedBroadcast source')
    assert 'public static void sendAllGms(' in s
    assert 'public static void sendRawSelfAndVisible(' in s
    assert 'public static void sendMapTeam(' not in s
    return s.replace('    /** Global broadcast includes the sender and uses the normal socket FIFO. */',
                     ADD_METHOD+'    /** Global broadcast includes the sender and uses the normal socket FIFO. */',1)

def patch_tournament(s):
    a,b,old=method_bounds(s,'private void broadcast(String s)')
    for expected in ['_round_id >= 4', 'broadcast_map(_map_id, s)',
                     'getVisiblePlayers(_map_id)', 'get_current_combat_team_id() == _team_id',
                     'SC_SystemMessage.send(pc, s_message)']:
        assert expected in old,expected
    assert old.count('SC_SystemMessage.send(pc, s_message)')==1
    assert old.count('getVisiblePlayers(_map_id)')==1
    return s[:a]+NEW_TOURNAMENT+s[b:]

def main():
    p=argparse.ArgumentParser();p.add_argument('--input',type=pathlib.Path,default=INPUT)
    p.add_argument('--output',type=pathlib.Path,default=OUTPUT)
    p.add_argument('--work',type=pathlib.Path,default=pathlib.Path('/tmp/broadcast-nonsc-phase5'))
    args=p.parse_args()
    data=args.input.read_bytes()
    blob=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
    if blob!=EXPECTED_BLOB:raise ValueError('Not verified phase4 source')
    with zipfile.ZipFile(args.input) as z:
        assert z.testzip() is None
        content={f.filename:z.read(f) for f in z.infolist() if not f.is_dir()}
    before=dict(content)
    assert 'CTransport.dll' in content
    assert sum(x.endswith('.java') for x in content)==1711
    assert 'public static byte[] sharedWire(String message)' in content[SYSTEM].decode('utf8')
    for path,change in ((SHARED,patch_shared),(TOURNAMENT,patch_tournament)):
        s=content[path].decode('utf8');n=change(s)
        assert n!=s
        content[path]=n.encode('utf8')
    changed=sorted(k for k in content if content[k]!=before[k])
    assert changed==sorted([SHARED,TOURNAMENT])
    assert content['CTransport.dll']==before['CTransport.dll']
    args.work.mkdir(parents=True,exist_ok=True)
    for path,bytes_ in content.items():
        if path.startswith('src/') and path.endswith('.java'):
            dest=args.work/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(bytes_)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path,bytes_ in sorted(content.items()):z.writestr(path,bytes_)
    with zipfile.ZipFile(args.output) as z:assert z.testzip() is None
    report=dict(phase=5,input_git_blob_sha=blob,input_sha256=hashlib.sha256(data).hexdigest(),
                output_sha256=hashlib.sha256(args.output.read_bytes()).hexdigest(),
                modified_files=changed,full_java_sources=1711,team_filter_preserved=True,
                per_map_membership_preserved=True,global_round_unchanged=True,
                one_wire_per_team_notice=True,no_fallback=True,native_dll_unchanged=True,
                windows_runtime_tested=False)
    (args.work/'audit.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf8')
    print(json.dumps(report,indent=2,ensure_ascii=False))
if __name__=='__main__':main()

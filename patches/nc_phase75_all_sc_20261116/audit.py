#!/usr/bin/env python3
from pathlib import Path
import zipfile,hashlib,json,re
ROOT=Path(__file__).resolve().parents[2]
src=ROOT/'downloads/RKern_NC_SPacket_Assemble_Phase74_FULL_SRC_20261116.zip'
PIN='4f5d0f8cdb1850693aa0b36ef1fba5c609292233f6c7558102f047287a24b3d8'
assert hashlib.sha256(src.read_bytes()).hexdigest()==PIN
out=ROOT/'reports/nc_phase75_all_sc_inventory'
out.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(src) as z:
    assert z.testzip() is None
    names=[n for n in z.namelist() if n.endswith('.java') and '/sc/packet/SC_' in n]
    report={}
    fulltext=[]
    for n in sorted(names):
        s=z.read(n).decode('utf-8')
        lines=s.splitlines()
        highlights=[{'line':i+1,'code':line.strip()} for i,line in enumerate(lines)
            if any(token in line for token in ('CSharedBroadcast','sharedWire(', 'broadcast(', 'broadcastFast(',
             'sendAll', 'sendTo', 'ByteBuffer.allocate', 'writeC(', 'writeD(', 'writeH(', 'writeS(', 'writeByte(',
             'new CAsyncSend', 'sendFormatted(', 'CIOFrame frame'))]
        if 'CSharedBroadcast.send(' in s or 'sharedWire(' in s:
            fulltext.append(('----- '+n+' -----\n'+s+'\n'))
            (out/Path(n).name).write_bytes(z.read(n))
        report[n]={'length':len(s),'lines':len(lines),
            'old_shared_broadcast':s.count('CSharedBroadcast.send('),
            'new_native_broadcast':s.count('CSharedBroadcast.sendFormatted('),
            'other_native_broadcast':s.count('CSharedBroadcast.sendMove(')+s.count('CSharedBroadcast.sendAction('),
            'sharedWire':s.count('sharedWire('),'broadcast_method':s.count('public static void broadcast')+s.count('public static boolean broadcast'),
            'lines_excerpt':highlights[:75]}
    other=[n for n in z.namelist() if '/sc/packet/' in n and n.endswith('.java') and '/SC_' not in n]
    summary={'phase':75,'input_sha':PIN,'total_sc_files':len(names),'legacy_shared_sc':[n for n in names if report[n]['old_shared_broadcast']],'legacy_wire_sc':[n for n in names if report[n]['sharedWire']],'non_sc_packet_count':len(other),'rows':report}
    (out/'summary.json').write_text(json.dumps(summary,indent=2,ensure_ascii=False)+'\n')
    (out/'legacy_sources.txt').write_text('\n'.join(fulltext))
    print('PHASE75_SC_INVENTORY',len(names),'legacy_shared',len(summary['legacy_shared_sc']),'legacy_wire',len(summary['legacy_wire_sc']))

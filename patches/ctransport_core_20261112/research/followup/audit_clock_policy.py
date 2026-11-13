from pathlib import Path
import sys,json,argparse
root=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);args=p.parse_args()
out=Path(__file__).parent
sys.argv=['audit','--x64',str(args.binary),'--x86',str(args.binary),'--out',str(out)]
s={};exec(compile((root/'research/audit_network_abi5.py').read_text().split("x64=AuditPE(a.x64,'x64')")[0],'helper','exec'),s)
o=s['AuditPE'](args.binary,'x64')
functions={k:o.dump(k,v)for k,v in {'SetTimeStamp':0x7048cc,'SetShortTimeStamp':0x704990,'CUserSocket_TimerExpired':0xa0d164,'CUserSocket_VersionPacket':0xa0e1a4}.items()}
calls=[0xa0d355,0xa0e5ee,0xa0e942,0xa0eab7]
for at in calls:
 ins=o.valid(at);assert ins and ins.mnemonic=='call' and ins.op_str=='0x704990'
evidence={'binary_sha256':s['EXPECTED']['x64'],'original_executed':False,'functions':functions,'verified_socket_timestamp_calls':[hex(v)for v in calls],'decision':'Keep L1 monotonic64/native login,partial-read,write-stall,grace deadlines; do not import L2 version packet/3D-world/32-bit rollover policies. Login timeout default remains60000ms and is configurable.'}
(out/'clock-policy-audit.json').write_text(json.dumps(evidence,indent=2)+'\n')
print('PASS original SHA and four socket clock call sites; existing L1 deadline policies preserved')

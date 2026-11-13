from pathlib import Path
import argparse,json,sys,struct
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);a=p.parse_args();out=Path(__file__).resolve().parent
sys.argv=['audit','--x64',str(a.binary),'--x86',str(a.binary),'--out',str(out)]
s={};exec(compile((out/'audit_network_abi5.py').read_text().split("x64=AuditPE(a.x64,'x64')")[0],'helper','exec'),s);o=s['AuditPE'](a.binary,'x64')
labels=[]
for n in range(31):
 at=0xcc8430+n*8;target=struct.unpack('<Q',o.pe.get_data(at-o.base,8))[0]
 text=o.pe.get_data(target-o.base,256).decode('utf-16le').split('\0')[0];assert text.startswith('ORT_')
 labels.append({'index':n,'address':hex(target),'label':text})
header=(out.parents[1]/'native/reference_trace.hpp').read_text();assert all(x['label']in header for x in labels)
functions={}
for name,entry in {'retain':0x7d6d50,'release':0x7d6eb4,'socket_retain':0x9f773c,'socket_release':0x9f7770,'socket_report':0x9f7998,'verifier_ctor':0xa12674,'verifier_tick':0xa1291c,'timer_forward1':0x404c88,'timer_forward2':0x404c94,'verifier_forward_wrapper':0x7048c4,'verifier_forward_body':0x704190,'timer_add':0x926bf4,'timer_dispatch':0x926844,'allocator_backend':0x74e08c,'allocator_return':0x74e3bc,'worker_sync':0x705330}.items():
 info=o.dump(name,entry);info['caller_count']=len(info.pop('callers'));functions[name]=info
checks=[]
for at,mn,op in [(0x9f774b,'lock add','dword ptr [r9 + rcx + 0x110], 1'),(0xa12a59,'test','eax, eax'),(0x404c8a,'xchg','dword ptr [rcx + 0xc], eax'),(0x7d6ee4,'mov','eax, dword ptr [rcx + 0xc]')]:
 i=o.valid(at);assert(i.mnemonic,i.op_str)==(mn,op),(hex(at),i)
 checks.append({'address':hex(at),'bytes':i.bytes.hex(),'instruction':mn+' '+op})
result={'binary_sha256':s['EXPECTED']['x64'],'original_executed':False,'labels':labels,'verified_instructions':checks,'functions':functions,'scope':'Original reference slots,verifier,timer flag,and allocator-return/worker-sync boundary;not a full original C++ ABI recreation.'}
(out/'EVIDENCE.json').write_text(json.dumps(result,indent=2)+'\n');print('PASS31 exact ORT labels and4 original reference/timer instruction checks;allocator/sync functions dumped.')

from pathlib import Path
import sys,json,struct,re,argparse
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);p.add_argument('--source-root',type=Path,required=True);p.add_argument('--out',type=Path,required=True);cli=p.parse_args()
root=cli.source_root;out=cli.out;out.mkdir(parents=True,exist_ok=True);x64=cli.binary;x86=cli.binary
sys.argv=['audit','--x64',str(x64),'--x86',str(x86),'--out',str(out)];scope={};src=(root/'research/audit_network_abi5.py').read_text().split("x64=AuditPE(a.x64,'x64')")[0];exec(compile(src,'audit_helper','exec'),scope);obj=scope['AuditPE'](x64,'x64')
roots={'map27_insert':0xa08498,'map27_delete':0xa08660,'map27_exists':0x4f9a98,'map28_insert':0x97ec10,'map28_delete':0x8951bc,'map28_exists':0x4f9bc0,'thread15_entry':0x725e30,'thread15_condition':0x53a8fc,'thread15_mail':0x725488,'thread15_other':0x82c408,'thread15_other_a':0x82b04c,'thread15_other_b':0x82b0dc,'thread15_other_c':0x82b87c,'thread15_other_d':0x82b424,'thread15_other_e':0x82c0c0,'ip_limiter_dec':0xa0dc04,'mac_limiter_dec':0xa0ded4,'inc_user_count':0xa08940,'dec_user_count':0xa08a48,'perfmon_mail_loop':0x722abc,'perfmon_report_system':0x82b4e8,'perfmon_report_cpu':0x82b288,'socket_number_getter':0xa01798,'mac_login_packet':0xa10ad0,'mac_login_admission':0xa0fe54,'mac_socket_close':0xa12dec}
summary={}
for label,addr in roots.items():
 info=obj.dump(label,addr);summary[label]=info
 if label.startswith('map') or label.startswith('thread15_other') or label in ('thread15_condition','inc_user_count','dec_user_count'):
  for c in info['callers']:
   if c['function']:obj.dump('caller_'+label+'_'+c['function'][2:],int(c['function'],16))
(out/'functions.json').write_text(json.dumps(summary,indent=2)+'\n')
for label,info in summary.items():print(label,info['entry'],'calls',info['direct_calls'],'callers',[(x['at'],x['function'])for x in info['callers']])

checks=[(0xa01798,'mov','eax, dword ptr [rcx + 0x4e8]'),(0xa089e8,'call','qword ptr [rax + 0xc8]'),(0xa08a09,'call','0xa08498'),(0xa08b11,'call','0xa08660'),(0x982e7e,'mov','ebx, dword ptr [r12 + 0x7e4]'),(0x982f6f,'call','0x97ec10'),(0xa12503,'call','0x8951bc'),(0x82b066,'call','qword ptr [rip + 0x2f12b4]'),(0x725ea0,'call','0x725488'),(0x722ac7,'call','0x82c408'),(0x53a8fc,'mov','al, 1'),(0xa10c0a,'mov','qword ptr [rsi + 0xc88], r11'),(0xa10519,'mov','rdx, qword ptr [r12 + 0xc88]'),(0xa10528,'call','0xa04c3c'),(0xa12e87,'mov','rdx, qword ptr [rdi + 0xc88]'),(0xa12e95,'call','0xa0ded4')]
verified=[]
for address,mnemonic,operands in checks:
 ins=obj.valid(address);assert ins and(ins.mnemonic,ins.op_str)==(mnemonic,operands),(hex(address),ins)
 verified.append({'address':hex(address),'bytes':ins.bytes.hex(),'mnemonic':mnemonic,'operands':operands})
assert 'UserSocketMapPlaying' in(out/'caller_map27_exists_50ea98.asm.txt').read_text()
assert 'g_UserSocketMapEnterChar' in(out/'caller_map28_delete_a123e8.asm.txt').read_text()
assert 'CPerfMon::ReportUserCount' in(out/'thread15_other_e.asm.txt').read_text()
(out/'verified-instructions.json').write_text(json.dumps({'input_sha256':scope['EXPECTED']['x64'],'image_base':hex(obj.base),'original_executable_run':False,'checks':verified},indent=2)+'\n')
print('PASS',len(checks),'exact original instructions plus map/PerfMon names at validated references')

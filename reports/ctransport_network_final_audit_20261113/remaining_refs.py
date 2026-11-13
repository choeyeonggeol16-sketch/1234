"""Resolve remaining network signatures, including image-base-relative LEAs."""
from pathlib import Path
import argparse,json,struct,sys,re
from capstone.x86 import X86_OP_IMM,X86_OP_MEM,X86_OP_REG,X86_REG_RIP
p=argparse.ArgumentParser();p.add_argument('--binary',type=Path,required=True);p.add_argument('--census',type=Path);a=p.parse_args();out=Path(__file__).parent
sys.argv=['audit','--x64',str(a.binary),'--x86',str(a.binary),'--out',str(out)]
s={};exec(compile((out/'audit_network_abi5.py').read_text().split("x64=AuditPE(a.x64,'x64')")[0],'helper','exec'),s)
o=s['AuditPE'](a.binary,'x64')
old=json.loads((a.census or out/'PREVIOUS_CENSUS_INPUT.json').read_text());assert old['binary_sha256']==s['EXPECTED']['x64']
def key(name):
 if name.startswith('e')and name in ('eax','ebx','ecx','edx','esi','edi','ebp','esp'):return 'r'+name[1:]
 return name[:-1]if name.startswith('r')and name[-1:]in('d','w','b')else name
known={}
for f in old['functions']:
 for b,e in f['chunks']:
  for ins in o.instructions(int(b,16),int(e,16)):known[ins.address]=(int(f['entry'],16),ins)
def base_proof(entry,at,reg):
 chunk=o.chunk(at)
 if not chunk:return None
 chunks=sorted(o.groups[chunk[2]]);head=o.instructions(*chunks[0]);definition=None
 for ins in head:
  if ins.address>=at:break
  if ins.mnemonic.startswith('j')or ins.mnemonic.startswith('ret'):break
  if len(ins.operands)>=2 and ins.operands[0].type==X86_OP_REG and key(ins.reg_name(ins.operands[0].reg))==reg:
   op=ins.operands[1];value=None
   if ins.mnemonic=='lea'and op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP:value=ins.address+ins.size+op.mem.disp
   if ins.mnemonic=='mov'and op.type==X86_OP_IMM:value=op.imm
   definition=(ins.address,value)
 if not definition or definition[1]!=o.base:return None
 for b,e in chunks:
  for ins in o.instructions(b,e):
   if ins.address<=definition[0]:continue
   if ins.mnemonic=='call'and reg in {'rax','rcx','rdx','r8','r9','r10','r11'}:return None
   if ins.mnemonic!='pop'and reg in {key(ins.reg_name(r))for r in ins.regs_access()[1]}:return None
 return {'definition':hex(definition[0]),'register':reg,'value':hex(o.base),'proof':'entry-prefix image base;no subsequent non-epilogue write;volatile-call clobbers rejected'}
results=[]
for item in old['unlocated_signatures']:
 target=int(item['va'],16);hits={};raw_hits=0
 targets={target:'exact'}
 if o.pe.get_data(target-o.base-2,2)==b'\x09\x00':targets[target-2]='tab-prefixed literal'
 for begin,data in o.text:
  for m in re.finditer(rb'[\x40-\x4f]?\x8d[\x05\x0d\x15\x1d\x25\x2d\x35\x3d][\s\S]{4}',data):
   at=begin+m.start();size=len(m.group());resolved=at+size+struct.unpack_from('<i',m.group(),size-4)[0]
   if resolved not in targets:continue
   c=o.chunk(at);ins=o.valid(at)
   if not ins or ins.mnemonic!='lea' or ins.size!=size:continue
   entry=c[2]if c else known.get(at,(None,None))[0]
   if entry is None:
    hits[at]={'at':hex(at),'entry':None,'instruction':ins.mnemonic+' '+ins.op_str,'bytes':ins.bytes.hex(),'address_proof':{'proof':'RIP-relative '+targets[resolved]+';function start not established'},'target':hex(resolved)}
   else:hits[at]={'at':hex(at),'entry':hex(entry),'instruction':ins.mnemonic+' '+ins.op_str,'bytes':ins.bytes.hex(),'address_proof':{'proof':'RIP-relative '+targets[resolved]},'target':hex(resolved)}
 for begin,data in o.text:
  for value,mode in [(target,'absolute'),(target-o.base,'image_relative')]:
   needle=struct.pack('<I',value);pos=0
   while True:
    pos=data.find(needle,pos)
    if pos<0:break
    raw_hits+=1;where=begin+pos;pos+=1
    for at in range(max(begin,where-12),where+1):
     c=o.chunk(at)
     if c:
      ins=o.valid(at);entry=c[2]
     elif at in known:entry,ins=known[at]
     else:continue
     if not ins or not ins.address<=where<ins.address+ins.size:continue
     for op in ins.operands:
      proof=None
      if op.type==X86_OP_IMM and op.imm==target:proof={'proof':'absolute immediate'}
      if op.type==X86_OP_MEM:
       if op.mem.base==X86_REG_RIP and ins.address+ins.size+op.mem.disp==target:proof={'proof':'RIP-relative address'}
       elif op.mem.disp==value and mode=='image_relative'and op.mem.index==0:
        proof=base_proof(entry,at,key(ins.reg_name(op.mem.base)))
      if proof:
       hits[at]={'at':hex(at),'entry':hex(entry),'instruction':ins.mnemonic+' '+ins.op_str,'bytes':ins.bytes.hex(),'address_proof':proof}
 for hit in hits.values():
  if not hit['entry']:
   at=int(hit['at'],16)
   for entry in sorted((v for v in o.direct if at-512<=v<=at),reverse=True):
    callers=o.calls_to(entry)
    if not any(c['validation']=='pdata instruction boundary'for c in callers):continue
    chunks=o.leaf_chunks(entry)
    if any(any(i.address==at for i in o.instructions(b,e))for b,e in chunks):
     hit['entry']=hex(entry);hit['entry_proof']={'method':'actual direct callee and reachable leaf CFG','validated_callers':callers};break
  if hit['entry']:hit['function']=o.dump('remaining_'+hit['entry'][2:],int(hit['entry'],16))
 results.append(dict(item,raw_hits=raw_hits,references=list(hits.values())))
assert len(results)==9 and all(r['references']and all(h['entry']for h in r['references'])for r in results)
(out/'REMAINING_REFS.json').write_text(json.dumps({'binary_sha256':s['EXPECTED']['x64'],'original_executed':False,'resolved_previous_signatures':9,'items':results},indent=2)+'\n')
checks=[(0x419ee6,'mov','dword ptr [rcx], 0'),(0x45d40e,'mov','dword ptr [rcx], 0'),
 (0x41c740,'mov','qword ptr [rcx + rax*8], r8'),(0x45d6c4,'mov','qword ptr [rcx + rax*8], r8'),
 (0x41e833,'lock add','dword ptr [r9 + 0x4020], 1'),(0x45d947,'lock add','dword ptr [r9 + 0x2020], 1'),
 (0x7d6dc1,'mov','dword ptr [r9 + 8], 0'),(0x7d6ddd,'lock add','dword ptr [r9 + 8], 1'),
 (0x7d6e81,'mov','dword ptr [r9 + 8], 0'),(0x7d6e9d,'lock add','dword ptr [r9 + 8], 1')]
verified=[]
for at,mnemonic,operands in checks:
 ins=o.valid(at);assert ins and (ins.mnemonic,ins.op_str)==(mnemonic,operands),(hex(at),ins)
 verified.append({'at':hex(at),'bytes':ins.bytes.hex(),'instruction':mnemonic+' '+operands})
(out/'VERIFIED.json').write_text(json.dumps({'binary_sha256':s['EXPECTED']['x64'],'checks':verified},indent=2)+'\n')
for r in results:print(r['signature'],'raw',r['raw_hits'],'validated',[(h['at'],h['entry'])for h in r['references']])

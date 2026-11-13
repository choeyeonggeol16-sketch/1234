"""Read-only PE/source audit. Candidate scans are validated against instructions.
This tool never runs the supplied executables or writes production sources.
"""
from pathlib import Path
from functools import lru_cache
from bisect import bisect_right
from collections import defaultdict
import argparse,hashlib,json,re,struct
import pefile,capstone
from capstone.x86 import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
EXPECTED={'x64':'9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588','x86':'8eecd317fb152b76f32a0a6a65fbf916e39d20511e203b4a0ea602fe041a22f5'}
p=argparse.ArgumentParser();p.add_argument('--x64',type=Path,required=True);p.add_argument('--x86',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
class AuditPE:
 def __init__(self,path,arch):
  self.path=path;self.pe=pefile.PE(str(path));self.base=self.pe.OPTIONAL_HEADER.ImageBase;self.bits=64 if arch=='x64'else 32
  digest=hashlib.sha256(path.read_bytes()).hexdigest();assert digest==EXPECTED[arch],(path,digest)
  self.imports={i.address:i.name.decode()for m in self.pe.DIRECTORY_ENTRY_IMPORT for i in m.imports if i.name}
  self.md=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64 if self.bits==64 else capstone.CS_MODE_32);self.md.detail=True
  self.ranges=[];self.groups=defaultdict(list);self.start=[];self.direct=defaultdict(list);self.indirect=defaultdict(list);self.resolved={}
  if self.bits==64:
   @lru_cache(None)
   def root(begin,end,unwind):
    raw=self.pe.get_data(unwind,4)
    if raw[0]>>3&4:
     offset=4+((raw[2]*2+3)//4)*4
     return root(*struct.unpack('<III',self.pe.get_data(unwind+offset,12)))
    return begin
   for entry in self.pe.DIRECTORY_ENTRY_EXCEPTION:
    s=entry.struct;r=root(s.BeginAddress,s.EndAddress,s.UnwindData)
    chunk=(self.base+s.BeginAddress,self.base+s.EndAddress);self.ranges.append((*chunk,self.base+r));self.groups[self.base+r].append(chunk)
   self.ranges.sort();self.start=[r[0]for r in self.ranges]
  self.text=[]
  for sec in self.pe.sections:
   if sec.Characteristics&0x20000000:self.text.append((self.base+sec.VirtualAddress,self.pe.get_data(sec.VirtualAddress,sec.Misc_VirtualSize)))
  for start,data in self.text:
   for m in re.finditer(b'[\xe8\xe9]',data):
    pos=m.start()
    if pos+5<=len(data):self.direct[start+pos+5+struct.unpack_from('<i',data,pos+1)[0]].append(start+pos)
   for m in re.finditer(b'\xff[\x15\x25]',data):
    pos=m.start()
    if pos+6>len(data):continue
    operand=struct.unpack_from('<i'if self.bits==64 else '<I',data,pos+2)[0]
    target=start+pos+6+operand if self.bits==64 else operand;self.indirect[target].append(start+pos)
 def chunk(self,va):
  at=bisect_right(self.start,va)-1
  if at>=0 and self.ranges[at][0]<=va<self.ranges[at][1]:return self.ranges[at]
  return None
 @lru_cache(None)
 def instructions(self,begin,end):return list(self.md.disasm(self.pe.get_data(begin-self.base,end-begin),begin))
 def valid(self,va):
  chunk=self.chunk(va)
  if chunk:return next((ins for ins in self.instructions(chunk[0],chunk[1])if ins.address==va),None)
  # x86 has no pdata: import xrefs below are explicitly candidate-only until
  # enclosing function entry and reachability are checked by disassembly.
  return next(self.md.disasm(self.pe.get_data(va-self.base,15),va),None)
 def calls_to(self,target):
  found=[]
  for va in self.direct[target]:
   ins=self.valid(va)
   if ins and ins.mnemonic in ('call','jmp') and ins.operands[0].type==X86_OP_IMM and ins.operands[0].imm==target:
    c=self.chunk(va);found.append({'at':hex(va),'op':ins.mnemonic,'function':hex(c[2])if c else None,'validation':'pdata instruction boundary'if c else'entry-only candidate'})
  return found
 def import_calls(self,name):
  result=[]
  for iat,label in self.imports.items():
   if label!=name:continue
   for va in self.indirect[iat]:
    ins=self.valid(va)
    if ins and ins.mnemonic in ('call','jmp'):
     c=self.chunk(va);result.append({'at':hex(va),'op':ins.mnemonic,'function':hex(c[2])if c else None,'validation':'pdata instruction boundary'if c else'entry-only candidate'})
  return result
 def annotate(self,ins):
  notes=[]
  for op in ins.operands:
   if op.type!=X86_OP_MEM:continue
   target=ins.address+ins.size+op.mem.disp if op.mem.base==X86_REG_RIP else op.mem.disp if self.bits==32 and op.mem.base==0 else None
   if target in self.imports:notes.append('IAT '+self.imports[target])
   if target is not None:
    try:
     data=self.pe.get_data(target-self.base,640)
     if len(data)>=8:
      text=data.split(b'\x00\x00')[0]
      # Decode strings only when the initial UTF16 characters are printable.
      chars=[]
      for at in range(0,len(data)-1,2):
       word=int.from_bytes(data[at:at+2],'little')
       if word==0:break
       if word<32 or word>126:chars=[];break
       chars.append(chr(word))
      if len(chars)>=4:notes.append('UTF16 '+repr(''.join(chars)))
    except Exception:pass
  return notes
 def leaf_chunks(self,va):
  pending=[va];seen={}
  while pending and len(seen)<512:
   at=pending.pop()
   if at in seen or not va<=at<va+512:continue
   ins=next(self.md.disasm(self.pe.get_data(at-self.base,15),at),None)
   if not ins:continue
   seen[at]=ins
   if ins.mnemonic in ('ret','retf','int3','ud2'):continue
   jump=ins.mnemonic.startswith('j')or ins.mnemonic in ('loop','loope','loopne')
   if jump and ins.operands and ins.operands[0].type==X86_OP_IMM:
    pending.append(ins.operands[0].imm)
   if ins.mnemonic!='jmp':pending.append(at+ins.size)
  chunks=[]
  for at,ins in sorted(seen.items()):
   if chunks and chunks[-1][1]==at:chunks[-1]=(chunks[-1][0],at+ins.size)
   else:chunks.append((at,at+ins.size))
  return chunks
 def dump(self,label,va):
  c=self.chunk(va);lines=[];calls=[]
  if c:
   chunks=sorted(self.groups[c[2]]);proof='PE pdata/unwind';entry=c[2]
  else:
   # Bounded leaf slice: do not invent a 768-byte function extent. End at
   # first return/unconditional jump and mark the scope as incomplete.
   chunks=self.leaf_chunks(va)
   proof='reachable leaf CFG within 512 bytes; no pdata function boundary';entry=va
  lines.append('# '+label+'; '+proof+'; entry '+hex(entry))
  for begin,end in chunks:
   lines.append('# chunk '+hex(begin)+'..'+hex(end))
   for ins in self.instructions(begin,end):
    notes=self.annotate(ins)
    if ins.mnemonic=='call'and ins.operands[0].type==X86_OP_IMM:calls.append(hex(ins.operands[0].imm))
    lines.append(f'{ins.address:016x}: {ins.mnemonic:8s} {ins.op_str}'+(' ; '+' | '.join(notes)if notes else''))
  (a.out/(label+'.asm.txt')).write_text('\n'.join(line.rstrip()for line in lines)+'\n')
  result={'entry':hex(entry),'scope':proof,'chunks':[[hex(b),hex(e)]for b,e in chunks],'direct_calls':calls,'callers':self.calls_to(entry)};self.resolved[label]=result;return result

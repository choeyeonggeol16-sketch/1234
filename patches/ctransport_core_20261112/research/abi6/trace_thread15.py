from pathlib import Path
import pefile,capstone,hashlib,json,argparse
cli=argparse.ArgumentParser();cli.add_argument('--binary',type=Path,required=True);cli.add_argument('--out',type=Path,required=True);args=cli.parse_args();args.out.mkdir(parents=True,exist_ok=True)
p=args.binary;assert hashlib.sha256(p.read_bytes()).hexdigest()=='9940df15727ec88086cefc036d153fe18c153c9c26a5c90bafe1545de797b588'
pe=pefile.PE(str(p));base=pe.OPTIONAL_HEADER.ImageBase
imports={v.address:v.name.decode() for m in pe.DIRECTORY_ENTRY_IMPORT for v in m.imports if v.name}
md=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64);md.detail=True
for addr in [0x725488,0x82c408]:
 e=next(x for x in pe.DIRECTORY_ENTRY_EXCEPTION if base+x.struct.BeginAddress<=addr<base+x.struct.EndAddress)
 end=base+e.struct.EndAddress;lines=[];calls=[];strings=[]
 for i in md.disasm(pe.get_data(addr-base,end-addr),addr):
  note=''
  for o in i.operands:
   target=0
   if o.type==capstone.x86.X86_OP_MEM and o.mem.base==capstone.x86.X86_REG_RIP:target=i.address+i.size+o.mem.disp
   if target:
    if target in imports:note=imports[target]
    else:
     try:
      b=pe.get_data(target-base,350);t=b.decode('utf-16le',errors='ignore').split('\0')[0]
      if b.startswith(b'.\\'):t=b.split(b'\0')[0].decode('ascii',errors='replace')
      if 6<len(t)<170 and all(c.isprintable() for c in t):note=t;strings.append(t)
     except Exception:pass
  if i.mnemonic=='call':calls.append(i.op_str+' '+note)
  lines.append(f'{i.address:016x}: {i.mnemonic:8s} {i.op_str}'+(' ; '+note if note else ''))
 (args.out/f'thread15-{addr:x}.asm.txt').write_text('\n'.join(lines)+'\n')
 print(json.dumps({'address':hex(addr),'instructions':len(lines),'calls':calls,'strings':strings}))

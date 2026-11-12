"""Read-only RTTI/vtable and I/O evidence extraction from the two supplied PEs."""
from pathlib import Path
import argparse,bisect,hashlib,json,struct
import pefile,capstone
p=argparse.ArgumentParser();p.add_argument('--input',type=Path,required=True);p.add_argument('--out',type=Path,required=True);args=p.parse_args();args.out.mkdir(exist_ok=True,parents=True)
all_results={}
for name in ['L2Server.exe','L2Server(10).exe']:
 path=args.input/name;data=path.read_bytes();pe=pefile.PE(data=data);base=pe.OPTIONAL_HEADER.ImageBase
 width=8 if pe.FILE_HEADER.Machine==0x8664 else 4;fmt='<Q' if width==8 else '<I'
 md=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64 if width==8 else capstone.CS_MODE_32);md.detail=True
 iat={i.address:i.name.decode() for m in pe.DIRECTORY_ENTRY_IMPORT for i in m.imports if i.name}
 found={};dumps=[]
 markers={'CSocket':'.?AVCSocket@@','CUserSocket':'.?AVCUserSocket@@','AtomicAddTimer':'.?AVAtomicAddTimer@@', 'CIOObject':'.?AVCIOObject@@','CIOSocket':'.?AVCIOSocket@@',
          'CIOSocketEx8192':'.?AV?$CIOSocketEx@V?$CIOBufferEx@$0CAAA@@@@@',
          'CIOSocketEx16384':'.?AV?$CIOSocketEx@V?$CIOBufferEx@$0EAAA@@@@@'}
 for cls,label in markers.items():
  marker=label.encode()+b'\0';offset=data.find(marker)
  if offset<0:continue
  type_rva=pe.get_rva_from_offset(offset)-2*width;type_value=type_rva if width==8 else base+type_rva
  tables=[];at=0
  while True:
   hit=data.find(struct.pack('<I',type_value),at)
   if hit<0:break
   at=hit+1
   if hit<12:continue
   col_off=hit-12
   try:
    col_rva=pe.get_rva_from_offset(col_off);col=pe.get_data(col_rva,24)
    signature=struct.unpack_from('<I',col)[0]
    if width==8 and (signature not in [0,1] or (signature==1 and struct.unpack_from('<I',col,20)[0]!=col_rva)):continue
    if width==4 and struct.unpack_from('<I',col)[0]!=0:continue
    ptr=struct.pack(fmt,base+col_rva);cursor=0
    while True:
     vt=data.find(ptr,cursor)
     if vt<0:break
     cursor=vt+width;vt_rva=pe.get_rva_from_offset(vt)+width
     slots=[struct.unpack(fmt,pe.get_data(vt_rva+j*width,width))[0] for j in range(24)]
     if not all(base<=v<base+pe.OPTIONAL_HEADER.SizeOfImage for v in slots[:3]):continue
     tables.append({'vtable':hex(base+vt_rva),'col':hex(base+col_rva),'offset':struct.unpack_from('<I',col,4)[0],'slots':[hex(v) for v in slots]})
     for slot,v in enumerate(slots[:22]):
      section=pe.get_section_by_rva(v-base)
      if not section or not section.Characteristics&0x20000000:continue
      dumps.append(f'\n### {cls} vtable {hex(base+vt_rva)} slot {slot} VA {hex(v)}')
      for i,ins in enumerate(md.disasm(pe.get_data(v-base,512),v)):
       notes=[]
       for op in ins.operands:
        if op.type==capstone.x86.X86_OP_MEM:
         target=ins.address+ins.size+op.mem.disp if op.mem.base==capstone.x86.X86_REG_RIP else op.mem.disp
         if target in iat:notes.append(iat[target])
       dumps.append(f'{ins.address:016x}: {ins.mnemonic:8s} {ins.op_str}'+(' ; '+','.join(notes) if notes else ''))
       if ins.mnemonic.startswith('ret') or i>75:break
   except (pefile.PEFormatError,struct.error):continue
  found[cls]=tables
 all_results[name]={'sha256':hashlib.sha256(data).hexdigest(),'pointer_bytes':width,'rtti':found}
 (args.out/(name+'.rtti-disasm.txt')).write_text('\n'.join(dumps)+'\n')
 print(name,{cls:len(v) for cls,v in found.items()})
(args.out/'server-rtti.json').write_text(json.dumps(all_results,indent=2)+'\n')

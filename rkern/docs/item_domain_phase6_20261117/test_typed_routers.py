from pathlib import Path
import re,json,subprocess,shutil
BASE=Path(__file__).resolve().parents[2]
ROOT=BASE/'src/l1j/server/server/model/item/function'
import tempfile
TARGET=Path(tempfile.mkdtemp(prefix='rkern_phase6_router_'))
if TARGET.exists():shutil.rmtree(TARGET)
TARGET.mkdir()
files=['WorldItemActions','TravelAndRecoveryItems','SupplementalCraftRouter','SupplementalRewardRouter']
all_names=[]
switches=json.loads((BASE/'docs/item_domain_phase6_20261117/SWITCH_MAP.json').read_text())
all_manifests=[('WorldItemActions',switches['world']['groups']),('TravelAndRecoveryItems',switches['travel']['groups']),('SupplementalCraftRouter',switches['craft']['groups']),('SupplementalRewardRouter',switches['rewards']['groups'])]
for name in files:
 s=(ROOT/(name+'.java')).read_text('utf8')
 s=re.sub(r'(?m)^package .+;\n','',s)
 s=re.sub(r'(?m)^import .+;\n','',s)
 (TARGET/(name+'.java')).write_text(s,'utf8')
for name,entries in all_manifests:
 for x in entries:
  if x['class'] not in all_names:all_names.append(x['class'])
# Every callee is typed with the actual method signature, not varargs/object placeholders.
lines=['class CIOClient {}','class L1PcInstance {}','class L1ItemInstance {}',
       'class InnHelper { static final int INN_KEYID=87654321; }',
       'class Trace { static String owner; static int item; static void record(String s,int i){owner=s;item=i;} }']
for name in all_names:
 args=next(router for router,entries in all_manifests if name in {x['class'] for x in entries})
 sig='CIOClient c, L1PcInstance pc, L1ItemInstance it, int id, L1ItemInstance t' if args in ('WorldItemActions','SupplementalCraftRouter') else ('CIOClient c, L1PcInstance pc, L1ItemInstance it, int id' if args=='TravelAndRecoveryItems' else 'L1PcInstance pc, L1ItemInstance it, int id, L1ItemInstance t')
 lines.append(f'final class {name} {{ static void use({sig}) {{ Trace.record("{name}",id); }} }}')
(TARGET/'Stubs.java').write_text('\n'.join(lines)+'\n','utf8')

main=['public class RouterCases { public static void main(String[] a) {',
      'CIOClient c=new CIOClient(); L1PcInstance p=new L1PcInstance(); L1ItemInstance it=new L1ItemInstance();',
      'int count=0;']
for router,records in all_manifests:
 for rec in records:
  for raw in rec['ids']:
   idv=str(raw)
   x=idv if idv!='InnHelper.INN_KEYID' else 'InnHelper.INN_KEYID'
   call={'WorldItemActions':'WorldItemActions.use(c,p,it,ID,it)',
         'TravelAndRecoveryItems':'TravelAndRecoveryItems.use(c,p,it,ID)',
         'SupplementalCraftRouter':'SupplementalCraftRouter.use(c,p,it,ID,it)',
         'SupplementalRewardRouter':'SupplementalRewardRouter.use(p,it,ID,it)'}[router].replace('ID',x)
   main.append(f'Trace.owner=null; {call}; if (!"{rec["class"]}".equals(Trace.owner) || Trace.item!={x}) throw new AssertionError("{router} id {x} -> "+Trace.owner); count++;')
 for idv in [0,1,-1,2147483647]:
  occupied={str(x) for e in records for x in e['ids']}
  if str(idv) not in occupied:
   call={'WorldItemActions':'WorldItemActions.use(c,p,it,ID,it)',
         'TravelAndRecoveryItems':'TravelAndRecoveryItems.use(c,p,it,ID)',
         'SupplementalCraftRouter':'SupplementalCraftRouter.use(c,p,it,ID,it)',
         'SupplementalRewardRouter':'SupplementalRewardRouter.use(p,it,ID,it)'}[router].replace('ID',str(idv))
   main.append(f'Trace.owner=null; {call}; if(Trace.owner!=null) throw new AssertionError("Unexpected {router} for {idv}"); count++;')
main+=['System.out.println("PASS typed production routers compiled and dispatched "+count+" ID cases including unknown IDs");','}}']
(TARGET/'RouterCases.java').write_text('\n'.join(main)+'\n','utf8')
r=subprocess.run(['javac','-encoding','UTF-8','-d',str(TARGET),*[str(x) for x in TARGET.glob('*.java')]],text=True,capture_output=True)
print(r.stdout,r.stderr);assert r.returncode==0
r=subprocess.run(['java','-cp',str(TARGET),'RouterCases'],capture_output=True,text=True)
print(r.stdout,r.stderr);assert r.returncode==0

shutil.rmtree(TARGET)

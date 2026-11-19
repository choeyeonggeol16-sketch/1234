#!/usr/bin/env python3
from pathlib import Path
import json,re,subprocess,tempfile,collections,random,shutil
root=Path(__file__).resolve().parents[3]
f=root/'src/l1j/server/server/model/item/function'
m=json.loads((Path(__file__).parent/'EXPECTED_ORIGINAL_FRAGMENTS.json').read_text())
router=(f/'UnlistedItemRouter.java').read_text()
with tempfile.TemporaryDirectory() as temp:
 base=Path(temp)
 def write(path,source):
  file=base/('src/'+path.replace('.','/')+'.java');file.parent.mkdir(parents=True,exist_ok=True);file.write_text(source)
  return file
 norm={}
 for fullname in re.findall(r'^import (?!static )([\w.]+);',router,re.M):
  name=fullname.split('.')[-1];package='.'.join(fullname.split('.')[:-1]);norm[fullname]=(package,name)
 for fullname,(package,name) in norm.items():
  write(fullname,f'package {package};\npublic class {name} {{}}\n')
 statics=collections.defaultdict(set)
 for fullname in re.findall(r'^import static ([\w.]+);',router,re.M):
  statics['.'.join(fullname.split('.')[:-1])].add(fullname.split('.')[-1])
 for fullname,names in statics.items():
  package='.'.join(fullname.split('.')[:-1]);name=fullname.split('.')[-1]
  write(fullname,'package '+package+'; public class '+name+' { '+' '.join('public static void '+n+'(Object...args) {}' for n in sorted(names))+' }')
 p='l1j.server.server.model.item.function'
 write(p+'.ItemActionContext',f'''package {p};
 import l1j.server.server.model.Instance.*;
 import l1j.server.server.model.network.core.session.CIOClient;
 class ItemActionContext {{ private int id; ItemActionContext(int id){{this.id=id;}}
 L1PcInstance player(){{return null;}} CIOClient client(){{return null;}} L1ItemInstance item(){{return null;}}
 int itemId(){{return id;}} int itemObjectId(){{return 0;}} int skillId(){{return 0;}} int spellTargetId(){{return 0;}}
 int spellX(){{return 0;}} int spellY(){{return 0;}}
 }}''')
 write(p+'.ItemActionResult','package '+p+'; enum ItemActionResult { '+', '.join('R'+str(i) for i in range(14))+' }')
 byclass=collections.defaultdict(list)
 for i,full in enumerate(m['router_destination']):
  cls,method=full.split('.');byclass[cls].append((i,method))
 for cls,methods in byclass.items():
  write(p+'.'+cls,'package '+p+'; final class '+cls+' {'+' '.join('static ItemActionResult '+method+'(ItemActionContext use){ return ItemActionResult.R'+str(i)+'; }' for i,method in methods)+'}')
 # Actual source under test is compiled verbatim.
 write(p+'.UnlistedItemRouter',router)
 original='\n'.join(('if' if i==0 else 'else if')+' '+cond+' return ItemActionResult.R'+str(i)+';' for i,cond in enumerate(m['original_predicates']))
 harness=f'''package {p};
 import java.util.Random;
 public class TestRouter {{
 static ItemActionResult original(int itemId) {{ {original} else return ItemActionResult.R13; }}
 public static void main(String[] args) {{
  Random rand = new Random(718230);
  long n=0;
  for(int id=0;id<5000000;id+=17) {{
   ItemActionResult old=original(id); ItemActionResult now=UnlistedItemRouter.use(new ItemActionContext(id));
   if(old!=now)throw new AssertionError("ID "+id+" "+old+" -> "+now); n++;
  }}
  for(int j=0;j<500000;j++) {{
    int id=(j%2==0)?rand.nextInt(1000000):rand.nextInt(Integer.MAX_VALUE);
    if(original(id)!=UnlistedItemRouter.use(new ItemActionContext(id)))throw new AssertionError("ID "+id);
    n++;
  }}
  int[][] samples={{ {{40136,0}},{{40859,1}},{{41357,2}},{{41277,3}},{{41383,4}},{{40170,5}},{{40226,6}},{{40232,7}},{{40265,8}},{{40164,9}},{{210020,10}},{{210000,11}},{{210123,12}},{{999999999,13}},{{40863,13}} }};
  for(int[] pair:samples) {{
    if(UnlistedItemRouter.use(new ItemActionContext(pair[0]))!=ItemActionResult.values()[pair[1]])
     throw new AssertionError("Sample id="+pair[0]+" actual="+UnlistedItemRouter.use(new ItemActionContext(pair[0])));
  }}
  System.out.println("PASS actual UnlistedItemRouter Java21 compile; original-vs-new routing "+n+" IDs; 15 boundary samples");
 }}
 }}'''
 write(p+'.TestRouter',harness)
 files=[str(x) for x in (base/'src').rglob('*.java')]
 subprocess.run(['javac','-encoding','UTF-8','-d',str(base/'classes')]+files,check=True,capture_output=False)
 subprocess.run(['java','-cp',str(base/'classes'),p+'.TestRouter'],check=True)

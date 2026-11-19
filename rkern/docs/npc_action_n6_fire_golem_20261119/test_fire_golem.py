"""Compare old recipe vectors and execute the production handler with typed fake receipt (not MySQL)."""
from pathlib import Path
import re,sys,zipfile,tempfile,subprocess
root=Path(__file__).resolve().parents[2]; prefix='src/l1j/server/server/model/network/core/cs/packet/'
with zipfile.ZipFile(sys.argv[1]) as z: old=z.read(prefix+'CS_NpcAcion2.java').decode()
new=(root/prefix/'CS_NpcAcion2.java').read_text(); handler=(root/prefix/'NpcExchangeCommands.java').read_text()
def block(s):
 a=s.index('} else if (npcid == 5066)'); return s[a:min(s.find(marker,a) for marker in ('} else if (npcid == 7)', '} else if (npcid == 80077)') if s.find(marker,a)>=0)]
a=block(old); b=block(new)
assert 'return NpcExchangeCommands.fireGolemWeapon(pc, obj.getId(), s, htmlid);' in b
assert '인첸트지급(' not in new
armor=a[a.index('s.equals("A")'):a.index('} else if (s.equals("a"))')]
assert armor in b # ground-drop behavior is deliberately unchanged
# Derive every expected recipe directly from the previous source, independent of new tables.
chunks=re.split(r'\} else if \(s\.equals(?:IgnoreCase)?\("([^"\n]+)"\)\) \{|\bif \(s\.equalsIgnoreCase\("(1)"\)\) \{',a)
# Explicit top-level matching avoids nested armor command branches.
pat=r'(?m)^\t{4}(?:\} else )?if \(s\.equals(?:IgnoreCase)?\("(\d+|[a-f])"\)\) \{[^\n]*\n'
ms=list(re.finditer(pat,a)); assert len(ms)==19,len(ms)
rows=[]
for k,m in enumerate(ms):
 cmd=m.group(1); end=ms[k+1].start() if k+1<len(ms) else len(a); seg=a[m.end():end]
 if cmd=='13': seg=seg[:seg.index('} else if (s.equals("A")')]
 req=[tuple(map(int,x)) for x in re.findall(r'consumeEnchantItem\((\d+), (\d+), 1\)',seg)]
 costs=[tuple(map(int,x)) for x in re.findall(r'consumeItem\((\d+), (\d+)\)',seg)]
 reward=re.search(r'인첸트지급\(pc, (\d+), 1, (\d+)\)',seg)
 if reward: out,en=map(int,reward.groups())
 else: out=int(re.search(r'storeItem\((\d+), 1\)',seg)[1]);en=0
 rows.append((cmd,req,costs,out,en))
# reuse the prior test's typed fake API; its previous assertions remain in its own script.
prev=(root/'docs/npc_action_n6_additional_exchange_20261119/test_exchange_extended.py').read_text()
stub=prev.split("java=r'''",1)[1].split(' HANDLER',1)[0]
stub=stub.replace('id==820004','id==820004||id==700077')
code='\n'.join(l for l in handler.splitlines() if not l.startswith(('package ','import '))).replace('final class NpcExchangeCommands','static final class NpcExchangeCommands')
java_rows=[]
for cmd,req,costs,out,en in rows:
 def arr(x):return '{'+','.join(str(y) for y in x)+'}'
 java_rows.append('new R("'+cmd+'",new int[][]{'+','.join(arr(x) for x in req)+'},new int[][]{'+','.join(arr(x) for x in costs)+'},'+str(out)+','+str(en)+')')
travel_start=new.index('if (s.equalsIgnoreCase("teleport island-silver"))')
travel_end=new.index('\n\t\t\t} else if',travel_start)
travel='static String silverIsland(L1PcInstance pc,String s,String htmlid){'+new[travel_start:travel_end]+'\nreturn htmlid;}'
body=r'''
 record R(String cmd,int[][] req,int[][] costs,int out,int en){}
 static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
 public static void main(String[] args){Logger.getLogger(NpcExchangeCommands.class.getName()).setLevel(Level.OFF);int cases=0;
 R[] recipes={ROWS};
 for(R row:recipes) for(int selected=0;selected<row.req.length;selected++) for(int miss=-1;miss<row.costs.length+1;miss++) for(int flag=0;flag<16;flag++) for(boolean commit:new boolean[]{true,false}) for(boolean tailPresent:new boolean[]{true,false}) {
 L1PcInstance p=new L1PcInstance();p.flags=flag;String tail=tailPresent?"tail":null;boolean staff=row.cmd.equals("11")||row.cmd.equals("12")||row.cmd.equals("13");
 for(int j=0;j<row.req.length;j++){int[] q=row.req[j];p.add(q[0],q[1],1,true);p.add(q[0],q[1]-1,1,false);if(staff?(miss!=row.costs.length||j!=selected):(j>=selected&&miss!=row.costs.length))p.add(q[0],q[1],1,false);}
 for(int j=0;j<row.costs.length;j++){int[] q=row.costs[j];p.add(q[0],0,q[1]-(j==miss?1:0),false);}
 String before=p.snapshot();String page=NpcExchangeCommands.fireGolemWeapon(p,42,row.cmd,tail);boolean valid=miss<0&&flag==0;
 check(before.equals(p.snapshot()),"premature inventory change "+row.cmd);check((p.success!=null)==valid,"admission "+row.cmd+" "+miss+" "+flag);
 if(valid){check(page==null&&p.trace.isEmpty(),"premature response");if(commit){p.success.run();var reward=p.items.getLast();check(reward.id==row.out&&reward.en==row.en&&reward.count==1,"wrong reward");for(int[] cost:row.costs)check(p.total(cost[0])==0,"wrong cost");for(int j=0;j<row.req.length;j++){int[] q=row.req[j];int count=0;for(var item:p.items)if(item.id==q[0]&&item.en==q[1]&&!item.eq)count+=item.count;check(count==(staff?0:j>selected?1:0),"wrong priority "+row.cmd);}
 check(p.trace.equals((row.cmd.equals("11")?"":"s403:i"+row.out+"+"+row.en+";")+"h42:;"),"wrong success response "+p.trace);
 }else{p.fail.run();check(p.snapshot().equals(before)&&p.trace.startsWith("m")&&!p.trace.contains("s403:")&&p.trace.endsWith(tailPresent?"h42:tail;":";"),"rollback");}}
 else{check(Objects.equals(page,tail)&&p.trace.startsWith("m")&&!p.trace.contains("s403:"),"failure response");}check(!p.busy,"gate retained");cases++;
 }
 for(String cmd:new String[]{"","0","14","01","A","F","aa","buy"," 1"}){L1PcInstance p=new L1PcInstance();check(NpcExchangeCommands.fireGolemWeapon(p,42,cmd,"tail").equals("tail")&&p.success==null&&p.trace.isEmpty(),"unexpected command");cases++;}
 System.out.println("Fire golem weapon recipe/failure/commit cases: "+cases);
 int travelCases=0;
 for(String cmd:new String[]{"teleport island-silver","TELEPORT ISLAND-SILVER","other"})for(int cash:new int[]{0,1499,1500,1501,5000})for(int flags:new int[]{0,2}){
 L1PcInstance p=new L1PcInstance();p.flags=flags;p.add(40308,0,cash,false);String page=silverIsland(p,cmd,"tail");boolean ok=cmd.equalsIgnoreCase("teleport island-silver")&&cash>=1500&&flags==0;check(p.tele==(ok?1:0)&&p.total(40308)==cash-(ok?1500:0),"silver island fee");check(page.equals(ok?"":"tail"),"silver island html");if(ok)check(p.x==33080&&p.y==33392&&p.map==4,"silver island destination");travelCases++;
 }
 System.out.println("Silver island payment guard cases: "+travelCases);
 }
}'''.replace('ROWS',',\n'.join(java_rows))
with tempfile.TemporaryDirectory() as d:
 file=Path(d)/'ExchangeTest.java'; file.write_text(stub+'\n'+code+'\n'+travel+'\n'+body)
 subprocess.run(['javac','-encoding','UTF-8','--release','21',str(file)],check=True)
 subprocess.run(['java','-cp',d,'ExchangeTest'],check=True)
print(f'Baseline recipe vectors: {len(rows)}; legacy armor block preserved')

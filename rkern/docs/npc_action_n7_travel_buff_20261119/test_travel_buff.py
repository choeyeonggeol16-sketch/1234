"""Compare moved branches against previous ZIP; run the actual moved bodies under typed stubs.
Usage: python test_travel_buff.py BASELINE_ZIP. No game runtime or DB asserted.
"""
from pathlib import Path
import re,subprocess,sys,zipfile,tempfile
root=Path(__file__).resolve().parents[2]; base='src/l1j/server/server/model/network/core/cs/packet/'
with zipfile.ZipFile(sys.argv[1]) as z: old=z.read(base+'CS_NpcAcion2.java').decode()
new=(root/base/'CS_NpcAcion2.java').read_text()
pat=r'(?m)^\t{3}(?:\} else )?if \([^\n]*?(?:\n\t{5}\|\|[^\n]*)?\) \{[^\n]*\n'
ms=list(re.finditer(pat,old)); end=old.index('\n\t\t\t}\n\t\t} catch (Exception e)')
rows=[]; methods=[]; cases=[]
for i,m in enumerate(ms):
 ids=re.findall(r'== (\d+)',m.group()); id=int(ids[0]) if ids else -1
 if id<0: continue
 route=re.search(r'(?m)^\s*(?:\} else )?if \(npcid == '+str(id)+r'\b[^\n]*(?:\n\s*\|\|[^\n]*)?\) \{[^\n]*\n\s*return (Npc\w+)\.(\w+)\(pc, s, htmlid([^)]*)\);',new)
 if not route: continue
 cls,name,extra=route.groups()
 if cls not in ('NpcTravelCommands','NpcBuffCommands','NpcExperienceCommands','NpcCastleActions','NpcGameEventActions'): continue
 original=old[m.end():ms[i+1].start() if i+1<len(ms) else end].rstrip('\n')
 if re.search(r"return Npc\w+\.",original): continue
 file=(root/base/(cls+'.java')).read_text(); signature=re.search(r'\tstatic String '+name+r'\([^\n]+\) \{\n',file); assert signature,(id,name)
 stop=file.index('\n\t\treturn htmlid;\n\t}',signature.end())
 actual=file[signature.end():stop]; expected=original
 if id==70611: expected=expected.replace('if (pc.getInventory().consumeItem(3000155, count)) {','if (count == 0) return "yuris3";\n\t\t\t\tif (pc.getInventory().consumeItem(3000155, count)) {')
 assert actual==expected,(id,name)
 rows.append((id,cls,name))
 if cls not in ('NpcTravelCommands','NpcBuffCommands'): continue
 params='L1PcInstance pc, String s, String htmlid'+(', Random _random' if '_random' in original else '')+(', L1Object obj' if id==7200022 else '')
 args='p,s,tail'+(',new Random(seed)' if '_random' in original else '')+(',npc' if id==7200022 else '')
 methods += ['static String old'+str(id)+'('+params+'){'+original+'\nreturn htmlid;}','static String new'+str(id)+'('+params+'){'+actual+'\nreturn htmlid;}']
 cases.append('case '+str(id)+' -> modified?new'+str(id)+'('+args+'):old'+str(id)+'('+args+');')
assert len(rows)==32,rows
assert len(cases)==29
# Both legacy/current share the real production constants and a deterministic wall clock for the cooldown checks.
code='\n'.join(methods).replace('System.currentTimeMillis()','NOW')
skill_names=sorted(set(re.findall(r'L1SkillId\.(\w+)',code)))
constants=' '.join('static final int '+x+'='+str(i+1000)+';' for i,x in enumerate(skill_names))
java=r'''
import java.util.*;
public class NpcMoveTest {
 static final long NOW=2000000L;
 static final int[] CLAN_BUFF_SKILLS={14,26,42,54,48,79,160,206,211,216,158,168};
 static final int[] BASIC_BUFF_SKILLS={26,42,48,158};
 static final int STATUS_UNDERWATER_BREATH=1500;
 static class L1SkillId {CONSTANTS}
 static class L1Clan {static final int 수련=1,수호=2,일반=3,군주=4,정예=5;}
 static class L1Object {} static class L1NpcInstance extends L1Object {}
 static class Inv {L1PcInstance p; Inv(L1PcInstance p){this.p=p;} boolean checkItem(int id,int n){return p.cash>=n;} boolean consumeItem(int id,int n){if(n<=0||p.fail||!checkItem(id,n))return false;p.cash-=n;p.log("debit",id,n);return true;}}
 static class GameMap {boolean escape; boolean isEscapable(){return escape;}}
 static class L1PcInstance {int lv, cash, rank, flag; long time; boolean fail,PC방_버프,invis,gm,skill;int lawful;StringBuilder trace=new StringBuilder();Inv inv=new Inv(this);GameMap map=new GameMap();
 void log(Object... v){trace.append(Arrays.toString(v)).append(';');} int getId(){return 9;}int getX(){return 32500;}int getY(){return 32501;}int getHeading(){return 2;}int getLevel(){return lv;}int getClanRank(){return rank;}long getBuffTime(){return time;}void setBuffTime(long t){time=t;log("time",t);}void setBuffnoch(int n){flag=n;log("flag",n);}void curePoison(){log("poison");}boolean isGm(){return gm;}boolean isInvisble(){return invis;}boolean hasSkillEffect(int id){return skill;}void setSkillEffect(int id,int ms){skill=true;log("effect",id,ms);}Inv getInventory(){return inv;}GameMap getMap(){return map;}void addLawful(int val){lawful+=val;log("lawful",val);}void start_teleport(int x,int y,int map,int head,int effect,boolean a,boolean b){log("tele",x,y,map,head,effect,a,b);}}
 static class L1SkillUse {static final int TYPE_GMBUFF=1,TYPE_SPELLSC=2;static void cast(L1PcInstance pc,Object... a){pc.log("cast",Arrays.toString(a));}}
 static class SC_SystemMessage {static void send(L1PcInstance p,Object... a){p.log("system",Arrays.toString(a));}}
 static class SC_ServerMessage {static void send(L1PcInstance p,Object... a){p.log("server",Arrays.toString(a));}}
 static class SC_SkillSound {static void send(L1PcInstance p,Object... a){p.log("sound",Arrays.toString(a));}static void broadcast(L1PcInstance p,Object... a){p.log("broadcast",Arrays.toString(a));}}
 static class SC_ChatPacket {static void sendDirect(L1PcInstance p,L1PcInstance target,String s){p.log("chat",s);}}
 static class SC_NpcChatPacket {static void sendNpc(L1PcInstance p,L1NpcInstance npc,String s,int t){p.log("npc",s,t);}}
 static class SC_PacketBox {static final int GREEN_MESSAGE=1;static void send(L1PcInstance p,Object... a){p.log("packet",Arrays.toString(a));}}
 static class SC_SkillIconBlessOfEva {static void send(L1PcInstance p,Object... a){p.log("eva",Arrays.toString(a));}}
 static class 잊섬Controller {static final 잊섬Controller I=new 잊섬Controller();static boolean open;static 잊섬Controller getInstance(){return I;}boolean get잊섬Start(){return open;}}
 METHODS
 static String run(int id,boolean modified,L1PcInstance p,String s,String tail,int seed,L1NpcInstance npc){return switch(id){CASES default -> throw new AssertionError(id);};}
 static L1PcInstance create(int lv,int cash,int flags){var p=new L1PcInstance();p.lv=lv;p.cash=cash;p.rank=flags%8;p.fail=(flags&1)!=0;p.PC방_버프=(flags&2)!=0;p.invis=(flags&4)!=0;p.gm=(flags&8)!=0;p.skill=(flags&16)!=0;p.map.escape=(flags&2)!=0;p.time=(flags&4)!=0?NOW-1000:0;return p;}
 public static void main(String[] a){int count=0;var npc=new L1NpcInstance();for(int id:new int[]{IDS})for(String s:new String[]{"","a","A","b","B","c","C","d","D","e","f","g","h","i","j","k","l","n","v","0","1","2","3","T_pcbang","teleportURL","teleport tamshop","giveto","wrong"})for(int lv:new int[]{1,10,20,29,30,39,40,43,44,45,51,55,56,60,74,75,80,81,90,91,99,100})for(int flags=0;flags<32;flags+=3)for(int cash:new int[]{0,100,1000,10000,300000}) {String tail=(flags&2)==0?null:"tail";var x=create(lv,cash,flags);var y=create(lv,cash,flags);잊섬Controller.open=(flags&8)!=0;String aa=run(id,false,x,s,tail,flags,npc),bb=run(id,true,y,s,tail,flags,npc);if(!Objects.equals(aa,bb)||x.cash!=y.cash||x.flag!=y.flag||x.lawful!=y.lawful||!x.trace.toString().equals(y.trace.toString()))throw new AssertionError(id+" "+s+" "+lv+" "+flags+" "+cash+" "+x.trace+" "+y.trace);count++;}System.out.println("Travel/buff equivalence cases: "+count);}
}
'''.replace('CONSTANTS',constants).replace('METHODS',code).replace('CASES','\n'.join(cases)).replace('IDS',','.join(str(r[0]) for r in rows if r[1] in ('NpcTravelCommands','NpcBuffCommands')))
with tempfile.TemporaryDirectory() as tmp:
 p=Path(tmp)/'NpcMoveTest.java';p.write_text(java);subprocess.run(['javac','--release','21','-encoding','UTF-8',str(p)],check=True);subprocess.run(['java','-Xmx256m','-cp',tmp,'NpcMoveTest'],check=True)
# ASCII range check matches original regex, including all BMP one-character inputs.
assert 's.length() == 1 && s.charAt(0) >= \'2\' && s.charAt(0) <= \'4\'' in new
for n in range(65536):
 s=chr(n);assert bool(re.fullmatch('[2-4]',s))==('2'<=s<='4')
assert 'if (item != null) SC_ServerMessage.send(pc, 143, "$7918", item.getName());' in new
assert 'if (pc.getInventory().storeItem(60032, 1) != null)' in new
assert 'if (pc.getInventory().storeItem(99115, 5) != null)' in new
assert len(re.findall(r'if \(npcid == 50015\)',new))==2
assert new.index('if (s.equalsIgnoreCase("T_pcbang"))')<new.index('if (s.equalsIgnoreCase("teleport island-silver"))')
print('32 original bodies matched (one explicit zero-count guard); 65,536 ASCII-range/BMP predicates; 3 null-result guards; duplicate route ordering preserved.')

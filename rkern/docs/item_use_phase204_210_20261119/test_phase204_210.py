#!/usr/bin/env python3
"""Typed original/new body comparisons, explicit ID audit. No server or database simulated as live."""
from pathlib import Path
import re, subprocess, tempfile
P=Path(__file__).resolve().parent
ROOT=P.parents[1]
SRC=ROOT/'src/l1j/server/server/model/item/function'
BASE=P/'baseline'
def read(n, root=SRC):return (root/(n+'.java')).read_text()
def cls(s,n,new=None):
    i=re.search(r'(?m)^(?:public )?final class '+n+r'\s*\{',s).start()
    return s[i:].replace(n,new or n)
def method(s,name):
    m=re.search(r'(?m)^\s*static\s+\w+\s+'+name+r'\s*\([^)]*\)\s*\{',s); assert m,name
    i=m.end(); level=1
    while level:
        level+=(s[i]=='{')-(s[i]=='}');i+=1
    return s[m.start():i]
def run(code):
    with tempfile.TemporaryDirectory() as tmp:
        p=Path(tmp); (p/'Test.java').write_text(code)
        subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',tmp,str(p/'Test.java')],check=True)
        r=subprocess.run(['java','-cp',tmp,'Test'],check=True,text=True,capture_output=True)
        print(r.stdout.strip())
# Entire preparation method: preserve all fallthrough and precedence.
old=cls(read('ItemActionPreparation',BASE),'ItemActionPreparation','OldPreparation')
new=cls(read('ItemActionPreparation'),'ItemActionPreparation','NewPreparation')
ammo=cls(read('AmmunitionSelectionItems'),'AmmunitionSelectionItems')
run('''
class E {static StringBuilder log=new StringBuilder();static int error,step;static boolean box;static void add(String s){log.append(s).append(';'); if(++step==error)throw new IllegalStateException("injected");}}
class Item {int id,type;Item(int a,int b){id=a;type=b;}int getItemId(){return id;}int getType(){return type;}}
class L1ItemInstance {Item t;L1ItemInstance(int id,int type){t=new Item(id,type);}Item getItem(){return t;}String getLogName(){return "item"+t.id;}}
class Inventory {L1ItemInstance prev; int arrow=123,sting=456;L1ItemInstance findItemId(int id){E.add("find:"+id);return prev;}int getArrowItemId(){E.add("arrowId");return arrow;}void setArrow(int v){E.add("arrow:"+v);arrow=v;}void setSting(int v){E.add("sting:"+v);sting=v;}L1ItemInstance getArrow(){E.add("getArrow");return prev;}L1ItemInstance getSting(){E.add("getSting");return prev;}}
class L1PcInstance {Inventory inv=new Inventory();L1ItemInstance w; L1ItemInstance getWeapon(){return w;}Inventory getInventory(){return inv;}}
class SC_ItemName {static void send(L1PcInstance p,L1ItemInstance i){E.add("itemName:"+i.t.id);}}
class SC_ServerMessage {static void send(L1PcInstance p,int x,String s){E.add("msg:"+x+":"+s);}}
class SC_SystemMessage {static void send(L1PcInstance p,String s){E.add(s);}}
class StarterSupportHandler {static StarterSupportHandler getInstance(){return new StarterSupportHandler();}void handle(L1PcInstance p,int id,L1ItemInstance i){E.add("starter:"+id);}}
class L1TreasureBox {static L1TreasureBox get(int id){E.add("box:"+id);return E.box?new L1TreasureBox():null;}void openAtomic(L1PcInstance p,L1ItemInstance i){E.add("open:"+i.t.id);}}
'''+old+new+ammo+'''
public class Test {
 static String evaluate(boolean old,int type,int id,int weapon,boolean previous,boolean box,int fail){E.log.setLength(0);E.step=0;E.error=fail;E.box=box;L1PcInstance p=new L1PcInstance();if(weapon>=0)p.w=new L1ItemInstance(50,weapon);if(previous)p.inv.prev=new L1ItemInstance(51,0);L1ItemInstance i=new L1ItemInstance(id,type);String r;try{r=""+(old?OldPreparation.stopsDispatch(p,i,id):NewPreparation.stopsDispatch(p,i,id));}catch(RuntimeException ex){r=ex.getMessage();}return E.log+"|"+r+"|"+p.inv.arrow+"|"+p.inv.sting;}
 public static void main(String[]a){int n=0;for(int t:new int[]{0,1,15,16,17})for(int id:new int[]{3000572,3000573,3000574,3000575,3000576,3000577,3000578,3000579,100})for(int w:new int[]{-1,3,4})for(boolean p:new boolean[]{false,true})for(boolean b:new boolean[]{false,true})for(int f=0;f<7;f++){String x=evaluate(true,t,id,w,p,b,f),y=evaluate(false,t,id,w,p,b,f);if(!x.equals(y))throw new AssertionError(x+" != "+y);n++;}System.out.println("PASS "+n+" ammunition/preparation outcomes including exception and precedence");}}
''')
# Main dispatcher: all ID labels unchanged; only approved inline branches changed.
s0=read('ItemActionDispatcher',BASE);s1=read('ItemActionDispatcher')
old_bodies={
'4100133':('TransformationListItems','handleShowJinDeathForms'),
'30124':('SiegeSupplyItems','showCatapultSupplyNotice'),
'42501':('WorldDestinationItems','handlePointTeleport'),
'3000049':('ExperienceRecoveryConsumables','handleRecoveryVoucherNotice'),
'4000138, 4000139, 4000140, 4000141, 4000142, 4000143, 4000144':('DragonBallConfirmationItems','requestConfirmation')}
def cases(s):return re.findall(r'\bcase\s+([\d\w.,\s]+?)\s*->',s)
assert cases(s0)==cases(s1)
old_methods=[];new_methods=[]
for idx,(label,(name,meth)) in enumerate(old_bodies.items()):
    m=re.search(r'case '+re.escape(label)+r' -> \{(.*?)\}',s0,re.S); assert m
    assert 'case '+label+' -> '+name+'.'+meth+'(use);' in s1
    body=m.group(1).replace('yield ', 'return ')
    if label=='42501': body='if (!pc.isGm()) return ItemActionResult.STOP;'+body
    old_methods.append('static ItemActionResult old'+str(idx)+'(ItemActionContext use){L1PcInstance pc=use.player();int spellTargetX=use.spellX(),spellTargetY=use.spellY();'+body+'}')
    new_methods.append(method(read(name),meth))
    s0=s0.replace(m.group(0),'case '+label+' -> '+name+'.'+meth+'(use);')
for name in ['SC_ShowPolyList','SC_SystemMessage','SC_MessageYN']:
    s0=s0.replace('import l1j.server.server.model.network.core.sc.packet.'+name+';\n','')
# Explicit whitelist of later approved route changes; retain byte-exact comparison
# for every other line in the original 204-210 refactor audit.
for item_id, owner, method_name, arguments in [
    (220012,'MagicShieldItemEffect','매직실드','pc'),
    (70721,'CallAdvanceItemEffect','콜어드밴스','pc, spellTargetId, item'),
    (70888,'ElvenStrikeItemEffect','엘븐스트라이크','pc, spellTargetId, item'),
    (707779,'AvengerItemEffect','어벤저','pc, spellTargetId, item'),
    (707781,'DemolitionItemEffect','데몰리션','pc, spellTargetId, item')]:
    old_route=f'case {item_id} -> {{ {method_name}({arguments}); yield ItemActionResult.COMPLETE; }}'
    approved_route=f'case {item_id} -> {owner}.{method_name}({arguments})\n                    ? ItemActionResult.COMPLETE : ItemActionResult.STOP;'
    assert old_route in s0 and approved_route in s1
    s0=s0.replace(old_route,approved_route)
    old_owner='CombatSkillItemEffects' if item_id==220012 else 'CombatAbilityItems'
    s0=s0.replace(f'import static l1j.server.server.model.item.function.{old_owner}.{method_name};\n','')
s0=s0.replace('case 55558 -> { 정상의가호2(pc, item); yield ItemActionResult.COMPLETE; }','case 55558 -> { 정상의가호2(pc, item); yield ItemActionResult.STOP; }')
old_marble = 'case CollectionLoadManager.CTSYSTEM_STORE_ID -> { CollectionHandler.store(pc, item, storedItemObjectId); yield ItemActionResult.COMPLETE; }'
new_marble = ('case CollectionLoadManager.CTSYSTEM_STORE_ID -> { CollectionHandler.store(pc, item, storedItemObjectId,\n'
              '                    () -> ItemActionCompletion.finishCommittedConsumable(pc, item)); yield ItemActionResult.STOP; }')
assert old_marble in s0 and new_marble in s1
s0=s0.replace(old_marble,new_marble)
assert s0==s1,'unexpected dispatcher change'
# Old/new numbered-wand block: preserve GM denial, all five indices, and failure order.
ext=read('ExtendedItemActions',BASE); a=ext.index('            if(!pc.isGm())');b=ext.index('            return;\n        }',ext.index('pc.sendPackets(String.format',a))
body=ext[a:b]; old_methods.append('static void oldBug(L1PcInstance pc,int itemId){'+body+'}')
new_methods.append(method(read('BugRaceControlWandItems'),'useNumberedDownWand'))
expected=(ext[:a]+'            BugRaceControlWandItems.useNumberedDownWand(pc, itemId);\n'+ext[b:]).replace('import l1j.server.server.Controller.BugRaceController;\n','')
assert expected==read('ExtendedItemActions')
old_calls=''.join('case '+str(i)+' -> old'+str(i)+'(u);' for i in range(len(old_bodies)))
new_calls=''.join('case '+str(i)+' -> '+m+'(u);' for i,(_,m) in enumerate(old_bodies.values()))
run('''
import java.util.Arrays;
enum ItemActionResult{STOP,COMPLETE}
class E {static StringBuilder log=new StringBuilder();static boolean fail;static void add(String s){log.append(s).append(';');if(fail)throw new IllegalStateException("output failed");}}
class L1PcInstance {boolean gm,auto;int id=123,map=14,head=7;int getId(){return id;}short getMapId(){return (short)map;}int getHeading(){return head;}boolean getAutoHunt(){return auto;}boolean isGm(){return gm;}void start_teleport(int x,int y,short m,int h,int g,boolean b,boolean a){E.add("teleport:"+x+":"+y+":"+m+":"+h+":"+g+":"+b+":"+a);}void sendPackets(String s){E.add(s);}}
class ItemActionContext {L1PcInstance p;int x,y;ItemActionContext(L1PcInstance p,int x,int y){this.p=p;this.x=x;this.y=y;}L1PcInstance player(){return p;}int spellX(){return x;}int spellY(){return y;}}
class SC_ShowPolyList{static void send(L1PcInstance p,int id,String s){E.add("poly:"+id+":"+s);}}
class SC_SystemMessage{static void send(L1PcInstance p,String s){E.add(s);}}
class SC_MessageYN{static void send(L1PcInstance p,int id,String s){E.add("confirm:"+id+":"+s);}}
class BugRaceController{static final BugRaceController ONE=new BugRaceController();boolean[] _is_downs=new boolean[5];static BugRaceController getInstance(){return ONE;}}
public class Test {
'''+ '\n'.join(old_methods+new_methods)+'''
 static ItemActionResult runOld(int i,ItemActionContext u){return switch(i){'''+old_calls+'''default->throw new AssertionError();};}
 static ItemActionResult runNew(int i,ItemActionContext u){return switch(i){'''+new_calls+'''default->throw new AssertionError();};}
 static String evaluate(boolean old,int i,int x,int y,boolean auto,boolean fail,boolean gm){E.log.setLength(0);E.fail=fail;L1PcInstance p=new L1PcInstance();p.auto=auto;p.gm=gm;ItemActionContext u=new ItemActionContext(p,x,y);String r;try{r=""+(old?runOld(i,u):runNew(i,u));}catch(RuntimeException ex){r=ex.getMessage();}return E.log+"|"+r;}
 static String bug(boolean old,int id,boolean gm,boolean fail,boolean prior){E.log.setLength(0);E.fail=fail;Arrays.fill(BugRaceController.ONE._is_downs,prior);L1PcInstance p=new L1PcInstance();p.gm=gm;String r="ok";try{if(old)oldBug(p,id);else useNumberedDownWand(p,id);}catch(RuntimeException ex){r=ex.getMessage();}return E.log+"|"+r+"|"+Arrays.toString(BugRaceController.ONE._is_downs);}
 public static void main(String[]a){int n=0;for(int i=0;i<5;i++)for(int x:new int[]{0,32768,-1})for(int y:new int[]{0,65535,-1})for(boolean h:new boolean[]{false,true})for(boolean f:new boolean[]{false,true})for(boolean g:new boolean[]{false,true}){String b=evaluate(true,i,x,y,h,f,g),c=evaluate(false,i,x,y,h,f,g);if(!b.equals(c))throw new AssertionError(b+" != "+c);n++;}for(int id=4100255;id<=4100259;id++)for(boolean gm:new boolean[]{false,true})for(boolean f:new boolean[]{false,true})for(boolean p:new boolean[]{false,true}){String b=bug(true,id,gm,f,p),c=bug(false,id,gm,f,p);if(!b.equals(c))throw new AssertionError(b+" != "+c);n++;}System.out.println("PASS "+n+" direct-action and numbered-wand outcomes");}}
''')
labels=cases(s1);print('PASS main dispatch labels:',len(labels),'groups,',sum(len(l.split(',')) for l in labels),'ID tokens; unchanged')
# Supplemental registry alignment (legacy mismatches deliberately not enabled).
registry=(ROOT/'src/l1j/server/server/utils/SupplementalItemIds.java').read_text().split('ITEM_IDS = {')[1].split('};')[0].replace('InnHelper.INN_KEYID','40312')
registered=set(map(int,re.findall(r'\d+',registry)))
handled=set(map(int,re.findall(r'\bcase\s+(\d+):',read('SupplementalItemActions'))))|{40312}
original=set(int(s) for s in (ROOT/'docs/item_domain_split_20261117/ORIGINAL_SUPPLEMENTAL_IDS.txt').read_text().splitlines() if s.isdigit())|{40312}
assert (original | {32011}) - {70888} == registered,(original-registered,registered-original)
print('Supplemental registry:',len(registered),'distinct IDs; switch:',len(handled),'distinct IDs')
print('Admitted with no supplemental case:',sorted(registered-handled))
print('Supplemental case not admitted:',sorted(handled-registered))
# Verify the actual decoder class (including modeOf) remains equivalent; UI extracted only.
old=cls(read('ItemTargetDecoder',BASE),'ItemTargetDecoder','OldDecoder')
new=cls(read('ItemTargetDecoder'),'ItemTargetDecoder','NewDecoder')
ui=method(read('TransformationListItems'),'showNeigeSelection')
run('''
class E {static StringBuilder log=new StringBuilder();static int fail,step;static void add(String s){log.append(s).append(';');if(++step==fail)throw new IllegalStateException("output failed");}}
class L1PcInstance {boolean invisible;int getId(){return 582;}boolean isInvisble(){return invisible;}void sendPackets(String s){E.add(s);}}
class SC_SkillSound {static void send(L1PcInstance p,int id,int effect){E.add("sound:"+id+":"+effect);}}
class SC_NPCTalkReturn {static void send(L1PcInstance p,int id,String s){E.add("html:"+id+":"+s);}}
class CollectionLoadManager {static final int CTSYSTEM_STORE_ID=2000000000;}
class TransformationListItems {'''+ui+'}'+old+new+'''
public class Test {
 static String evaluate(boolean old,int id,boolean invis,int fail){E.log.setLength(0);E.fail=fail;E.step=0;L1PcInstance p=new L1PcInstance();p.invisible=invis;String r;try{r=""+(old?OldDecoder.showSelection(p,id):NewDecoder.showSelection(p,id));}catch(RuntimeException ex){r=ex.getMessage();}return E.log+"|"+r;}
 public static void main(String[]a){int n=0;for(int id:new int[]{40088,140088,875640379,40096,0,40087,40089})for(boolean invis:new boolean[]{false,true})for(int fail=0;fail<=3;fail++){String b=evaluate(true,id,invis,fail),c=evaluate(false,id,invis,fail);if(!b.equals(c))throw new AssertionError(b+" != "+c);n++;}System.out.println("PASS "+n+" pre-admission selection outcomes");}}
''')
assert method(read('ItemTargetDecoder',BASE),'modeOf').replace('140089, 30074 -> TARGET','140089, 30074, 70888 -> TARGET')==method(read('ItemTargetDecoder'),'modeOf')
# Compare top-level supplemental cases to the immediate child router, including multi-label arrow switches.
def colon_groups(s):
    return [(set(int(x) if x.isdigit() else 40312 for x in re.findall(r'case\s+(\d+|InnHelper\.INN_KEYID)\s*:',labels)),target) for labels,target in re.findall(r'((?:\s*case\s+(?:\d+|InnHelper\.INN_KEYID)\s*:)+)\s*(\w+)\.use\(',s)]
for ids,target in colon_groups(read('SupplementalItemActions')):
    raw=read(target)
    dest=set(map(int,re.findall(r'\bcase\s+(\d+)\s*:',raw)))
    if target=='AdvancedAbilityItems':
        dest={int(x) for group in cases(raw) for x in re.findall(r'\d+',group)}
    if 'case InnHelper.INN_KEYID:' in raw:dest.add(40312)
    assert ids==dest,(target,ids-dest,dest-ids)
    print('PASS immediate supplemental router',target,len(ids),'IDs')

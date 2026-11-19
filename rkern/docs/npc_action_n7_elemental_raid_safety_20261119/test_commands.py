from pathlib import Path
import os,re,zipfile,tempfile,subprocess
ROOT=Path(__file__).resolve().parents[2]
P=ROOT/'src/l1j/server/server/model/network/core/cs/packet'
BASE=Path(os.environ.get('RKERN_PREVIOUS_ZIP','/mnt/data/RKern_NPC_Action_N7_Residual_Handlers_Piers_Safety_FULL_SRC_20261119.zip'))
def extract(src,name):
    m=re.search(r'(?m)^\s*(?:(?:private|public) )?static (?:void|boolean|int|String) '+re.escape(name)+r'\([^)]*\)\s*\{',src)
    assert m,name
    i=src.index('{',m.start())+1; depth=1
    while depth:
        if src[i]=='{':depth+=1
        elif src[i]=='}':depth-=1
        i+=1
    return src[m.start():i].strip()
def methods(cls,names):
    s=(P/(cls+'.java')).read_text()
    return '\n'.join(extract(s,n) for n in names)
with zipfile.ZipFile(BASE) as z:
    old=z.read(str((P/'HtmlCmdHandler.java').relative_to(ROOT))).decode()
start=old.index('\n\t\t\telse if (((L1NpcInstance) obj).getNpcTemplate().get_npcId() == 9291) {')
end=old.index('\n\t\t\t} else if (s.equalsIgnoreCase("exp"))',start)
body=old[old.index('{',start)+1:end]
body=re.sub(r'/\*.*?\*/','',body,flags=re.S)
oldmethod='static void originalElemental(L1PcInstance pc,int objid,String s,String initialPage){String htmlid=initialPage;String[] htmldata=null;'+body+'\nif(htmlid!=null)SC_NPCTalkReturn.send(pc,objid,htmlid,htmldata);}'
q=(P/'NpcQuestTransactions.java').read_text(); first=extract(q,'orcQuestToken'); rest=q[q.index(first)+len(first):]; second=extract(rest,'orcQuestToken')
new=methods('NpcLocalCommands',['elementalAttribute','elementalResetCost','clearElementalProtection','beginnerMorphId','beginnerMorph'])+'\n'+first+'\n'+second+'\n'+methods('NpcGameEventActions',['enterClanRaid','enterBossRaid'])
java='''import java.util.*;import java.text.DecimalFormat;
public class Check {
 static int checks;
 static void ck(boolean x){if(!x)throw new AssertionError("assertion "+checks);checks++;}
 static class Inventory {L1PcInstance p;boolean funds,debit,grant;int money,consumes,stores;Inventory(L1PcInstance a){p=a;}boolean checkItem(int id,int n){p.log.add("check:"+id+":"+n);return funds;}boolean hasItemDirect(int id){p.log.add("has:"+id);return funds;}boolean consumeItem(int id,int n){consumes++;p.log.add("debit:"+id+":"+n);if(debit)money+=n;return debit;}L1ItemInstance storeItem(int id,int n){stores++;p.log.add("grant:"+id+":"+n);return grant?new L1ItemInstance(id):null;}}
 static class L1ItemInstance {int id; L1ItemInstance(int i){id=i;} L1ItemInstance getItem(){return this;} String getName(){return "item"+id;}}
 static class L1NpcInstance {L1NpcInstance getNpcTemplate(){return this;}String get_name(){return "NPC";}}
 static class Party {boolean leader;boolean isLeader(L1PcInstance p){return leader;}}
 static class L1PcInstance {List<String> log=new ArrayList<>();Inventory inv=new Inventory(this);Party p=new Party();boolean elf,protect,inParty;int attr,count,level,clan;int getId(){return 1;}boolean isElf(){return elf;}int getElfAttr(){return attr;}void setElfAttr(int n){attr=n;log.add("attr:"+n);}int getElfAttrInitCount(){return count;}void addElfAttrInitCount(int n){count=Math.min(count+n,20);log.add("init:"+n);}boolean hasSkillEffect(int n){return protect;}void removeSkillEffect(int n){protect=false;log.add("remove:"+n);}Inventory getInventory(){return inv;}int getLevel(){return level;}void sendPackets(String s){log.add("packet:"+s);}int getClanid(){return clan;}boolean isInParty(){return inParty;}Party getParty(){return p;}}
 static class SC_SystemMessage {static void send(L1PcInstance p,String s){p.log.add("system:"+s);}}
 static class SC_ServerMessage {static void send(L1PcInstance p,int id,String... s){p.log.add("msg:"+id+":"+Arrays.toString(s));}}
 static class SC_SkillIconGFX {static void send(L1PcInstance p,int id,int icon){p.log.add("icon:"+id+":"+icon);}}
 static class SC_SkillSound {static void send(L1PcInstance p,int id,int sound){p.log.add("sound:"+id+":"+sound);}}
 static class SC_NPCTalkReturn {static void send(L1PcInstance p,int id,String page,String[] data){p.log.add("html:"+id+":"+page+":"+Arrays.toString(data));}}
 static class L1SkillId {static final int ELEMENTAL_PROTECTION=1;}
 static class L1ItemId {static final int ADENA=40308;}
 static class L1PolyMorph {static final int MORPH_BY_NPC=1;static void doPoly(L1PcInstance p,int id,int time,int type,boolean b){p.log.add("morph:"+id+":"+time+":"+type+":"+b);}}
 static class Queen1 {static boolean active;static boolean getqeensgo(){return active;}static void setqeensgo(boolean b){active=b;}}
 static class BossRaid {static boolean active;static boolean getBossGo(){return active;}static void setBossGo(boolean b){active=b;}}
 static class QueenSystem2 {static QueenSystem2 I=new QueenSystem2();static QueenSystem2 getInstance(){return I;}void startQueen(L1PcInstance p){p.log.add("startQueen");}}
 static class BossRaidSystem {static BossRaidSystem I=new BossRaidSystem();static BossRaidSystem getInstance(){return I;}void startBossRaid(L1PcInstance p){p.log.add("startBoss");}}
'''+oldmethod+'\n'+new+'''
 static L1PcInstance sample(boolean elf,int attr,int count,boolean prot,boolean debit){L1PcInstance p=new L1PcInstance();p.elf=elf;p.attr=attr;p.count=count;p.protect=prot;p.inv.debit=debit;return p;}
 public static void main(String[] args){
  int begin=checks;
  for(String s:new String[]{"fire","FIRE","water","Water","air","AIR","earth","Earth","init","INIT","count","COUNT","money","Money","bm","BM","","unknown","mayer3"})
   for(boolean elf:new boolean[]{false,true})for(int attr:new int[]{0,1,2,3,4,8,9,-1})for(int c=-1;c<=20;c++)for(boolean prot:new boolean[]{false,true})for(boolean debit:new boolean[]{false,true}){
    L1PcInstance a=sample(elf,attr,c,prot,debit),b=sample(elf,attr,c,prot,debit);
    String initialPage=s.equals("mayer3")?"mayer3":null;originalElemental(a,99,s,initialPage);elementalAttribute(b,99,s,initialPage);
    ck(a.log.equals(b.log));ck(a.attr==b.attr);ck(a.count==b.count);ck(a.protect==b.protect);ck(a.inv.money==b.inv.money);
   }
  System.out.println("elemental original/new assertions: "+(checks-begin));begin=checks;
  int[] morphs={13152,15868,15539,13635,15550};
  for(int i=0;i<5;i++)for(String cmd:new String[]{"Src"+(i+1),"sRC"+(i+1)})for(int level=1;level<=100;level++)for(boolean funds:new boolean[]{false,true})for(boolean debit:new boolean[]{false,true}){
    L1PcInstance p=new L1PcInstance();p.level=level;p.inv.funds=funds;p.inv.debit=debit;
    int id=beginnerMorphId(cmd);ck(id==morphs[i]);boolean r=beginnerMorph(p,id),ok=level<=82&&funds&&debit;
    ck(r==(level<=82));ck(p.inv.consumes==(level<=82&&funds?1:0));ck(p.inv.money==(ok?100:0));ck(p.log.contains("morph:"+id+":1800:1:false")==ok);
  }
  for(String s:new String[]{"Src0","Src6","Src11","src1extra"," Src1",""})ck(beginnerMorphId(s)==0);
  System.out.println("morph assertions: "+(checks-begin));begin=checks;
  int[] ids={41060,0,41065,41064,41062,41063,41061};String[] ap={"orcfnoname9",null,"orcfnoa4","orcfhuwoomo4","orcfbakumo4","orcfbuka4","orcfkame4"},zp={"orcfnoname11",null,"orcfnoa7","orcfhuwoomo6","orcfbakumo6","orcfbuka6","orcfkame6"};
  for(int id=71037;id<=71045;id++)for(String cmd:new String[]{"A","a","Z","z","teleportURL","TELEPORTURL","","Q","mayer3"})for(boolean grant:new boolean[]{false,true})for(boolean debit:new boolean[]{false,true}){
    L1PcInstance p=new L1PcInstance();p.inv.grant=grant;p.inv.debit=debit;int i=id-71038;boolean token=i>=0&&i<7&&i!=1;
    String expected=id==71039&&cmd.equalsIgnoreCase("teleportURL")?"orcfbuwoo2":token&&cmd.equalsIgnoreCase("A")&&grant?ap[i]:token&&cmd.equalsIgnoreCase("Z")&&debit?zp[i]:null;
    String initialPage=cmd.equals("mayer3")?"mayer3":null;if(initialPage!=null)expected=initialPage;String actual=orcQuestToken(p,new L1NpcInstance(),id,cmd,initialPage);ck(Objects.equals(expected,actual));ck(p.inv.stores==(token&&cmd.equalsIgnoreCase("A")?1:0));ck(p.inv.consumes==(token&&cmd.equalsIgnoreCase("Z")?1:0));
    if(token&&cmd.equalsIgnoreCase("A")){ck(p.log.contains("grant:"+ids[i]+":1"));ck(p.log.stream().anyMatch(x->x.startsWith("msg:143:"))==grant);}
    if(token&&cmd.equalsIgnoreCase("Z"))ck(p.log.contains("debit:"+ids[i]+":1"));
  }
  System.out.println("orc token assertions: "+(checks-begin));begin=checks;
  for(boolean clanRaid:new boolean[]{false,true})for(String s:new String[]{"1","2",""})for(boolean active:new boolean[]{false,true})for(int clan:new int[]{0,1})for(boolean party:new boolean[]{false,true})for(boolean leader:new boolean[]{false,true})for(boolean funds:new boolean[]{false,true})for(boolean debit:new boolean[]{false,true}){
    Queen1.active=BossRaid.active=active;L1PcInstance p=new L1PcInstance();p.clan=clan;p.inParty=party;p.p.leader=leader;p.inv.funds=funds;p.inv.debit=debit;
    boolean eligible=(!clanRaid||s.equals("1")&&clan!=0)&&!active&&party&&leader&&funds;boolean ok=eligible&&debit;
    boolean continues=clanRaid?enterClanRaid(p,s):enterBossRaid(p);ck(continues==(clanRaid?(!s.equals("1")||!active&&clan!=0):!active));
    ck(p.inv.consumes==(eligible?1:0));String event=clanRaid?"startQueen":"startBoss";ck(p.log.contains(event)==ok);ck((clanRaid?Queen1.active:BossRaid.active)==(active||ok));
    if(ok)ck(p.log.indexOf("debit:7005254:1")<p.log.indexOf(event));
  }
  System.out.println("raid assertions: "+(checks-begin));System.out.println("Total assertions: "+checks);
 }
}
'''
with tempfile.TemporaryDirectory() as d:
    f=Path(d)/'Check.java';f.write_text(java)
    subprocess.run(['javac','--release','21','-encoding','UTF-8',str(f)],check=True)
    subprocess.run(['java','-cp',d,'Check'],check=True)
# Verify original route priority at the replacement positions and no reintroduced deleted class.
h=(P/'HtmlCmdHandler.java').read_text();assert h.index('== 9291)')<h.index('s.equalsIgnoreCase("exp")');assert h.index('beginnerMorphId =')<h.index('s.equalsIgnoreCase("contract1")');assert h.index('orcQuestToken(')<h.index('== 71078)');assert 'CS_NpcAcion2' not in h
assert '&& pc.getInventory().consumeItem(200000, 1)) {\n\t\t\t\t\t\t\tpc.start_teleport(32723' in h
print('Route placement and stat-reset debit guard: PASS')

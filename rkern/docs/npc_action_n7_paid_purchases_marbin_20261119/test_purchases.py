from pathlib import Path
import os, re, subprocess, tempfile, zipfile
ROOT=Path(__file__).resolve().parents[2]
P=ROOT/'src/l1j/server/server/model/network/core/cs/packet'
BASE=Path(os.environ.get('RKERN_PREVIOUS_ZIP','/mnt/data/RKern_NPC_Action_N7_Market_Mercenary_Ring_Safety_FULL_SRC_20261119.zip'))
def extract(source,name):
    match=re.search(r'(?m)^\s*(?:private )?static (?:void|int|String) '+re.escape(name)+r'\([^)]*\)\s*\{',source)
    assert match,name
    i=source.index('{',match.start())+1; n=1
    while n:
        n+=(source[i]=='{')-(source[i]=='}'); i+=1
    return source[match.start():i].strip()
def get(cls,name):return extract((P/(cls+'.java')).read_text(),name)
with zipfile.ZipFile(BASE) as z: old=z.read(str((P/'HtmlCmdHandler.java').relative_to(ROOT))).decode()
a=old.index('int count = 1;',old.index('get_npcId() == 200075)')); b=old.index('int adena = count * 2500;',a)
original='static int[] oldSharna(L1PcInstance pc,String s){'+old[a:b]+'return new int[]{itemid,count};}'
travel=(P/'NpcTravelCommands.java').read_text()
coords=re.search(r'private static final int\[\]\[\] GHOST_LOC\s*=\s*(\{.*?\});',travel,re.S).group(1)
oldcoords=re.search(r'int\[\]\[\] ghostloc\s*=\s*(\{.*?\});',old,re.S).group(1)
assert re.findall(r'\d+',coords)==re.findall(r'\d+',oldcoords)
new=(P/'HtmlCmdHandler.java').read_text()
# Assert branch identity/order and unchanged RNG thresholds.
pat=r'get_npcId\(\)\s*==\s*(\d+)'
assert re.findall(pat,old)==re.findall(pat,new)
assert re.findall(r'random\.nextInt\(10\) > \d+',old)==re.findall(r'random\.nextInt\(10\) > \d+',new)
methods='\n'.join(get('NpcExchangeCommands',n) for n in ['tikalExchange','hugrint','contract','sharnaItem','sharnaCount','sharna','paidPurchase'])+'\n'+get('NpcExperienceCommands','marbin').replace('NpcExchangeCommands.debitLegacy','debitLegacy')+'\n'+get('NpcTravelCommands','esmeralda')
java=r'''import java.util.*;
import java.util.logging.*;
public class Check {
 static final Logger LOG=Logger.getAnonymousLogger();static {LOG.setLevel(Level.OFF);} static int assertions;
 static void ck(boolean c){if(!c)throw new AssertionError("assertion "+assertions);assertions++;}
 static class L1ItemId {static final int ADENA=40308,MAGIC_BREATH=410094;}
 static class L1ItemInstance {int id,count;L1ItemInstance(int i,int n){id=i;count=n;}L1ItemInstance getItem(){return this;}String getName(){return "item"+id;}}
 static class Inv {Map<Integer,Integer> map=new HashMap<>();boolean checkItem(int id,int count){return map.getOrDefault(id,0)>=count;}void add(int id,int n){map.merge(id,n,Integer::sum);}}
 static class L1PcInstance {Inv inv=new Inv();List<String> log=new ArrayList<>();int level,mode,exp;boolean released;L1InventoryTransaction pending;int getLevel(){return level;}int getId(){return 8;}Inv getInventory(){return inv;}void beginGhost(int x,int y,short m,boolean b,int t){log.add("ghost:"+x+":"+y+":"+m);ck(b&&t==8);}}
 static boolean has(L1PcInstance p,int id,int n){return p.inv.checkItem(id,n);}
 static boolean debitRecipe(L1InventoryTransaction t,L1PcInstance p,int id,int n){return t.debit(id,n);}
 static boolean debitLegacy(L1InventoryTransaction t,L1PcInstance p,int id,int n){return t.debit(id,n);}
 static void 마빈경험치지급2(L1PcInstance p){ck(p.released);p.exp+=2;p.log.add("exp2");}
 static void 마빈경험치지급6(L1PcInstance p){ck(p.released);p.exp+=6;p.log.add("exp6");}
 static class SC_NPCTalkReturn {static void send(L1PcInstance p,int id,String s){ck(id==7);p.log.add("html:"+s);}}
 static class SC_ServerMessage {static void send(L1PcInstance p,int id,String name,String item){ck(p.released&&id==143&&name.equals("NPC"));p.log.add("reward:"+item);}}
 static class SC_SystemMessage {static void send(L1PcInstance p,String msg){ck(p.released);p.log.add("system:"+msg);}}
 static class SC_SkillSound {static void send(L1PcInstance p,int id,int sound){ck(p.released&&id==8&&sound==3944);p.log.add("sound");}static void broadcast(L1PcInstance p,int id,int sound){ck(p.released&&id==8&&sound==3944);p.log.add("broadcast");}}
 // Receipt model only: no real DB/CIO here. No staged mutations become live before complete().
 // mode: 0 commit; 1 begin fail; 2 debit fail; 3 grant fail; 4 submit throw; 5 rollback; 6 second debit fails.
 static class L1InventoryTransaction implements AutoCloseable {
   L1PcInstance p;Map<Integer,Integer> staged;Runnable failure,success;boolean submitted;int debits,resource;
   static L1InventoryTransaction begin(L1PcInstance p,int r){if(p.mode==1)throw new IllegalStateException();return new L1InventoryTransaction(p,r);}
   L1InventoryTransaction(L1PcInstance p,int r){this.p=p;resource=r;staged=new HashMap<>(p.inv.map);}
   boolean debit(int id,int n){debits++;if(p.mode==2||p.mode==6&&debits==2||staged.getOrDefault(id,0)<n)return false;staged.merge(id,-n,Integer::sum);return true;}
   L1ItemInstance grant(int id,int n){if(p.mode==3||id<=0)return null;staged.merge(id,n,Integer::sum);return new L1ItemInstance(id,n);}
   void onFailure(Runnable r){failure=r;}
   void commitAfterInventoryRelease(Runnable r){if(p.mode==4)throw new IllegalStateException();success=r;submitted=true;p.pending=this;}
   void complete(){ck(submitted);p.pending=null;p.released=true;if(p.mode==5){failure.run();return;}p.inv.map=staged;success.run();}
   public void close(){if(!submitted)p.released=true;}
 }
'''+original+'\n'+methods+'\nstatic final int[][] GHOST_LOC='+coords+r''';
 static void purchase(int kind,int level,String cmd,int mode,boolean funds,boolean owned){
   L1PcInstance p=new L1PcInstance();p.level=level;p.mode=mode;
   int costId=kind==0?210081:40308, cost=kind==0?100:kind==1?1000:kind<4?500000:oldSharna(p,cmd)[1]*2500;
   int reward=kind==0?210082:kind==1?410094:kind==2?87050:kind==3?87051:oldSharna(p,cmd)[0];int count=kind==4?oldSharna(p,cmd)[1]:1;
   String success=kind==0?"joegolem18":kind==1?"hugrint2":kind<4?"":"sharna3";
   String failure=kind==0?"joegolem19":kind==1?"hugrint3":kind==2?"killton3":kind==3?"merin3":"sharna5";
   if(owned&&kind==1)p.inv.add(reward,1);p.inv.add(costId,cost-(funds?0:1));Map<Integer,Integer> before=new HashMap<>(p.inv.map);
   String r=switch(kind){case 0->tikalExchange(p,7,"NPC");case 1->hugrint(p,7);case 2->contract(p,7,true);case 3->contract(p,7,false);default->sharna(p,7,cmd,"NPC");};
   ck(p.log.isEmpty()&&p.inv.map.equals(before));
   if(kind==1&&owned){ck(r.equals("hugrint4")&&p.pending==null);return;}
   if(!funds||reward==0||mode>=1&&mode<=4){ck(failure.equals(r)&&p.pending==null);return;}
   ck(r==null&&p.pending!=null);ck(p.pending.resource==(kind==0?500062:kind==1?900146:kind==2?777849:kind==3?777848:200075));p.pending.complete();
   if(mode==5){ck(p.inv.map.equals(before)&&p.log.equals(List.of("html:"+failure)));return;}
   ck(p.inv.map.get(costId)==0&&p.inv.map.get(reward)==count);ck(p.log.getLast().equals("html:"+success));
   if(kind==0||kind==4)ck(p.log.equals(List.of("reward:item"+reward,"html:"+success)));
   if(kind==1)ck(p.log.size()==1);
   if(kind==2||kind==3)ck(p.log.size()==2&&p.log.getFirst().startsWith("system:"));
 }
 static void marbinCase(String cmd,int level,int mode,int missing){
   L1PcInstance p=new L1PcInstance();p.level=level;p.mode=mode;boolean a=cmd.equalsIgnoreCase("a"),b=cmd.equalsIgnoreCase("b"),c=cmd.equalsIgnoreCase("c"),d=cmd.equalsIgnoreCase("d");int first=a?700013:700014,second=d?1000004:700016;
   if(missing!=1)p.inv.add(first,1);if(missing!=2)p.inv.add(second,1);if(missing!=3)p.inv.add(700015,100);
   Map<Integer,Integer> before=new HashMap<>(p.inv.map);String r=marbin(p,7,cmd,"mayer3");ck(p.log.isEmpty()&&p.exp==0&&p.inv.map.equals(before));
   if(!a&&!b&&!c&&!d){ck(r.equals("mayer3")&&p.pending==null);return;}
   String failure=a?"marbinquest9":b?"marbinquest8":"marbinquest7",success=a?"marbinquest4":b?"marbinquest2":"marbinquest6";
   if(b&&level<70||!b&&missing!=0||mode==1||mode==3||mode==4||!b&&(mode==2||mode==6)){ck(failure.equals(r)&&p.pending==null);return;}
   ck(r==null&&p.pending!=null&&p.pending.resource==70000);p.pending.complete();if(mode==5){ck(p.inv.map.equals(before)&&p.exp==0&&p.log.equals(List.of("html:"+failure)));return;}
   if(b){ck(p.exp==0&&p.inv.map.get(700012)==1&&p.log.equals(List.of("html:"+success)));for(var e:before.entrySet())ck(p.inv.map.get(e.getKey()).equals(e.getValue()));}
   else{ck(p.inv.map.get(first)==0&&p.inv.map.get(second)==0&&p.inv.map.get(700015)==0&&p.inv.map.get(700017)==(a?5:15));ck(p.exp==(a?2:6)&&p.log.equals(List.of(a?"exp2":"exp6","broadcast","sound","html:"+success)));}
 }
 public static void main(String[] args){
   L1PcInstance p=new L1PcInstance();p.level=80;
   for(int i=0;i<=65535;i++){String s=String.valueOf((char)i);ck(sharnaCount(s)==oldSharna(p,s)[1]);}
   for(int i=-5;i<=255;i++){p.level=i;ck(sharnaItem(i)==oldSharna(p,"0")[0]);}
   ck(sharnaCount("")==1&&sharnaCount("10")==1&&sharnaCount("mayer3")==1);System.out.println("Sharna mapping assertions="+assertions);int start=assertions;
   for(int k=0;k<5;k++)for(int l:new int[]{29,30,39,40,51,52,54,55,59,60,64,65,69,70,74,75,79,80,99})for(String s:new String[]{"0","9",":",";","<","=",">","?","@","a","B","c","x","mayer3"})for(int m=0;m<=5;m++)for(boolean funds:new boolean[]{false,true})for(boolean owned:new boolean[]{false,true})purchase(k,l,s,m,funds,owned);
   System.out.println("Purchase receipt assertions="+(assertions-start));start=assertions;
   for(String s:new String[]{"a","A","b","B","c","C","d","D","x","mayer3",""})for(int l:new int[]{1,69,70,71,80,99})for(int m=0;m<=6;m++)for(int miss=0;miss<=3;miss++)marbinCase(s,l,m,miss);
   System.out.println("Marbin receipt assertions="+(assertions-start));start=assertions;
   p.log.clear();esmeralda(p,"other");ck(p.log.isEmpty());Set<String> allowed=new HashSet<>();for(int i=0;i<GHOST_LOC[0].length;i++)allowed.add("ghost:"+GHOST_LOC[0][i]+":"+GHOST_LOC[1][i]+":"+GHOST_LOC[2][i]);
   for(int i=0;i<1000;i++){p.log.clear();esmeralda(p,i%2==0?"journey":"JOURNEY");ck(p.log.size()==1&&allowed.contains(p.log.getFirst()));}
   System.out.println("Ghost assertions="+(assertions-start));System.out.println("PASS total assertions="+assertions);
 }
}'''
with tempfile.TemporaryDirectory() as d:
    f=Path(d)/'Check.java';f.write_text(java)
    subprocess.run(['javac','-encoding','UTF-8','--release','21',str(f)],check=True)
    subprocess.run(['java','-cp',d,'Check'],check=True)

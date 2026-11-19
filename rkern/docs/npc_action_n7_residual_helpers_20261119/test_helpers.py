from pathlib import Path
import re, os, zipfile, tempfile, subprocess
ROOT=Path(__file__).resolve().parents[2]
P=ROOT/'src/l1j/server/server/model/network/core/cs/packet'
BASE=Path(os.environ.get('RKERN_PREVIOUS_ZIP','/mnt/data/RKern_NPC_Action_N7_Template_Dispatch_Quest_Receipt_FULL_SRC_20261119.zip'))
# Extract method source (including body) from actual production sources.
def extract(src,name):
    m=re.search(r'(?m)^\s*(?:private |public )?static (?:String|void|boolean) '+re.escape(name)+r'\([^\n]*\)\s*\{',src)
    assert m,name
    a=src.index('{',m.start()); d=1;i=a+1
    while d:
        if src[i]=='{':d+=1
        elif src[i]=='}':d-=1
        i+=1
    return src[m.start():i].strip()
def new(cls,name):return extract((P/(cls+'.java')).read_text(),name)
def norm(s):return re.sub(r'\s+','',re.sub(r'^(?:private|public) static ','static ',s))
with zipfile.ZipFile(BASE) as z: old=z.read(next(n for n in z.namelist() if n.endswith('/HtmlCmdHandler.java'))).decode()
unchanged={'NpcQuestTransactions':['지브릴','로빈후드','karmaLevelToHtmlId','getBloodCrystalByKarma','getSoulCrystalByKarma'],'NpcTravelCommands':['카너스','talkToDimensionDoor'],'NpcExperienceCommands':['게렝','마빈경험치지급2','마빈경험치지급6'],'NpcLocalCommands':['StatInitialize'],'NpcExchangeCommands':['createNewItem']}
count=0
for cls,names in unchanged.items():
    for name in names:
        assert norm(extract(old,name))==norm(new(cls,name)),name
        count+=1
print('Unchanged relocated/deduplicated methods:',count)
methods='\n'.join(new('NpcExchangeCommands',n) for n in ['PiersItem','PiersCheckItem','clearPiersSelection','validPiersSelection','비자야','piers'])+'\n'+'\n'.join(new('NpcLocalCommands',n) for n in ['poly','polyByKeplisha'])+'\n'+new('NpcQuestTransactions','마르바')
# Actual method bodies, minimal typed test doubles for game dependencies.
java='''import java.util.*;
public class Check {
static int checks;static void ck(boolean b){checks++;if(!b)throw new AssertionError("case "+checks);}
static class L1ItemInstance {int id,e;L1ItemInstance(int i,int n){id=i;e=n;}int getItemId(){return id;}int getEnchantLevel(){return e;}String getName(){return "item"+id;}L1ItemInstance getItem(){return this;}}
static class Inventory {List<L1ItemInstance> items=new ArrayList<>();boolean funds=true,consume=true,grant=true;int checks,deletes,debits,stores,lastId,lastEnchant,lastPrice;boolean checkItem(int id){return checkItem(id,1);}boolean checkItem(int id,int n){checks++;return id==40308?funds:items.stream().anyMatch(i->i.id==id);}boolean checkEnchant(int id,int e){lastId=id;lastEnchant=e;return items.stream().anyMatch(i->i.id==id&&i.e==e);}boolean DeleteEnchant(int id,int e){deletes++;return true;}boolean consumeItem(int id,int n){debits++;lastPrice=n;return consume;}List<L1ItemInstance> getItems(){return items;}L1ItemInstance storeItem(int id,int count){stores++;return grant?new L1ItemInstance(id,0):null;}}
static class L1Object {} static class L1NpcInstance extends L1Object {L1NpcInstance getNpcTemplate(){return this;}String get_name(){return "NPC";}}
static class L1PcInstance {int[] PiersItemId=new int[19],PiersEnchant=new int[19];Inventory inv=new Inventory();String page;String[] list;int rewards,rewardId,rewardEnchant,morph,morphId,morphType,errors;Inventory getInventory(){return inv;}}
static class CIOClient {L1PcInstance p;CIOClient(L1PcInstance p){this.p=p;}L1PcInstance getActiveChar(){return p;}}
static class SC_NPCTalkReturn {static void send(L1PcInstance p,int id,String s){p.page=s;}static void send(L1PcInstance p,int id,String s,String[] a){p.page=s;p.list=a;}}
static class SC_ServerMessage {static void send(L1PcInstance p,int id,String... s){p.errors++;}}
static class L1ItemId {static final int ADENA=40308;}
static class L1PolyMorph {static final int MORPH_BY_NPC=1,MORPH_BY_KEPLISHA=2;static void doPoly(L1PcInstance p,int id,int duration,int type,boolean b){p.morph++;p.morphId=id;p.morphType=type;ck(duration==1800&&!b);}}
static boolean createNewItem(L1PcInstance p,String name,int id,int count,int e){p.rewards++;p.rewardId=id;p.rewardEnchant=e;return true;}
'''+methods+'''
public static void main(String[] a){
int[] ids={13,81,162,177,194};
for(int n=0;n<=200;n++)for(int mode=0;mode<4;mode++)for(boolean funds:new boolean[]{false,true}){
 L1PcInstance p=new L1PcInstance();int e=mode<2?8:9;int price=e==8?5000000:10000000;int reward=30018+mode;
 Arrays.fill(p.PiersItemId,999);Arrays.fill(p.PiersEnchant,999);p.inv.funds=funds;
 for(int i=0;i<n;i++)p.inv.items.add(new L1ItemInstance(ids[i%5],e));
 PiersCheckItem(p,1,e,price,reward,e-1);
 int limit=funds?Math.min(n,10):0;
 ck((limit>0?"piers00":"piers04").equals(p.page));
 for(int i=0;i<10;i++){ck(p.PiersItemId[i]==(i<limit?ids[i%5]:0));ck(p.PiersEnchant[i]==(i<limit?e:0));}
 if(limit>0){ck(p.list.length==10&&p.PiersItemId[16]==reward&&p.PiersItemId[17]==e-1&&p.PiersItemId[18]==price);PiersItem(p,1,"NPC",p.PiersItemId[limit-1],p.PiersEnchant[limit-1],price);ck(p.rewards==1&&p.rewardId==reward&&p.rewardEnchant==e-1&&p.inv.debits==1&&p.inv.deletes==1&&"".equals(p.page));for(int x:p.PiersItemId)ck(x==0);for(int x:p.PiersEnchant)ck(x==0);}
 else {ck(p.PiersItemId[16]==0&&p.PiersItemId[17]==0&&p.PiersItemId[18]==0);}
 PiersItem(p,1,"NPC",0,0,0);ck("piers04".equals(p.page));ck(p.rewards==(limit>0?1:0));
}
for(int mask=0;mask<4;mask++)for(boolean funds:new boolean[]{false,true}){
 L1PcInstance p=new L1PcInstance();p.inv.funds=funds;Arrays.fill(p.PiersItemId,999);Arrays.fill(p.PiersEnchant,999);
 if((mask&1)!=0)p.inv.items.add(new L1ItemInstance(13,8));if((mask&2)!=0)p.inv.items.add(new L1ItemInstance(81,9));
 String[] pages={"piers04","piers03","piers02","piers01"};ck(piers(p,new L1NpcInstance(),1,"a","tail").equals(pages[funds?mask:0]));for(int i:p.PiersItemId)ck(i==0);for(int i:p.PiersEnchant)ck(i==0);
}
for(char c:new char[]{'A','B','C','D','a','b','0','9','X','０'}){
 L1PcInstance p=new L1PcInstance();p.inv.items.add(new L1ItemInstance(13,8));p.inv.items.add(new L1ItemInstance(81,9));
 String r=piers(p,new L1NpcInstance(),1,String.valueOf(c),"tail");if(c>='A'&&c<='D')ck(r.equals("tail")&&p.page.equals("piers00")&&p.PiersItemId[16]==30018+c-'A');else if(c=='a')ck(r.equals("piers01"));else if(c=='0'||c=='9')ck(r.equals("tail")&&"piers04".equals(p.page)&&p.rewards==0);else ck(r.equals("tail")&&p.page==null);
}
for(int kind=0;kind<2;kind++)for(boolean funds:new boolean[]{false,true})for(boolean consume:new boolean[]{false,true})for(int id=0;id<64;id++){
 L1PcInstance p=new L1PcInstance();p.inv.funds=funds;p.inv.consume=consume;if(kind==0)poly(new CIOClient(p),id);else polyByKeplisha(new CIOClient(p),id);ck(p.inv.debits==(funds?1:0));ck(p.morph==(funds&&consume?1:0));ck(p.errors==(funds&&consume?0:1));if(p.morph==1)ck(p.morphId==id&&p.morphType==kind+1);
}
poly(new CIOClient(null),1);polyByKeplisha(new CIOClient(null),1);
for(String s:new String[]{"A","B","C","D","a","b","c","d","","0","?"})for(boolean funds:new boolean[]{false,true}){
 L1PcInstance p=new L1PcInstance();p.inv.funds=funds;int ix="ABCD".indexOf(s);if(s.length()!=1)ix=-1;
 if(ix>=0)p.inv.items.add(new L1ItemInstance(ix<2?503:504,ix%2==0?8:9));String page=비자야(p,new L1NpcInstance(),s);boolean ok=ix>=0&&funds;ck(page.equals(ok?"vjaya05":"vjaya04"));ck(p.rewards==(ok?1:0));if(ok)ck(p.rewardId==(ix%2==0?30119:30120)&&p.inv.lastPrice==(ix%2==0?5000000:10000000));if(ix<0)ck(p.inv.checks==0&&p.inv.debits==0&&p.inv.deletes==0);
}
for(boolean grant:new boolean[]{false,true}){L1PcInstance p=new L1PcInstance();p.inv.grant=grant;String page=마르바(p,new L1NpcInstance(),"A");ck(page.equals(grant?"marba6":""));ck(p.errors==(grant?1:0));}
System.out.println("Typed Java assertions passed: "+checks);
}}
'''
with tempfile.TemporaryDirectory() as td:
    f=Path(td)/'Check.java';f.write_text(java,encoding='utf-8'); subprocess.run(['javac','-encoding','UTF-8','--release','21',str(f)],check=True);subprocess.run(['java','-cp',td,'Check'],check=True)

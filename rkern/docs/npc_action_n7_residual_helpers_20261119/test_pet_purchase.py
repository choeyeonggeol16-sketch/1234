from pathlib import Path
import tempfile, subprocess, re
root=Path(__file__).resolve().parents[2]
s=(root/'src/l1j/server/server/model/network/core/cs/packet/NpcPetCommands.java').read_text()
m=re.search(r'static void petbuy\([^\n]*\) \{',s);p=s.index('{',m.start());d=1;i=p+1
while d:
    if s[i]=='{':d+=1
    elif s[i]=='}':d-=1
    i+=1
method=s[m.start():i]
java='''import java.util.*;
class PetTest {
static int checks;static void ck(boolean b){checks++;if(!b)throw new AssertionError("case "+checks);}
static class L1ItemInstance {int getId(){return 123;}}
static class L1PcInventory {boolean has,consume,grant;int size,debits,grants,lastId,lastCount;boolean checkItem(int id,int n){return has;}boolean consumeItem(int id,int n){debits++;lastId=id;lastCount=n;return consume;}int getSize(){return size;}L1ItemInstance storeItem(int id,int n){grants++;ck(id==40314&&n==1);return grant?new L1ItemInstance():null;}}
static class L1NpcInstance {int cost;int getPetcost(){return cost;}L1NpcInstance getNpcTemplate(){return this;}int get_npcId(){return 46044;}}
static class L1MonsterInstance extends L1NpcInstance {boolean dead;boolean isDead(){return dead;}}
static class L1PetInstance extends L1NpcInstance {L1PetInstance(){}L1PetInstance(L1MonsterInstance m,L1PcInstance pc,int id){pc.pets++;ck(id==123);}}
static class Ability{int cha;int getTotalCha(){return cha;}}
static class L1PcInstance {int clazz,spawned,pets,messages,names;boolean hasType=true;L1NpcInstance spawnResult;L1PcInventory inv=new L1PcInventory();Ability a=new Ability();Map<Integer,L1NpcInstance> pl=new HashMap<>();L1PcInventory getInventory(){return inv;}Ability getAbility(){return a;}Map<Integer,L1NpcInstance> getPetList(){return pl;}boolean isCrown(){return clazz==1;}boolean isElf(){return clazz==2;}boolean isWizard(){return clazz==3;}boolean isDarkelf(){return clazz==4;}boolean isDragonknight(){return clazz==5;}boolean isBlackwizard(){return clazz==6;}}
static class CIOClient{L1PcInstance p;CIOClient(L1PcInstance p){this.p=p;}L1PcInstance getActiveChar(){return p;}}
static class SC_ServerMessage{static void send(L1PcInstance p,int n){p.messages++;ck(n==489);}}
static class SC_ItemName{static void send(L1PcInstance p,L1ItemInstance i){p.names++;}}
static class L1SpawnUtil{static L1NpcInstance spawn(L1PcInstance p,int id,int a,int b,int c){p.spawned++;return p.spawnResult;}}
static class L1PetType{}
static boolean typeExists;static class PetTypeTable{static PetTypeTable getInstance(){return new PetTypeTable();}L1PetType get(int n){return typeExists?new L1PetType():null;}}
'''+method+'''
public static void main(String[] args){
 petbuy(new CIOClient(null),46044,41159,1000);
 for(int clazz=0;clazz<=6;clazz++)for(int cha:new int[]{0,5,6,12,20})for(int cost:new int[]{0,6,18})for(boolean has:new boolean[]{false,true})for(boolean debit:new boolean[]{false,true})for(int spawn=0;spawn<4;spawn++)for(boolean type:new boolean[]{false,true})for(int size:new int[]{179,180})for(boolean grant:new boolean[]{false,true}){
 L1PcInstance p=new L1PcInstance();p.clazz=clazz;p.a.cha=cha;p.inv.has=has;p.inv.consume=debit;p.inv.size=size;p.inv.grant=grant;typeExists=type;L1PetInstance pet=new L1PetInstance();pet.cost=cost;p.pl.put(1,pet);if(spawn==1)p.spawnResult=new L1NpcInstance();if(spawn>=2){L1MonsterInstance m=new L1MonsterInstance();m.dead=spawn==3;p.spawnResult=m;}
 int available=cha+(clazz==0?0:clazz==2?12:6)-cost;boolean admitted=available/6>0;boolean paid=admitted&&has&&debit;boolean stored=paid&&spawn==2&&type&&available>=6&&size<180;
 petbuy(new CIOClient(p),46044,41159,1000);ck(p.inv.debits==(admitted&&has?1:0));ck(p.spawned==(paid?1:0));ck(p.inv.grants==(stored?1:0));ck(p.pets==(stored&&grant?1:0)&&p.names==p.pets);ck(p.messages==(admitted?0:1));if(admitted&&has)ck(p.inv.lastId==41159&&p.inv.lastCount==1000);
 }
 System.out.println("Typed pet assertions passed: "+checks);
}}
'''
with tempfile.TemporaryDirectory() as t:
 p=Path(t)/'PetTest.java';p.write_text(java);subprocess.run(['javac','-encoding','UTF-8','--release','21',str(p)],check=True);subprocess.run(['java','-cp',t,'PetTest'],check=True)

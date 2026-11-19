#!/usr/bin/env python3
"""Targeted Java 21 typed failure injection: production EnchantmentOutcomeEffects
and unmodified production method bodies for pet admission and pet-call sound.
Does not validate SQL, full dependency build or live game server.
"""
import re
import subprocess
import tempfile
from pathlib import Path
from textwrap import dedent
R=Path(__file__).resolve().parents[2]/'src/l1j/server/server/model/item/function'

def put(root,p,code):
    out=root/p;out.parent.mkdir(parents=True,exist_ok=True);out.write_text(dedent(code).lstrip(),encoding='utf8')

def extract(java,name):
    s=(R/java).read_text(encoding='utf8')
    start=s.index(name)
    brace=s.index('{',start); depth=1; end=brace+1
    while depth:
        depth += (s[end]=='{')-(s[end]=='}');end+=1
    return s[start:end]

with tempfile.TemporaryDirectory(prefix='rkern_item136_') as temp:
    src=Path(temp)/'src';classes=Path(temp)/'classes';classes.mkdir()
    put(src,'l1j/server/Config.java','''
        package l1j.server;
        public class Config { public static boolean MASTERENCHANTMESS=true;
            public static int LOGGING_WEAPON_ENCHANT=0, LOGGING_ARMOR_ENCHANT=0; }
    ''')
    put(src,'l1j/server/server/model/L1PcInventory.java','''
        package l1j.server.server.model;
        import l1j.server.server.model.Instance.*;
        public class L1PcInventory {
            public static final int COL_ENCHANTLVL=1;
            public int updated, saved, removed, equippedCalls;
            public boolean throwUpdate=false;
            public void updateItem(L1ItemInstance item,int col){ updated++; if(throwUpdate)throw new IllegalStateException("update"); }
            public void saveItem(L1ItemInstance item,int col){saved++;}
            public void removeItem(L1ItemInstance item,int amount){removed+=amount;}
            public void setEquipped(L1ItemInstance item,boolean b){equippedCalls++; item.equipped=b;}
        }
    ''')
    put(src,'l1j/server/server/model/Instance/L1ItemInstance.java','''
        package l1j.server.server.model.Instance;
        public class L1ItemInstance {
            public static class T { public int type2=2,type=1,id=20011,safe=0;
                public int getType2(){return type2;} public int getType(){return type;}
                public int getItemId(){return id;} public int get_safeenchant(){return safe;} }
            public final T t=new T(); public int enchant=1,count=1; public boolean equipped=true,identified=true;
            public T getItem(){return t;} public int getEnchantLevel(){return enchant;}
            public void setEnchantLevel(int i){enchant=i;}
            public String getName(){return "test";}
            public boolean isIdentified(){return identified;}
            public boolean isEquipped(){return equipped;}
            public int getCount(){return count;}
        }
    ''')
    put(src,'l1j/server/server/model/Instance/L1PcInstance.java','''
        package l1j.server.server.model.Instance;
        import l1j.server.server.model.L1PcInventory;
        public class L1PcInstance {
            public final L1PcInventory inv=new L1PcInventory();
            public final AC ac=new AC(); public final RES res=new RES(); public int saved;
            public L1PcInventory getInventory(){return inv;} public AC getAC(){return ac;}
            public RES getResistance(){return res;} public void saveInventory(){saved++;}
            public static class AC {public int delta; public void addAc(int x){delta+=x;}}
            public static class RES {public int delta;public void addMr(int x){delta+=x;}}
        }
    ''')
    put(src,'l1j/server/server/model/network/core/session/CIOClient.java','''
        package l1j.server.server.model.network.core.session;
        import l1j.server.server.model.Instance.L1PcInstance;
        public class CIOClient {public L1PcInstance pc; public CIOClient(L1PcInstance p){pc=p;}
            public L1PcInstance getActiveChar(){return pc;}}
    ''')
    put(src,'l1j/server/server/monitor/LoggerInstance.java','''
        package l1j.server.server.monitor;
        import l1j.server.server.model.Instance.*;
        public class LoggerInstance {
            private static final LoggerInstance S=new LoggerInstance();public int logs;
            public static LoggerInstance getInstance(){return S;}
            public void addEnchant(L1PcInstance p,L1ItemInstance i,boolean success){logs++;}
        }
    ''')
    for n,method in [('SC_SPMR','send'),('SC_OwnCharStatus','send')]:
        put(src,f'l1j/server/server/model/network/core/sc/packet/{n}.java',f'''
            package l1j.server.server.model.network.core.sc.packet;
            import l1j.server.server.model.Instance.L1PcInstance;
            public class {n} {{public static int calls; public static boolean fail;
                public static void {method}(L1PcInstance p) {{calls++; if(fail)throw new RuntimeException("{n} packet");}}}}
        ''')
    put(src,'l1j/server/server/model/network/core/sc/packet/SC_ServerMessage.java','''
        package l1j.server.server.model.network.core.sc.packet;
        import l1j.server.server.model.Instance.L1PcInstance;
        public class SC_ServerMessage {public static int calls;public static boolean fail;
            public static void send(L1PcInstance p,int id,String... args){calls++;if(fail)throw new RuntimeException("message");}}
    ''')
    for n,method in [('SC_SystemMessage','broadcast'),('SC_PacketBox','broadcastGreen')]:
        put(src,f'l1j/server/server/model/network/core/sc/packet/{n}.java',f'''
            package l1j.server.server.model.network.core.sc.packet;
            public class {n} {{public static int calls;public static boolean fail;
                public static void {method}(String s) {{calls++; if(fail)throw new RuntimeException("{n}");}}}}
        ''')
    # Compile original source (no trimmed or rewritten methods).
    put(src,'l1j/server/server/model/item/function/EnchantmentOutcomeEffects.java',(R/'EnchantmentOutcomeEffects.java').read_text('utf8'))
    put(src,'l1j/server/server/model/item/function/EnchantPetHarness.java',r'''
        package l1j.server.server.model.item.function;
        import l1j.server.server.model.Instance.*;
        import l1j.server.server.model.network.core.session.CIOClient;
        import l1j.server.server.model.network.core.sc.packet.*;
        import l1j.server.Config;
        public final class EnchantPetHarness {
            private static int checks;
            static void ck(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
            static void reset(){SC_ServerMessage.fail=false;SC_SPMR.fail=false;SC_OwnCharStatus.fail=false;
                SC_SystemMessage.fail=false;SC_PacketBox.fail=false; Config.MASTERENCHANTMESS=true;}
            public static void main(String[] args) {
                reset();
                L1PcInstance pc=new L1PcInstance(); L1ItemInstance item=new L1ItemInstance();
                SC_SPMR.fail=true; SC_OwnCharStatus.fail=true; SC_ServerMessage.fail=true;
                SC_SystemMessage.fail=true; SC_PacketBox.fail=true;
                int n=SC_ServerMessage.calls,m=SC_SPMR.calls,all=SC_SystemMessage.calls,green=SC_PacketBox.calls;
                EnchantmentOutcomeEffects.SuccessEnchant(pc,item,new CIOClient(pc),9);
                ck(item.enchant==10,"enchant remains applied"); ck(pc.inv.updated==1,"inventory update");
                ck(pc.ac.delta==-9,"armor correction");ck(pc.res.delta==9,"MR correction");
                ck(SC_SPMR.calls==m+1,"MR send attempted");
                ck(SC_ServerMessage.calls==n+1,"success notice attempted");
                ck(SC_SystemMessage.calls==all+1,"global notice attempted");
                ck(SC_PacketBox.calls==green+1,"green notice attempted");
                reset(); SC_ServerMessage.fail=true; SC_OwnCharStatus.fail=true;
                pc=new L1PcInstance();item=new L1ItemInstance(); item.t.id=1116;
                item.enchant=9; EnchantmentOutcomeEffects.FailureEnchant(pc,item,new CIOClient(pc));
                ck(item.enchant==0,"failed special resets enchant despite packet failure");
                ck(pc.inv.updated==1,"failed special updates inventory");
                ck(pc.inv.equippedCalls==1,"failed special unequips");
                reset(); SC_ServerMessage.fail=true; SC_OwnCharStatus.fail=true;
                pc=new L1PcInstance(); item=new L1ItemInstance();item.t.id=100;
                EnchantmentOutcomeEffects.FailureEnchant(pc,item,new CIOClient(pc));
                ck(pc.inv.removed==1,"destroy despite notice failure");
                reset(); pc=new L1PcInstance();item=new L1ItemInstance();pc.inv.throwUpdate=true;
                n=SC_ServerMessage.calls;all=SC_SystemMessage.calls;
                boolean threw=false;try {EnchantmentOutcomeEffects.SuccessEnchant(pc,item,new CIOClient(pc),9);}
                catch(IllegalStateException expected){threw=true;}
                ck(threw,"economy state exception not swallowed");
                ck(SC_ServerMessage.calls==n && SC_SystemMessage.calls==all,"no success notices before state persisted");
                System.out.println("ENCHANT_PROD_TYPED_CHECKS="+checks);
            }
        }
    ''')
    # Compile actual unchanged method bodies within a synthetic class to stub unrelated dependencies.
    pet_method=extract('PetWithdrawalItems.java','public static boolean withdrawPet(')
    sound_method=extract('PetAndSummonItems.java','static ItemActionResult handleSpecialFlute(')
    put(src,'l1j/server/server/model/item/function/PetWithdrawalHarness.java','''
        package l1j.server.server.model.item.function;
        import java.util.*;
        public class PetWithdrawalHarness {
            static class PetWithdrawalItems {
            '''+pet_method+'''
            }
            static class PetAndSummonItems {
            private static final java.util.logging.Logger ITEM_USE2_LOG=java.util.logging.Logger.getLogger("pet");
            '''+sound_method+'''
            }
            static class ItemActionResult {static final ItemActionResult COMPLETE=new ItemActionResult();}
            static class ItemActionContext { L1PcInstance p; L1PcInstance player(){return p;} }
            static class SC_Sound {static int sent,broadcast; static boolean fail;
                static void send(L1PcInstance pc,int id){sent++;if(fail)throw new RuntimeException("send");}
                static void broadcast(L1PcInstance pc,int id){broadcast++;if(fail)throw new RuntimeException("broadcast");}}
            static class SC_ServerMessage {static void send(L1PcInstance pc,int id) {}}
            static class L1ItemInstance {int getItemId(){return 40314;}}
            static class Inventory {L1ItemInstance item=new L1ItemInstance();
                L1ItemInstance getItem(int id){return item;}}
            static class L1Npc {}
            static class L1Pet {int get_npcid(){return 1;}}
            static class L1NpcInstance {int getPetcost(){return 1;}}
            static class L1PetInstance extends L1NpcInstance {static int created,calls;
                L1PetInstance(L1Npc temp,L1PcInstance pc,L1Pet p){created++;}
                int getItemObjId(){return 123;}
                void setPetcost(int cost){}
                void call(){calls++;}}
            static class PetTable {static final PetTable INSTANCE=new PetTable();static L1Pet pet;
                static PetTable getInstance(){return INSTANCE;}
                L1Pet getTemplate(int obj){return pet;}}
            static class NpcTable {static final NpcTable INSTANCE=new NpcTable();static L1Npc npc;
                static NpcTable getInstance(){return INSTANCE;}
                L1Npc getTemplate(int id){return npc;}}
            static class L1PcInstance {Map map=new Map();Inventory inventory=new Inventory();int mapId,cha=24;
                Inventory getInventory(){return inventory;}
                java.util.Map<Integer,L1NpcInstance> pets=new java.util.HashMap<>();
                Map getMap(){return map;} int getMapId(){return mapId;}
                boolean isInWarArea(){return false;}
                java.util.Map<Integer,L1NpcInstance> getPetList(){return pets;}
                Ability getAbility(){return new Ability(cha);}
                boolean isCrown(){return false;} boolean isElf(){return false;}
                boolean isWizard(){return false;} boolean isDarkelf(){return false;}
                boolean isDragonknight(){return false;} boolean isBlackwizard(){return false;}}
            static class Map {boolean isTakePets(){return true;}}
            static class Ability {int cha;Ability(int c){cha=c;}int getTotalCha(){return cha;}}
            static int checks;
            static void ck(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
            public static void main(String[] args){
                L1PcInstance pc=new L1PcInstance();PetTable.pet=null;NpcTable.npc=new L1Npc();
                ck(!PetWithdrawalItems.withdrawPet(pc,777),"no pet row must not succeed");
                ck(L1PetInstance.created==0,"missing pet row no spawned pet");
                PetTable.pet=new L1Pet();NpcTable.npc=null;
                ck(!PetWithdrawalItems.withdrawPet(pc,777),"no NPC template must not succeed");
                NpcTable.npc=new L1Npc();
                ck(PetWithdrawalItems.withdrawPet(pc,777),"valid pet can be withdrawn");
                ck(L1PetInstance.created==1,"pet constructed once");
                ItemActionContext use=new ItemActionContext();use.p=pc;
                pc.pets.put(8,new L1PetInstance(new L1Npc(),pc,new L1Pet()));
                SC_Sound.fail=true;
                ck(PetAndSummonItems.handleSpecialFlute(use)==ItemActionResult.COMPLETE,"flute completes");
                ck(L1PetInstance.calls==1,"pet call not skipped by sound failure");
                ck(SC_Sound.sent==1 && SC_Sound.broadcast==1,"both audio sends attempted");
                System.out.println("PET_METHOD_TYPED_CHECKS="+checks);
            }
        }
    ''')
    files=[str(p) for p in src.rglob('*.java')]
    result=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes)]+files, capture_output=True,text=True)
    if result.returncode:
        print(result.stdout, result.stderr)
        raise SystemExit(result.returncode)
    for name in ['l1j.server.server.model.item.function.EnchantPetHarness',
                 'l1j.server.server.model.item.function.PetWithdrawalHarness']:
        test=subprocess.run(['java','-cp',str(classes),name],capture_output=True,text=True)
        if test.returncode:
            print(test.stdout,test.stderr)
            raise SystemExit(test.returncode)
        print(test.stdout.strip())
print('TYPED_JAVA21_OK')

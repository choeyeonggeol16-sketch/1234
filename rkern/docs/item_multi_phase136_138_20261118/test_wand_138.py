#!/usr/bin/env python3
"""Compile and execute actual production useNpcSpownWand method with Java 21 stubs."""
import subprocess,tempfile
from pathlib import Path
from textwrap import dedent
R=Path(__file__).resolve().parents[2]/'src/l1j/server/server/model/item/function'
s=(R/'NpcWandAndSpawnItems.java').read_text('utf8')
st=s.index('public static void useNpcSpownWand(');start=s.index('{',st);depth=1;i=start+1
while depth:
    depth+=(s[i]=='{')-(s[i]=='}');i+=1
method=s[st:i]
with tempfile.TemporaryDirectory(prefix='rkern138wand_') as temp:
    root=Path(temp)/'src';out=Path(temp)/'bin';out.mkdir()
    def put(path,txt):
        p=root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(dedent(txt).lstrip(),'utf8')
    put('l1j/server/server/model/Instance/L1NpcInstance.java','''
        package l1j.server.server.model.Instance;
        public class L1NpcInstance {
            public static int constructions,timed;
            public static boolean failTimed;
            public L1NpcInstance(Object template){constructions++;}
            public void setId(int id){} public void setMap(int map){} public void setX(int x){}
            public void setY(int y){} public void setHomeX(int x){} public void setHomeY(int y){}
            public void setHeading(int hd){}
            public void startTimedDelete(int ms){timed++;if(failTimed)throw new RuntimeException("timer");}
        }
    ''')
    put('l1j/server/server/model/item/function/WandHarness.java','''
        package l1j.server.server.model.item.function;
        import l1j.server.server.model.Instance.L1NpcInstance;
        import java.lang.reflect.Constructor;
        import java.util.logging.*;
        public class WandHarness {
            static class NpcWandAndSpawnItems {
        '''+method+'''
            }
            static class L1Npc {String getImpl(){return "L1Npc";}}
            static class NpcTable {static L1Npc template;
                static NpcTable getInstance(){return new NpcTable();}
                L1Npc getTemplate(int id){return template;}}
            static class L1ItemInstance {int id=1,charges;L1ItemInstance(int x){charges=x;}
                int getId(){return id;} int getChargeCount(){return charges;}
                void setChargeCount(int x){charges=x;}}
            static class L1PcInventory {static final int COL_CHARGE_COUNT=1;
                L1ItemInstance item;int removes,updates;
                L1ItemInstance getItem(int id){return item;}
                void removeItem(L1ItemInstance it){removes++;item=null;}
                void updateItem(L1ItemInstance it,int column){updates++;}}
            static class L1PcInstance {L1PcInventory inv=new L1PcInventory();
                L1PcInventory getInventory(){return inv;}
                int getMapId(){return 4;}int getX(){return 123;}int getY(){return 321;}}
            static class IdFactory {static IdFactory getInstance(){return new IdFactory();}
                int nextId(){return 100;}}
            static class L1World {static boolean fail;static int stored;
                static L1World getInstance(){return new L1World();}
                void storeAndEnterWorld(L1NpcInstance npc){if(fail)throw new RuntimeException("world");stored++;}}
            static class ActionCodes {static final int ACTION_Wand=1;}
            static class SC_AttackStatus {static boolean fail;static int packets;
                static void sendAndBroadcast(L1PcInstance p,int a,int b,boolean c){packets++;if(fail)throw new RuntimeException("packet");}}
            static int checks;static void ck(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
            public static void main(String[] args){
                java.util.logging.Logger.getLogger(NpcWandAndSpawnItems.class.getName()).setLevel(Level.OFF);
                L1PcInstance pc=new L1PcInstance();L1ItemInstance item=new L1ItemInstance(2);pc.inv.item=item;
                NpcTable.template=null;NpcWandAndSpawnItems.useNpcSpownWand(pc,1,item);
                ck(item.charges==2 && L1World.stored==0,"invalid NPC template");
                NpcTable.template=new L1Npc();
                L1World.fail=true;NpcWandAndSpawnItems.useNpcSpownWand(pc,1,item);
                ck(item.charges==2 && L1World.stored==0,"spawn failure doesn't debit");
                L1World.fail=false;SC_AttackStatus.fail=true;
                NpcWandAndSpawnItems.useNpcSpownWand(pc,1,item);
                ck(item.charges==1 && L1World.stored==1,"spawned and charged once");
                ck(pc.inv.updates==1 && SC_AttackStatus.packets==1,"updated despite visual failure");
                pc.inv.item=new L1ItemInstance(1);NpcWandAndSpawnItems.useNpcSpownWand(pc,1,item);
                ck(item.charges==1 && L1World.stored==1,"stale/unowned wand refused");
                pc.inv.item=item;
                NpcWandAndSpawnItems.useNpcSpownWand(pc,1,item);
                ck(item.charges==0 && pc.inv.removes==1,"last use removes wand");
                int n=L1World.stored;NpcWandAndSpawnItems.useNpcSpownWand(pc,1,item);
                ck(L1World.stored==n,"spent wand refused");
                System.out.println("WAND_METHOD_TYPED_CHECKS="+checks);
            }
        }
    ''')
    files=list(root.rglob('*.java'))
    r=subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(out)]+[str(x) for x in files],capture_output=True,text=True)
    if r.returncode:print(r.stderr);raise SystemExit(r.returncode)
    r=subprocess.run(['java','-cp',str(out),'l1j.server.server.model.item.function.WandHarness'],capture_output=True,text=True)
    if r.returncode:print(r.stderr,r.stdout);raise SystemExit(r.returncode)
    print(r.stdout.strip())

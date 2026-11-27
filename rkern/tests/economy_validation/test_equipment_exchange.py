"""Exercise the production exchange handler against an isolated receipt boundary."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

ROOT = Path(__file__).resolve().parents[2]
SERVER = "l1j.server.server"
MODEL = SERVER + ".model"
SHOP = MODEL + ".shop"
ACTORS = MODEL + ".Instance"
NET = MODEL + ".network.core.connection"
PRODUCTION = ROOT / "src/l1j/server/server/model/shop/CEquipmentExchange.java"

with TemporaryDirectory(prefix="rkern-equipment-exchange-") as temp:
    root = Path(temp)

    def write(package, name, source):
        path = root.joinpath(*package.split("."), name + ".java")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(f"package {package};\n" + source, encoding="utf-8")

    config = "\n".join(f'public static String ChangeItemList{n} = "31";' for n in range(1, 21))
    write("l1j.server", "Config", "public class Config {" + config + "}")
    write(ACTORS, "L1ItemInstance", """
        public class L1ItemInstance {
            public final int id, item;
            public int count=1, enchant=7, bless=1, element=8, blessing=2, level=3;
            public boolean equipped, identified;
            public Object ends;
            public L1ItemInstance(int id,int item){this.id=id;this.item=item;}
            public int getId(){return id;} public int getItemId(){return item;} public int getCount(){return count;}
            public boolean isEquipped(){return equipped;} public Object getEndTime(){return ends;}
            public int getEnchantLevel(){return enchant;} public void setEnchantLevel(int n){enchant=n;}
            public int getBless(){return bless;} public void setBless(int n){bless=n;}
            public int getAttrEnchantLevel(){return element;} public void setAttrEnchantLevel(int n){element=n;}
            public int get_bless_level(){return blessing;} public void set_bless_level(int n){blessing=n;}
            public int get_item_level(){return level;} public void set_item_level(int n){level=n;}
            public void setIdentified(boolean n){identified=n;} public String getLogName(){return "reward";}
        }
    """)
    write(ACTORS, "L1PcInstance", f"""
        import java.util.HashMap;
        import {NET}.CUserSocket;
        public class L1PcInstance {{
            public final Inventory inv=new Inventory(); public final CUserSocket session=new CUserSocket(this);
            public boolean busy; public int trade;
            public Inventory getInventory(){{return inv;}} public CUserSocket getNetConnection(){{return session;}}
            public int getTradeID(){{return trade;}} public boolean isItemTransferBusy(){{return busy;}}
            public int getId(){{return 1;}} public String getName(){{return "owner";}}
            public static class Inventory extends HashMap<Integer,L1ItemInstance> {{
                public L1ItemInstance GetByServerID(int id){{return get(id);}} public int getSize(){{return size();}}
            }}
        }}
    """)
    write(NET, "CUserSocket", f"""
        import {ACTORS}.L1PcInstance;
        import {SHOP}.NpcShopView;
        public class CUserSocket {{
            public NpcShopView view; public boolean ready=true; public final L1PcInstance owner;
            public CUserSocket(L1PcInstance owner){{this.owner=owner;}}
            public boolean ownsReadyGameSession(){{return ready;}} public L1PcInstance getActiveChar(){{return owner;}}
            public boolean isCurrentCharacter(L1PcInstance pc){{return owner==pc&&ready;}}
            public NpcShopView getNpcShopView(){{return view;}}
            public void clearNpcShopView(NpcShopView old){{takeNpcShopView(old);}}
            public boolean takeNpcShopView(NpcShopView old){{if(view!=old)return false;view=null;return true;}}
        }}
    """)
    write(SHOP, "NpcShopView", f"""
        import {NET}.CUserSocket;
        import {ACTORS}.L1PcInstance;
        public class NpcShopView {{
            public final int id; public final CEquipmentExchange.Selection selection; public boolean current=true;
            private final Shop shop=new Shop();
            public NpcShopView(int id,CEquipmentExchange.Selection selection){{this.id=id;this.selection=selection;}}
            public CEquipmentExchange.Selection equipmentExchange(){{return selection;}}
            public int resultId(){{return id;}} public int shopId(){{return selection.shopId();}}
            public boolean currentFor(CUserSocket s,L1PcInstance pc){{return current&&s.isCurrentCharacter(pc);}}
            public Offer offer(int row){{return row==0?new Offer():null;}}
            public Shop currentShop(){{return current?shop:null;}}
            public int catalogIndex(Object list,int row,Offer offer){{return row;}}
            public static class Shop {{
                public boolean canOffer(Offer item,L1PcInstance pc){{return true;}}
                public Object getSellingItems(){{return null;}}
            }}
            public static class Offer {{public Offer getItem(){{return this;}} public int getItemId(){{return 44;}}}}
        }}
    """)
    write(MODEL + ".network.core.sc.packet", "SC_PremiumShopSellList", f"""
        import {NET}.CUserSocket;
        import {SHOP}.*;
        public class SC_PremiumShopSellList {{
            static int next=100;
            public static boolean sendEquipmentExchange(CUserSocket s,CEquipmentExchange.Selection e){{
                s.view=new NpcShopView(++next,e);return true;
            }}
        }}
    """)
    write(MODEL + ".network.core.sc.packet", "SC_SystemMessage", f"""
        import {ACTORS}.L1PcInstance;
        public class SC_SystemMessage {{public static void send(L1PcInstance pc,String text){{}}}}
    """)
    write(SERVER + ".datatables", "CObjectDB", f"""
        import {ACTORS}.L1ItemInstance;
        public class CObjectDB {{
            public static CObjectDB LoadItem(){{return new CObjectDB();}}
            public L1ItemInstance createItem(int id){{return new L1ItemInstance(99,id);}}
        }}
    """)
    write(SERVER + ".storage", "CacheD", """
        public class CacheD {public static int accountOrderKey(String s,int id){return id;}}
    """)
    write(SERVER + ".logd", "CLogD", """
        public class CLogD {
            public static int records;
            public static CLogD getInstance(){return new CLogD();}
            public void addShop(String a,int b,long c,String d,String e){records++;}
        }
    """)
    write(MODEL + ".inventory", "L1InventoryTransaction", f"""
        import {ACTORS}.*;
        import java.util.ArrayList;
        public class L1InventoryTransaction implements AutoCloseable {{
            public static L1InventoryTransaction pending;
            public final L1PcInstance pc; final ArrayList<L1ItemInstance> debits=new ArrayList<>();
            public static boolean capacity=true;
            L1ItemInstance reward; Runnable success,failure;
            L1InventoryTransaction(L1PcInstance pc){{this.pc=pc;pc.busy=true;}}
            public static L1InventoryTransaction begin(L1PcInstance pc,int key){{return new L1InventoryTransaction(pc);}}
            public boolean debit(L1ItemInstance item,int n){{debits.add(item);return true;}}
            public L1ItemInstance grant(L1ItemInstance item){{reward=item;return capacity?item:null;}}
            public void onFailure(Runnable r){{failure=r;}}
            public void commit(Runnable r){{success=r;pending=this;}}
            public void close(){{if(success==null)pc.busy=false;}}
            public static void complete(boolean committed){{
                var tx=pending;pending=null;
                if(committed){{
                    for(var item:tx.debits)if(--item.count==0)tx.pc.inv.remove(item.id);
                    tx.pc.inv.put(tx.reward.id,tx.reward);tx.success.run();
                }} else tx.failure.run();
                tx.pc.busy=false;
            }}
        }}
    """)
    write(SHOP, "EquipmentExchangeRegression", f"""
        import {ACTORS}.*;
        import {MODEL}.inventory.L1InventoryTransaction;
        import {SERVER}.logd.CLogD;
        public class EquipmentExchangeRegression {{
            public static void main(String[] args){{
                var actor=new L1PcInstance();var ticket=new L1ItemInstance(4,77100);var source=new L1ItemInstance(5,31);
                var other=new L1ItemInstance(6,31);
                actor.inv.put(4,ticket);actor.inv.put(5,source);actor.inv.put(6,other);
                CEquipmentExchange.open(actor,ticket,source);
                var first=actor.session.view;
                check(first.selection.shopId()==88001,"ticket uses former catalog");
                CEquipmentExchange.open(actor,ticket,source);
                var current=actor.session.view;
                check(current!=first&&!CEquipmentExchange.handleResult(actor.session,actor,first.id,0,1,0,1),
                        "stale view scope");
                actor.inv.remove(5);
                select(actor);
                check(L1InventoryTransaction.pending==null&&other.count==1,"same-template peer cannot stand in for source");
                actor.inv.put(5,source);
                CEquipmentExchange.open(actor,ticket,source);
                ticket.equipped=true;select(actor);ticket.equipped=false;
                check(L1InventoryTransaction.pending==null,"ticket is revalidated");
                L1InventoryTransaction.capacity=false;
                CEquipmentExchange.open(actor,ticket,source);select(actor);
                check(L1InventoryTransaction.pending==null&&ticket.count==1&&source.count==1,"capacity failure is atomic");
                L1InventoryTransaction.capacity=true;
                CEquipmentExchange.open(actor,ticket,source);select(actor);
                check(actor.busy&&ticket.count==1&&source.count==1&&!actor.inv.containsKey(99)&&CLogD.records==0,
                        "no mutation before DB completion");
                L1InventoryTransaction.complete(false);
                check(actor.session.view==null&&ticket.count==1&&source.count==1&&CLogD.records==0,"rollback");
                CEquipmentExchange.open(actor,ticket,source);select(actor);select(actor);
                L1InventoryTransaction.complete(true);
                var reward=actor.inv.get(99);
                check(reward!=null&&reward.enchant==7&&reward.bless==1&&reward.element==8&&reward.level==3
                        &&reward.blessing==2&&reward.identified,"committed result inherits actual source");
                check(!actor.inv.containsKey(4)&&!actor.inv.containsKey(5)&&other.count==1&&CLogD.records==1,
                        "commit debits both exact instances and logs once");
                System.out.println("PASS equipment view scope, ownership, capacity, rollback and committed inheritance");
            }}
            static void select(L1PcInstance actor){{
                var current=actor.session.view;
                check(CEquipmentExchange.handleResult(actor.session,actor,current.id,0,1,0,1),"selection route");
            }}
            static void check(boolean valid,String reason){{if(!valid)throw new AssertionError(reason);}}
        }}
    """)
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(root),
                    str(PRODUCTION), *(str(p) for p in root.rglob("*.java"))], check=True)
    subprocess.run(["java", "-cp", str(root), SHOP + ".EquipmentExchangeRegression"], check=True)

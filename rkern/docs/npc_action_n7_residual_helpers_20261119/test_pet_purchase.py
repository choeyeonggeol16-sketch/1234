"""Run the production pet purchase handler against controlled receipts."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[2]
source = (root / 'src/l1j/server/server/model/network/core/cs/packet/NpcPetCommands.java').read_text(encoding='utf-8')
method = source[source.index('    static void petbuy('):source.index('    static void withdrawPets(')]

java = r'''
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

public class PetTest {
    static final Logger LOG = Logger.getLogger("PetTest");
    static int checks;
    static L1PcInstance pc;
    static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }

    static class L1NpcInstance {
        int cost = 6;
        int getPetcost() { return cost; }
        boolean isDead() { return false; }
    }

    static final class L1MonsterInstance extends L1NpcInstance {
        @Override boolean isDead() { return pc.deadTemplate; }
    }

    static final class L1PetInstance extends L1NpcInstance {
        int getId() { return 31; }
        static L1PetInstance prepareNpcPurchase(L1NpcInstance target, L1PcInstance owner, int id) {
            check(owner == pc && id == 42);
            if (owner.failConstruct) throw new IllegalStateException();
            return new L1PetInstance();
        }
        void enterPurchasedPetWorld() {
            if (pc.failWorld) throw new IllegalStateException();
            pc.worldPets++;
        }
    }

    static final class L1PcInstance {
        final Map<Integer, L1NpcInstance> pets = new HashMap<>();
        int classId, charisma, size, worldPets, paid, amulets, rows, published, messages, names;
        boolean available = true, petType = true, template = true, grant = true;
        boolean deadTemplate, failConstruct, failWorld;
        Runnable finish;
        L1PcInstance getAbility() { return this; }
        int getTotalCha() { return charisma; }
        boolean isCrown() { return classId == 1; }
        boolean isElf() { return classId == 2; }
        boolean isWizard() { return classId == 3; }
        boolean isDarkelf() { return classId == 4; }
        boolean isDragonknight() { return classId == 5; }
        boolean isBlackwizard() { return classId == 6; }
        Map<Integer, L1NpcInstance> getPetList() { return pets; }
        L1PcInstance getInventory() { return this; }
        int getSize() { return size; }
        L1ItemInstance getItem(int id) { return amulets > 0 && id == 42 ? new L1ItemInstance() : null; }
    }

    record CIOClient(L1PcInstance getActiveChar) {}
    record L1Pet(int npc, int id) {}
    static final class L1ItemInstance {
        boolean isStackable() { return false; }
        int getId() { return 42; }
    }
    static final class PetTypeTable {
        static PetTypeTable getInstance() { return new PetTypeTable(); }
        Object get(int id) { return pc.petType ? this : null; }
    }
    static final class NpcTable {
        static NpcTable getInstance() { return new NpcTable(); }
        L1NpcInstance newNpcInstance(int id) { return pc.template ? new L1MonsterInstance() : null; }
    }
    static final class PetTable {
        static PetTable getInstance() { return new PetTable(); }
        static L1Pet newPetRecord(L1NpcInstance target, int id, int item) { return new L1Pet(id, item); }
        static void insertPet(Object connection, L1Pet pet) {
            check(pet.npc() == 31 && pet.id() == 42);
            pc.rows++;
        }
        static void publishPurchasedPet(L1Pet pet) { pc.published++; }
    }
    static final class SC_SystemMessage {
        static void send(L1PcInstance pc, String message) { pc.messages++; }
    }
    static final class SC_ServerMessage {
        static void send(L1PcInstance pc, int id) { check(id == 489 || id == 82); pc.messages++; }
    }
    static final class SC_ItemName {
        static void send(L1PcInstance pc, L1ItemInstance item) { pc.names++; }
    }

    static final class L1InventoryTransaction implements AutoCloseable {
        Runnable durable, failure;
        java.util.function.Consumer<Object> sql;
        int price;
        static L1InventoryTransaction begin(L1PcInstance owner, int npc) {
            check(pc == owner && npc == 46044);
            return new L1InventoryTransaction();
        }
        boolean debit(int item, int price) {
            check(item == 41159 && price == 1000);
            this.price = price;
            return pc.available;
        }
        L1ItemInstance grant(int item, int quantity) {
            check(item == 40314 && quantity == 1);
            return pc.grant ? new L1ItemInstance() : null;
        }
        void requireTables(String table) { check(table.equals("pets")); }
        void sql(java.util.function.Consumer<Object> sql) { this.sql = sql; }
        void onDurableCommit(Runnable callback) { durable = callback; }
        void onFailure(Runnable callback) { failure = callback; }
        void commitAfterInventoryRelease(Runnable effect) {
            pc.finish = () -> {
                sql.accept(this);
                pc.paid += price;
                pc.amulets++;
                durable.run();
                effect.run();
            };
            rollback = failure;
        }
        @Override public void close() {}
    }

    static Runnable rollback;
''' + method + r'''

    static void buy() {
        rollback = null;
        petbuy(new CIOClient(pc), 46044, 41159, 1000);
        check(pc.paid == 0 && pc.amulets == 0 && pc.rows == 0 && pc.worldPets == 0);
    }

    public static void main(String[] args) {
        LOG.setLevel(Level.OFF);
        for (int classId = 0; classId <= 6; classId++) {
            for (int charisma : new int[]{0, 5, 6, 12, 20}) {
                for (int petCost : new int[]{0, 6, 18}) {
                    pc = new L1PcInstance();
                    pc.classId = classId;
                    pc.charisma = charisma;
                    var pet = new L1PetInstance();
                    pet.cost = petCost;
                    pc.pets.put(1, pet);
                    buy();
                    int total = charisma + (classId == 0 ? 0 : classId == 2 ? 12 : 6) - petCost;
                    check((pc.finish != null) == (total >= 6));
                }
            }
        }
        for (int failure = 0; failure < 7; failure++) {
            pc = new L1PcInstance();
            pc.charisma = 12;
            switch (failure) {
                case 0 -> pc.size = 180;
                case 1 -> pc.petType = false;
                case 2 -> pc.template = false;
                case 3 -> pc.deadTemplate = true;
                case 4 -> pc.available = false;
                case 5 -> pc.grant = false;
                case 6 -> pc.failConstruct = true;
                default -> throw new AssertionError();
            }
            buy();
            check(pc.finish == null && rollback == null);
        }
        pc = new L1PcInstance();
        pc.charisma = 12;
        buy();
        rollback.run();
        check(pc.messages == 1 && pc.paid == 0 && pc.amulets == 0 && pc.rows == 0 && pc.worldPets == 0);
        for (boolean failWorld : new boolean[]{true, false}) {
            pc = new L1PcInstance();
            pc.charisma = 12;
            pc.failWorld = failWorld;
            buy();
            pc.finish.run();
            check(pc.paid == 1000 && pc.amulets == 1 && pc.rows == 1 && pc.published == 1);
            check(pc.worldPets == (failWorld ? 0 : 1) && pc.names == 1);
        }
        System.out.println("PASS pet purchase checks=" + checks);
    }
}
'''

with tempfile.TemporaryDirectory() as tmp:
    file = Path(tmp, 'PetTest.java')
    file.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', tmp, str(file)], check=True)
    subprocess.run(['java', '-cp', tmp, 'PetTest'], check=True)

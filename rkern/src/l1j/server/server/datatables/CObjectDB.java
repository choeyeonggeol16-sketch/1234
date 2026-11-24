package l1j.server.server.datatables;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.IdFactory;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1MerchantInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1GuardInstance;
import l1j.server.server.model.Instance.L1TeleporterInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1FieldObjectInstance;
import l1j.server.server.model.Instance.L1PeopleInstance;
import l1j.server.server.model.Instance.L1CastleGuardInstance;
import l1j.server.server.model.Instance.L1HousekeeperInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1Npc;

/**
 * Prototype and creation owner. Item IDs and NPC IDs are separate namespaces.
 * The existing SQL loaders are retained as the typed stores, not copied.
 * Live object identity and registration remain owned by CWorld/IdFactory.
 */
public final class CObjectDB {
    private static final Logger LOG = Logger.getLogger(CObjectDB.class.getName());

    private CObjectDB() {}

    // Independent lazy holders preserve the existing startup dependencies.
    private static final class Items {
        private static volatile ItemTable current = new ItemTable();
    }

    private static final class Npcs {
        private static volatile NpcTable current = new NpcTable();
    }

    private static final class Sets {
        private static volatile ArmorSetTable current = new ArmorSetTable();
    }

    public static ItemTable LoadItem() {
        return Items.current;
    }

    public static NpcTable LoadNPC() {
        return Npcs.current;
    }

    public static ArmorSetTable LoadSetItem() {
        return Sets.current;
    }

    // Publish one completed loader generation; do not mutate an older reader's
    // constructor cache or set list. SQL failure policy stays in the loaders.
    public static synchronized void ReloadItem() {
        Items.current = new ItemTable();
    }

    public static synchronized void ReloadNPC() {
        Npcs.current = new NpcTable();
    }

    public static synchronized void ReloadSetItem() {
        Sets.current = new ArmorSetTable();
    }

    public static L1Item GetItem(int classId) {
        return LoadItem().getTemplate(classId);
    }

    public static L1Npc GetNPC(int classId) {
        return LoadNPC().getTemplate(classId);
    }

    public static L1ItemInstance CreateItem(int classId) {
        return CreateItem(classId, true);
    }

    public static L1ItemInstance CreateItem(int classId, boolean identified) {
        return CreateItem(GetItem(classId), identified);
    }

    public static L1ItemInstance CreateItem(L1Item template, boolean identified) {
        return CreateItem(template, true, identified);
    }

    public static L1ItemInstance CreateItem(L1Item template) {
        // Legacy template/name overloads leave the instance's default unchanged.
        return CreateItem(template, false, false);
    }

    private static L1ItemInstance CreateItem(L1Item template, boolean setIdentified, boolean identified) {
        if (template == null) return null;
        L1ItemInstance item = new L1ItemInstance();
        item.setId(IdFactory.getInstance().nextId());
        item.setItem(template);
        item.setBless(template.getBless());
        if (setIdentified) item.setIdentified(identified);
        L1World.getInstance().storeObject(item);
        return item;
    }

    public static L1NpcInstance CreateNPC(int classId) {
        NpcTable generation = LoadNPC();
        return CreateNPC(generation, generation.getTemplate(classId));
    }

    public static L1NpcInstance CreateNPC(L1Npc template) {
        return CreateNPC(LoadNPC(), template);
    }

    static L1NpcInstance CreateNPC(NpcTable generation, L1Npc template) {
        if (template == null) return null;
        try {
            NpcConstructor constructor = generation.resolveConstructor(template.getImpl());
            return constructor == null ? null : constructor.create(template);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            LOG.log(Level.SEVERE, "CreateNPC failed - npcId: " + template.get_npcId(), failure);
            return null;
        }
    }

    @FunctionalInterface
    interface NpcConstructor {
        L1NpcInstance create(L1Npc template) throws Throwable;
    }

    static NpcConstructor ResolveNPCConstructor(String implName) {
        String name = implName.isEmpty() ? "Monster" : implName;
        String qualified = "l1j.server.server.model.Instance." + name
                + (name.endsWith("Instance") ? "" : "Instance");
        try {
            Class<?> type;
            try {
                type = Class.forName(qualified);
            } catch (ClassNotFoundException missing) {
                // Preserve the legacy fallback for an unknown implementation.
                type = L1MonsterInstance.class;
            }
            if (type == L1MonsterInstance.class) return L1MonsterInstance::new;
            if (type == L1MerchantInstance.class) return L1MerchantInstance::new;
            if (type == L1NpcInstance.class) return L1NpcInstance::new;
            if (type == L1GuardInstance.class) return L1GuardInstance::new;
            if (type == L1TeleporterInstance.class) return L1TeleporterInstance::new;
            if (type == L1NpcShopInstance.class) return L1NpcShopInstance::new;
            if (type == L1FieldObjectInstance.class) return L1FieldObjectInstance::new;
            if (type == L1PeopleInstance.class) return L1PeopleInstance::new;
            if (type == L1CastleGuardInstance.class) return L1CastleGuardInstance::new;
            if (type == L1HousekeeperInstance.class) return L1HousekeeperInstance::new;
            // Preserve the legacy first-public-constructor selection for other
            // implementations. Adapt once per loader generation, not per spawn.
            MethodHandle handle = MethodHandles.publicLookup()
                    .unreflectConstructor(type.getConstructors()[0])
                    .asType(MethodType.methodType(L1NpcInstance.class, L1Npc.class));
            return template -> (L1NpcInstance) handle.invokeExact(template);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOG.log(Level.SEVERE, "NPC constructor resolution failed: " + implName, failure);
            return null;
        }
    }
}

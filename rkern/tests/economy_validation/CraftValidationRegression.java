package l1j.server.server.packets.Protocol.MainServer.Proto;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.CraftInputItem;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.CraftInputItemSlotInfo;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.CraftInputList;
import l1j.server.server.shared.arrangement.ArrangeParsee;
import l1j.server.server.shared.arrangement.ArrangeParseeFactory;
import l1j.server.server.templates.L1EtcItem;

public final class CraftValidationRegression {
    public static void main(String[] args) throws Exception {
        var inputs = new CraftInputList();
        inputs.add_arr_input_item(material(1, 10, 4, 1, 7, 0));
        inputs.add_arr_input_item(material(1, 11, 2, 3, 0, 50));
        inputs.add_arr_input_item(material(2, 20, 9, 1, 0, 20));
        inputs.add_arr_option_item(material(3, 30, 1, 1, 0, 100));

        var selected = selection(1, 11, 1, 0);
        check(inputs.validateSelection(List.of(selected, selection(2, 20, 1, 0))) == 70,
                "alternative and second required group");
        check(selected.get_count() == 2 && selected.get_bless() == 3, "use server quantity and wildcard");
        check(inputs.validateSelection(List.of(selection(1, 10, 1, 7))) < 0, "all required groups verified");
        check(inputs.validateSelection(List.of(selection(1, 10, 1, 6), selection(2, 20, 1, 0))) < 0,
                "enchant condition preserved");
        check(inputs.validateSelection(List.of(selection(1, 11, 1, 0), selection(1, 10, 1, 7),
                selection(2, 20, 1, 0))) < 0, "one choice per slot");
        check(inputs.validateSelection(List.of(selection(0, 11, 1, 0), selection(1, 20, 1, 0),
                selection(2, 30, 1, 0))) == 170, "zero-based client and optional bonus");
        check(inputs.validateSelection(List.of(selection(1, 11, 1, 0), selection(2, 20, 1, 0),
                selection(4, 30, 1, 0))) < 0, "optional group is recognized by its own slot");
        check(inputs.validateSelection(List.of(selection(3, 30, 1, 0), selection(2, 20, 1, 0))) < 0,
                "optional selection cannot substitute for required");
        var element = selection(1, 11, 1, 0);
        element.set_elemental_enchant_type(1);
        element.set_elemental_enchant_value(10);
        check(inputs.validateSelection(List.of(element, selection(2, 20, 1, 0))) < 0, "element bounds");
        assertItemSelection();
        assertNpcCatalog();
        System.out.println("PASS craft material selection, item attributes, and NPC catalog authorization");
    }

    private static void assertItemSelection() {
        var choice = selection(1, 10, 1, 7);
        choice.set_elemental_enchant_type(2);
        choice.set_elemental_enchant_value(3);
        var definition = new L1EtcItem();
        definition.setItemId(220);
        definition.setItemDescId(10);
        var item = new L1ItemInstance(definition, 1);
        item.setBless(1);
        item.setEnchantLevel(7);
        item.setAttrEnchantLevel(8);
        check(choice.isInput(item), "server item matches selection");
        item.setAttrEnchantLevel(3);
        check(!choice.isInput(item), "unmatched actual attribute");
        item.setAttrEnchantLevel(8);
        item.setBless(0);
        check(!choice.isInput(item), "unmatched actual bless");
        var roomtisTemplate = new L1EtcItem();
        roomtisTemplate.setItemId(22229);
        roomtisTemplate.setItemDescId(10);
        var roomtis = new L1ItemInstance(roomtisTemplate, 1);
        roomtis.setBless(0);
        roomtis.setEnchantLevel(7);
        roomtis.setAttrEnchantLevel(8);
        check(choice.isInput(roomtis), "roomtis blessing rule");
    }

    private static void assertNpcCatalog() throws Exception {
        Class<?> catalogClass = SC_CRAFT_LIST_ALL_ACK.class;
        Method build = catalogClass.getDeclaredMethod("buildNpcCatalog", String.class, ArrangeParsee.class);
        build.setAccessible(true);
        var catalogs = catalogClass.getDeclaredField("npcCatalogs");
        catalogs.setAccessible(true);
        Object before = catalogs.get(null);
        try {
            Object first = build.invoke(null, "30,10,20", ArrangeParseeFactory.createIntArrange());
            Object second = build.invoke(null, "99", ArrangeParseeFactory.createIntArrange());
            catalogs.set(null, Map.of(501, first, 502, second));
            check(SC_CRAFT_LIST_ALL_ACK.getCraftIdListFromNpcId(501) != null, "listed NPC has a wire list");
            check(SC_CRAFT_LIST_ALL_ACK.offersRecipe(501, 30) && SC_CRAFT_LIST_ALL_ACK.offersRecipe(501, 10),
                    "wire list and allowed ids come from the same catalogue");
            check(!SC_CRAFT_LIST_ALL_ACK.offersRecipe(501, 99) && !SC_CRAFT_LIST_ALL_ACK.offersRecipe(503, 30),
                    "recipe must belong to the actual provider");
            catalogs.set(null, Map.of(501, second));
            check(!SC_CRAFT_LIST_ALL_ACK.offersRecipe(501, 30) && SC_CRAFT_LIST_ALL_ACK.offersRecipe(501, 99),
                    "new catalog visibility");
        } finally {
            catalogs.set(null, before);
        }
    }

    private static CraftInputItem material(int slot, int id, int count, int bless, int enchant, int bonus) {
        var item = new CraftInputItem();
        item.set_slot(slot);
        item.set_nameId(id);
        item.set_count(count);
        item.set_bless(bless);
        item.set_enchant(enchant);
        item.set_increaseProb(bonus);
        return item;
    }

    private static CraftInputItemSlotInfo selection(int slot, int descId, int bless, int enchant) {
        var request = new CraftInputItemSlotInfo();
        request.set_input_slot_no(slot);
        request.set_item_name_id(descId);
        request.set_count(100);
        request.set_bless(bless);
        request.set_enchant(enchant);
        return request;
    }

    private static void check(boolean valid, String purpose) {
        if (!valid) throw new AssertionError(purpose);
    }
}

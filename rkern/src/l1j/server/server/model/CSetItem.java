package l1j.server.server.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import l1j.server.server.datatables.ArmorSetTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.templates.L1ArmorSets;

/** Shared set definition. A player's active sets are owned by L1EquipmentSlot. */
final class CSetItem {
    private static final Definitions DEFINITIONS = loadSets();

    private final int[] itemIds;
    private final int ac, hp, mp, hpr, mpr, mr;
    private final int str, dex, con, wis, cha, intelligence;
    private final int water, earth, wind, fire;
    final int mainItemId;
    final boolean grantsEva;
    final int polyId;

    private CSetItem(L1ArmorSets row) {
        itemIds = parseItemIds(row.getSets());
        ac = row.getAc();
        hp = row.getHp();
        mp = row.getMp();
        hpr = row.getHpr();
        mpr = row.getMpr();
        mr = row.getMr();
        str = row.getStr();
        dex = row.getDex();
        con = row.getCon();
        wis = row.getWis();
        cha = row.getCha();
        intelligence = row.getIntl();
        water = row.get_defense_water();
        earth = row.get_defense_earth();
        wind = row.get_defense_wind();
        fire = row.get_defense_fire();
        mainItemId = row.get_main_id();
        grantsEva = row.getId() == 128;
        polyId = row.getPolyId();
    }

    static List<CSetItem> definitions(boolean polymorph) {
        return polymorph ? DEFINITIONS.polymorph() : DEFINITIONS.regular();
    }

    static boolean isMainItem(int itemId) {
        return Arrays.binarySearch(DEFINITIONS.mainItemIds(), itemId) >= 0;
    }

    private record Definitions(List<CSetItem> regular, List<CSetItem> polymorph, int[] mainItemIds) {}

    private static Definitions loadSets() {
        final List<CSetItem> regular = new ArrayList<>();
        final List<CSetItem> polymorph = new ArrayList<>();
        final L1ArmorSets[] rows = ArmorSetTable.getInstance().getAllList();
        final int[] mainItemIds = new int[rows.length];
        int mainItemCount = 0;
        for (L1ArmorSets row : rows) {
            try {
                final CSetItem set = new CSetItem(row);
                (set.polyId == -1 ? regular : polymorph).add(set);
                if (set.mainItemId > 0) mainItemIds[mainItemCount++] = set.mainItemId;
            } catch (Exception failure) {
                failure.printStackTrace();
            }
        }
        final int[] registeredMainItems = Arrays.copyOf(mainItemIds, mainItemCount);
        Arrays.sort(registeredMainItems);
        return new Definitions(List.copyOf(regular), List.copyOf(polymorph), registeredMainItems);
    }

    private static int[] parseItemIds(String items) {
        final StringTokenizer tokens = new StringTokenizer(items, ",");
        final int[] ids = new int[tokens.countTokens()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = Integer.parseInt(tokens.nextToken().strip());
        }
        return ids;
    }

    void applyEffects(L1PcInstance pc, int sign) {
        ItemStat.AC.apply(pc, sign * ac);
        ItemStat.MAX_HP.apply(pc, sign * hp);
        ItemStat.MAX_MP.apply(pc, sign * mp);
        pc.addHpr(sign * hpr);
        ItemStat.MPR.apply(pc, sign * mpr);
        ItemStat.MR.apply(pc, sign * mr);

        final Ability ability = pc.getAbility();
        ability.addAddedStr((byte) (sign * str));
        ability.addAddedDex((byte) (sign * dex));
        ability.addAddedCon((byte) (sign * con));
        ability.addAddedWis((byte) (sign * wis));
        ability.addAddedCha((byte) (sign * cha));
        ability.addAddedInt((byte) (sign * intelligence));

        final Resistance resistance = pc.getResistance();
        resistance.addWater(sign * water);
        resistance.addEarth(sign * earth);
        resistance.addWind(sign * wind);
        resistance.addFire(sign * fire);
    }

    boolean isValid(L1EquipmentSlot slot) {
        for (int itemId : itemIds) {
            if (!slot.hasAppliedItem(itemId)) return false;
        }
        return true;
    }

    boolean containsItem(int itemId) {
        for (int id : itemIds) {
            if (id == itemId) return true;
        }
        return false;
    }

    boolean hasDuplicateEquippedRing(L1EquipmentSlot slot) {
        for (int id : itemIds) {
            final int copies = slot.equippedSetRingCopies(id);
            if (copies != 0) return copies >= 2;
        }
        return false;
    }

    int polymorphGfx(L1PcInstance pc) {
        if (polyId != 6080 && polyId != 6094) return polyId;
        return pc.get_sex() == 0 ? 6094 : 6080;
    }

    boolean canApplyPolymorph(L1PcInstance pc) {
        if (polyId == -1) return false;
        if (polyId == 6080 || polyId == 6094) {
            final L1PcInventory inventory = pc.getInventory();
            final L1ItemInstance item = inventory.findItemId(20383);
            return item != null && item.getChargeCount() != 0 && inventory.checkItem(20383, 1);
        }
        return true;
    }
}

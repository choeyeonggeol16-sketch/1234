package l1j.server.server.model.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import gnu.trove.map.hash.TIntObjectHashMap;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.templates.L1ArmorSets;
import l1j.server.server.utils.ItemPresentOutStream;

/** Shared set definition. A player's active sets are owned by CUserEquipment. */
public final class CSetItem {
    private static final Definitions DEFINITIONS = loadSets();

    /** Eagerly finish the existing shared set-definition initialization. */
    public static void PostLoad() {
        definitions(false);
    }

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

    public static void writeTooltip(L1ItemInstance item, ItemPresentOutStream out) {
        final List<CSetItem> sets = DEFINITIONS.byMainItem().get(item.getItemId());
        if (sets == null) return;
        out.writeSetItem();
        out.writeC(item.get_main_set_armor() ? 1 : 2);
        for (int i = 0; i < sets.size(); i++) {
            sets.get(i).writeEffects(out);
        }
        out.writeSetItem();
        out.writeC(0);
    }

    private void writeEffects(ItemPresentOutStream out) {
        out.writeAddAc(-ac);
        out.writeAddMaxHP(hp);
        out.writeMaxMP(mp);
        out.writeAddHPPrecovery(hpr);
        out.writeAddMPPrecovery(mpr);
        out.writeAddMR(mr);
        out.writeaSTR_Bu(str);
        out.writeaDEX_Bu(dex);
        out.writeaCON_Bu(con);
        out.writeaWIS_Bu(wis);
        out.writeaCHA_Bu(cha);
        out.writeaINT_Bu(intelligence);
        out.writeRegistWater(water);
        out.writeRegistEarth(earth);
        out.writeRegistWind(wind);
        out.writeRegistFire(fire);
    }

    private record Definitions(List<CSetItem> regular, List<CSetItem> polymorph,
                               TIntObjectHashMap<List<CSetItem>> byMainItem) {}

    private static Definitions loadSets() {
        final List<CSetItem> regular = new ArrayList<>();
        final List<CSetItem> polymorph = new ArrayList<>();
        final var byMainItem = new TIntObjectHashMap<List<CSetItem>>();
        for (L1ArmorSets row : CObjectDB.LoadSetItem().getAllList()) {
            try {
                final CSetItem set = new CSetItem(row);
                (set.polyId == -1 ? regular : polymorph).add(set);
                if (set.mainItemId > 0) {
                    List<CSetItem> group = byMainItem.get(set.mainItemId);
                    if (group == null) {
                        group = new ArrayList<>(1);
                        byMainItem.put(set.mainItemId, group);
                    }
                    group.add(set);
                }
            } catch (Exception failure) {
                failure.printStackTrace();
            }
        }
        return new Definitions(List.copyOf(regular), List.copyOf(polymorph), byMainItem);
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

        final var ability = pc;
        ability.AddStatBonusStr((byte) (sign * str));
        ability.AddStatBonusDex((byte) (sign * dex));
        ability.AddStatBonusCon((byte) (sign * con));
        ability.AddStatBonusWis((byte) (sign * wis));
        ability.AddStatBonusCha((byte) (sign * cha));
        ability.AddStatBonusInt((byte) (sign * intelligence));

        final var resistance = pc;
        resistance.AddWaterResist(sign * water);
        resistance.AddEarthResist(sign * earth);
        resistance.AddWindResist(sign * wind);
        resistance.AddFireResist(sign * fire);
    }

    boolean isValid(CUserEquipment slot) {
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

    boolean hasDuplicateEquippedRing(CUserEquipment slot) {
        for (int id : itemIds) {
            final int copies = slot.equippedSetRingCopies(id);
            if (copies >= 2) return true;
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
            final CUserInventory inventory = pc.getInventory();
            final L1ItemInstance item = inventory.GetFirstItemByClassID(20383);
            return item != null && item.getChargeCount() != 0 && inventory.HaveItemByClassId(20383, 1);
        }
        return true;
    }
}

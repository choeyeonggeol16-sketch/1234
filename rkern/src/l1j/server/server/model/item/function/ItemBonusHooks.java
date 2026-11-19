package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.Exp.ExpBonusState;

/** Legacy entrypoints retained for existing game callers. */
public final class ItemBonusHooks {
    private ItemBonusHooks() {}
    public static int calcMpTick(L1DollInstance doll) { return DollTickBonuses.calcMpTick(doll); }
    public static int calcHpTick(L1DollInstance doll) { return DollTickBonuses.calcHpTick(doll); }
    public static void onDollSpawned(L1PcInstance pc, L1DollInstance doll) { DollExperienceBonuses.onDollSpawned(pc, doll); }
    public static void onDollRemoved(L1PcInstance pc, L1DollInstance doll) { DollExperienceBonuses.onDollRemoved(pc, doll); }
    public static void onEquipChanged(L1PcInstance pc, L1ItemInstance item, boolean equipped) { EquipmentExperienceBonuses.onEquipChanged(pc, item, equipped); }
    public static void onEnchantChanged(L1PcInstance pc, L1ItemInstance item) { EquipmentExperienceBonuses.onEnchantChanged(pc, item); }
}

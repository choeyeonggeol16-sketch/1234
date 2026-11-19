package l1j.server.server.model.item.function;

import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import static l1j.server.server.model.item.function.CreatureItemActions.*;

/** Compatibility API; implementations live in focused combat modules. */
public final class CombatAbilityItems {
    private CombatAbilityItems() {}

    public static void 블레이즈스턴(L1PcInstance pc, int targetId, L1ItemInstance item) { BlazeStunItemEffect.블레이즈스턴(pc, targetId, item); }
    public static void 엘븐스트라이크(L1PcInstance pc, int targetId, L1ItemInstance item) { ElvenStrikeItemEffect.엘븐스트라이크(pc, targetId, item); }
    public static void 이터니티(L1PcInstance pc, int targetId, L1ItemInstance item) { EternityItemEffect.이터니티(pc, targetId, item); }
    public static double calcMrDefense(int MagicResistance, double dmg) { return EternityItemEffect.calcMrDefense(MagicResistance, dmg); }
    public static void 쉐도우(L1PcInstance pc, int targetId, L1ItemInstance item) { ShadowBindingItemEffect.쉐도우(pc, targetId, item); }
    public static void 데몰리션(L1PcInstance pc, int targetId, L1ItemInstance item) { DemolitionItemEffect.데몰리션(pc, targetId, item); }
    public static void 어벤저(L1PcInstance pc, int targetId, L1ItemInstance item) { AvengerItemEffect.어벤저(pc, targetId, item); }
    public static void 콜어드밴스(L1PcInstance pc, int targetId, L1ItemInstance item) { CallAdvanceItemEffect.콜어드밴스(pc, targetId, item); }
    public static void 전투지휘봉(L1PcInstance pc, int targetId, L1ItemInstance item) { BattleCommandWandEffect.전투지휘봉(pc, targetId, item); }
    public static int targetDirection(L1PcInstance pc, int tx, int ty) { return CombatSupportItems.targetDirection(pc, tx, ty); }
    public static void Start(L1PcInstance pc, L1Character cha) { CombatSupportItems.Start(pc, cha); }
    public static void Start1(L1PcInstance pc, L1NpcInstance member1) { CombatSupportItems.Start1(pc, member1); }
    public static void 액션(L1PcInstance pc, L1Character cha) { CombatSupportItems.액션(pc, cha); }
    public static void 액션1(L1PcInstance pc, L1NpcInstance member1) { CombatSupportItems.액션1(pc, member1); }
}

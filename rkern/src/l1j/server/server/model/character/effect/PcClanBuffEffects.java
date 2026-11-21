package l1j.server.server.model.character.effect;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class PcClanBuffEffects {

    private PcClanBuffEffects() {}

    public enum Kind {
        혈맹버프AC(L1SkillId.혈맹버프AC, 5538,
                "혈맹의 군주가 [혈맹버프: AC -5]를 사용하였습니다",
                "\\f:혈맹의 군주가 [혈맹버프:AC -5]를 사용하였습니다"),
        혈맹버프마방(L1SkillId.혈맹버프마방, 5539,
                "혈맹의 군주가 [혈맹버프: MR +10%]를 사용하였습니다",
                "\\f:혈맹의 군주가 [혈맹버프: MR +10%]를 사용하였습니다"),
        혈맹버프PVP추가(L1SkillId.혈맹버프PVP추가, 5540,
                "혈맹의 군주가 [혈맹버프: PVP추가대미지 +3]를 사용하였습니다",
                "\\f:혈맹의 군주가 [혈맹버프: PVP추가대미지 +3]를 사용하였습니다"),
        혈맹버프PVP감소(L1SkillId.혈맹버프PVP감소, 5541,
                "혈맹의 군주가 [혈맹버프: PVP대미지감소 +3]를 사용하였습니다",
                "\\f:혈맹의 군주가 [혈맹버프: PVP대미지감소 +3]를 사용하였습니다"),
        혈맹버프HP증가(L1SkillId.혈맹버프HP증가, 5542,
                "혈맹의 군주가 [혈맹버프: HP증가 +150]를 사용하였습니다",
                "\\f:혈맹의 군주가 [혈맹버프: HP증가 +150]를 사용하였습니다"),
        혈맹버프올내성(L1SkillId.혈맹버프올내성, 5543,
                "혈맹의 군주가 [혈맹버프: 전체 내성 +3]를 사용하였습니다",
                "\\f:혈맹의 군주가 [혈맹버프: 전체 내성 +3]를 사용하였습니다"),
        혈맹버프올적중(L1SkillId.혈맹버프올적중, 5544,
                "혈맹의 군주가 [혈맹버프: 전체 적중 +3]를 사용하였습니다",
                "\\f:혈맹의 군주가 [혈맹버프: 전체 적중 +3]를 사용하였습니다"),
        혈맹버프올스텟(L1SkillId.혈맹버프올스텟, 5545,
                "혈맹의 군주가 [혈맹버프: 전체 스텟 +3]를 사용하였습니다",
                "\\f:혈맹의 군주가 [혈맹버프: 전체 스텟 +3]를 사용하였습니다"),
        혈맹버프회복(L1SkillId.혈맹버프회복, 5546,
                null,
                null);

        private static final Kind[] BY_ITEM_ORDER = values();
        private final int skillId;
        private final int icon;
        private final String green;
        private final String notice;

        Kind(int skillId, int icon, String green, String notice) {
            this.skillId = skillId;
            this.icon = icon;
            this.green = green;
            this.notice = notice;
        }

        public int skillId() { return skillId; }

        public static Kind fromItemId(int itemId) {
            final int index = itemId - 145001;
            return index >= 0 && index < BY_ITEM_ORDER.length ? BY_ITEM_ORDER[index] : null;
        }
    }

    public static void apply(L1PcInstance pc, Kind kind) {
        if (kind == Kind.혈맹버프회복) return;
        pc.applySkillModifierFromOwner(kind.skillId, 0);
        notifyChange(pc, kind.skillId);
    }

    public static void notifyChange(L1PcInstance pc, int skillId) {
        switch (skillId) {
            case L1SkillId.혈맹버프AC -> SC_OwnCharAttrDef.send(pc);
            case L1SkillId.혈맹버프마방 -> SC_SPMR.send(pc);
            case L1SkillId.혈맹버프HP증가 -> SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
            case L1SkillId.혈맹버프올스텟 -> SC_OwnCharStatus2.send(pc);
            default -> { }
        }
    }

    public static void startScrollFromOwner(L1PcInstance pc, int skillId, int seconds, int clanId,
            int itemId, boolean unused) {
        if (pc.getClanid() != clanId) return;
        final Kind kind = Kind.fromItemId(itemId);
        if (kind == null || kind.skillId != skillId) return;
        pc.skillEffectRemoveFromOwner(skillId, true);
        apply(pc, kind);
        pc.skillEffectSetFromOwner(skillId, (int) Math.clamp(seconds * 1000L, 0, Integer.MAX_VALUE));
        SC_InventoryIcon.icoNew(pc, skillId, kind.icon, seconds, true);
        SC_SkillSound.send(pc, pc.getId(), 13566);
        SC_SkillSound.broadcast(pc, pc.getId(), 13566);
        if (kind == Kind.혈맹버프회복) {
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE,
                    "혈맹의 군주가 [혈맹버프: 물약회복량 +" + Config.혈맹버프회복 + "%]을 사용하였습니다");
            SC_SystemMessage.send(pc,
                    "\f:혈맹의 군주가 [혈맹버프: 물약회복량 +" + Config.혈맹버프회복 + "%]를 사용하였습니다");
        } else {
            SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, kind.green);
            SC_SystemMessage.send(pc, kind.notice);
        }
    }
}

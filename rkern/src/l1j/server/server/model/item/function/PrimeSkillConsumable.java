package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.world.siege.CCastleSiegeManager;
import l1j.server.server.utils.GameUtils;

/** Consumable active skill for item ID 70708. */
final class PrimeSkillConsumable {
    private PrimeSkillConsumable() {}

    static void use(L1PcInstance pc) {
        if (GameUtils.isNonAction(pc)) return;
        if (pc.getType() != 0 || pc.getLevel() < 85) {
            pc.sendPackets("현재 프라임을 사용할 수 없는 조건입니다.");
            return;
        }
        final L1Clan clan = pc.getClan();
        if (pc.getClanid() == 0) {
            pc.sendPackets("프라임을 사용하기 위해서는 혈맹을 창설하여야 합니다.");
            return;
        }
        if (!pc.getInventory().HaveItemByClassId(41246, 1000)) {
            pc.sendPackets("결정체가 부족합니다.");
            return;
        }
        if (pc.getCurrentMp() < 50) {
            pc.sendPackets("MP가 부족합니다.");
            return;
        }
        if (pc.hasSkillEffect(L1SkillId.프라임대기시간)) {
            final int remaining = pc.getSkillEffectTimeSec(L1SkillId.프라임대기시간);
            SC_SystemMessage.send(pc, "\\fY프라임 대기시간 : " + remaining + "초 후에 사용가능합니다.");
            return;
        }
        DurableSkillReagentTransaction.useDurableSkillReagent(pc, 41246, 1000, () -> {
            final boolean castle = isInWarAreaAndWarTime(pc);
            final int skillId = castle ? L1SkillId.프라임1 : L1SkillId.프라임;
            final int level = pc.getLevel();
            final int tierId = level >= 95 ? L1SkillId.프라임95
                    : level >= 90 ? L1SkillId.프라임90 : L1SkillId.프라임85;
            // The existing 32-slot per-PC serializer executes every stat,
            // timer and status update, including the caster's own buff.
            if (!submitPrime(pc, pc, skillId, tierId)) {
                SC_SystemMessage.send(pc, "프라임 효과를 적용하지 못했습니다.");
                return;
            }
            pc.sendPackets(castle ? "공성존의 프라임 효과가 적용되었습니다."
                    : "프라임의 효과가 적용되었습니다.");
            pc.setCurrentMp(pc.getCurrentMp() - 50);
            pc.setSkillEffect(L1SkillId.프라임대기시간, Config.프라임대기시간 * 1000);
            SC_OwnCharAttrDef.send(pc);
            SC_OwnCharStatus.send(pc);
            L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(),
                    null, 0, L1SkillUse.TYPE_SPELLSC);
            SC_SPMR.send(pc);
            SC_SkillSound.send(pc, pc.getId(), 12301);
            SC_SkillSound.broadcast(pc, pc.getId(), 12301);

            if (clan == null) return;
            try (var ordinaryNotice = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    SC_SystemMessage.nativeWriter("프라임의 효과가 적용되었습니다."),
                    Integer.MIN_VALUE, Integer.MAX_VALUE);
                 var castleNotice = new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
                    SC_SystemMessage.nativeWriter("공성존의 프라임 효과가 적용되었습니다."),
                    Integer.MIN_VALUE, Integer.MAX_VALUE)) {
                for (L1PcInstance recipient : CWorld.getInstance().getVisiblePlayer(pc, 20)) {
                    if (recipient == pc || recipient.getClanid() != pc.getClanid()) continue;
                    if (!submitPrime(recipient, pc, skillId, tierId)) continue;
                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(
                            castle ? castleNotice : ordinaryNotice, recipient);
                }
            }
        });
    }

    private static boolean submitPrime(L1PcInstance recipient, L1PcInstance caster,
            int skillId, int tierId) {
        final var controller = recipient.getCreatureController();
        return controller != null && controller.addExecutableCastBuff(recipient,
                skillId, 600, tierId, caster.getId(), null);
    }

    private static boolean isInWarAreaAndWarTime(L1PcInstance pc) {
        int castleId = L1CastleLocation.getCastleIdByArea(pc);
        return castleId != 0 && CCastleSiegeManager.getInstance().isNowWar(castleId);
    }
}

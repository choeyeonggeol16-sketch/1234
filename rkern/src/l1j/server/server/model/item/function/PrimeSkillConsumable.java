package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import l1j.server.Config;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eDurationShowType;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.MJCommons;
import l1j.server.server.utils.eKind;

/** Consumable active skill for item ID 70708. */
final class PrimeSkillConsumable {
    private PrimeSkillConsumable() {}

    static void use(L1PcInstance pc) {
            L1Clan clan1 = pc.getClan();
            if (MJCommons.isNonAction(pc)) {
                return;
            }
            if (pc.getType() == 0 && pc.getLevel() >= 85) {
                if (pc.getClanid() == 0) {
                    pc.sendPackets("프라임을 사용하기 위해서는 혈맹을 창설하여야 합니다.");
                    return;
                }
                if (!pc.getInventory().checkItem(41246, 1000)) {
                    pc.sendPackets("결정체가 부족합니다.");
                    return;
                }
                if (pc.getCurrentMp() < 50) {
                    pc.sendPackets("MP가 부족합니다.");
                    return;
                }
                if (pc.hasSkillEffect(L1SkillId.프라임대기시간)) {
                    int n = pc.getSkillEffectTimeSec(L1SkillId.프라임대기시간);
                    SC_SystemMessage.send(pc, "\\fY프라임 대기시간 : " + n + "초 후에 사용가능합니다.");
                    return;
                }
                DurableSkillReagentTransaction.useDurableSkillReagent(pc, 41246, 1000, ()->{
                    if (pc.hasSkillEffect(L1SkillId.프라임)) {
                        pc.removeSkillEffect(L1SkillId.프라임);
                    }
                    if (pc.hasSkillEffect(L1SkillId.프라임1)) {
                        pc.removeSkillEffect(L1SkillId.프라임1);
                    }
                    if (pc.hasSkillEffect(L1SkillId.프라임85)) {
                        pc.removeSkillEffect(L1SkillId.프라임85);
                    }
                    if (pc.hasSkillEffect(L1SkillId.프라임90)) {
                        pc.removeSkillEffect(L1SkillId.프라임90);
                    }
                    if (pc.hasSkillEffect(L1SkillId.프라임95)) {
                        pc.removeSkillEffect(L1SkillId.프라임95);
                    }
                    if (pc.getLevel() >= 85 && pc.getLevel() < 90) {
                        pc.setSkillEffect(L1SkillId.프라임85, 600 * 1000);
                    } else if (pc.getLevel() >= 90 && pc.getLevel() < 95) {
                        pc.setSkillEffect(L1SkillId.프라임90, 600 * 1000);
                    } else if (pc.getLevel() >= 95) {
                        pc.setSkillEffect(L1SkillId.프라임95, 600 * 1000);
                    }
                    if (!isInWarAreaAndWarTime(pc)) {
                        pc.setSkillEffect(L1SkillId.프라임, 600 * 1000);
                        SC_InventoryIcon.icoNew(pc, L1SkillId.프라임, 5383, 600, true);
                        pc.addBowHitRate(3);
                        pc.addBowDmgRate(3);
                        pc.addDmgRate(3);
                        pc.addHitRate(3);
                        pc.getAbility().addSp(2);
                        pc.addMagicHit(2);
                        pc.sendPackets("프라임의 효과가 적용되었습니다.");
                    } else {
                        pc.setSkillEffect(L1SkillId.프라임1, 600 * 1000);
                        SC_InventoryIcon.icoNew(pc, L1SkillId.프라임, 5383, 600, true);
                        pc.addBowHitRate(9);
                        pc.addBowDmgRate(9);
                        pc.addDmgRate(9);
                        pc.addHitRate(9);
                        pc.getAbility().addSp(6);
                        pc.addMagicHit(6);
                        pc.addMaxHp(500);
                        pc.sendPackets("공성존의 프라임 효과가 적용되었습니다.");
                    }
                    pc.setCurrentMp(pc.getCurrentMp() - 50);
                    pc.setSkillEffect(L1SkillId.프라임대기시간, Config.프라임대기시간 * 1000);
                    SC_OwnCharAttrDef.send(pc);
                    SC_OwnCharStatus.send(pc);
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    SC_SPMR.send(pc);
                    SC_SkillSound.send(pc, pc.getId(), 12301);
                    SC_SkillSound.broadcast(pc, pc.getId(), 12301);
                    if (clan1 != null) {
                        try(var noticeA=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_SystemMessage.nativeWriter("프라임의 효과가 적용되었습니다."),Integer.MIN_VALUE,Integer.MAX_VALUE);
var noticeB=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_SystemMessage.nativeWriter("공성존의 프라임 효과가 적용되었습니다."),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance player : L1World.getInstance().getVisiblePlayer(pc, 20)) {
                            if (player.getClanid() == pc.getClanid()) {
                                if (player.hasSkillEffect(L1SkillId.프라임)) {
                                    player.removeSkillEffect(L1SkillId.프라임);
                                }
                                if (player.hasSkillEffect(L1SkillId.프라임1)) {
                                    player.removeSkillEffect(L1SkillId.프라임1);
                                }
                                if (player.hasSkillEffect(L1SkillId.프라임85)) {
                                    player.removeSkillEffect(L1SkillId.프라임85);
                                }
                                if (player.hasSkillEffect(L1SkillId.프라임90)) {
                                    player.removeSkillEffect(L1SkillId.프라임90);
                                }
                                if (player.hasSkillEffect(L1SkillId.프라임95)) {
                                    player.removeSkillEffect(L1SkillId.프라임95);
                                }
                                if (pc.getLevel() >= 85 && pc.getLevel() < 90) {
                                    player.setSkillEffect(L1SkillId.프라임85, 600 * 1000);
                                } else if (pc.getLevel() >= 90 && pc.getLevel() < 95) {
                                    player.setSkillEffect(L1SkillId.프라임90, 600 * 1000);
                                } else if (pc.getLevel() >= 95) {
                                    player.setSkillEffect(L1SkillId.프라임95, 600 * 1000);
                                }
                                if (!isInWarAreaAndWarTime(pc)) {
                                    player.setSkillEffect(L1SkillId.프라임, 600 * 1000);
                                    SC_InventoryIcon.icoNew(player, L1SkillId.프라임, 5383, 600, true);
                                    player.addBowHitRate(3);
                                    player.addBowDmgRate(3);
                                    player.addDmgRate(3);
                                    player.addHitRate(3);
                                    player.getAbility().addSp(2);
                                    player.addMagicHit(2);
                                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(noticeA,player);
                                } else {
                                    player.setSkillEffect(L1SkillId.프라임1, 600 * 1000);
                                    SC_InventoryIcon.icoNew(player, L1SkillId.프라임, 5383, 600, true);
                                    player.addBowHitRate(9);
                                    player.addBowDmgRate(9);
                                    player.addDmgRate(9);
                                    player.addHitRate(9);
                                    player.getAbility().addSp(6);
                                    player.addMagicHit(6);
                                    player.addMaxHp(500);
                                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(noticeB,player);
                                }
                                SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
                                SC_OwnCharAttrDef.send(player);
                                SC_OwnCharStatus.send(player);
                                SC_SPMR.send(player);
                                SC_SkillSound.send(player, player.getId(), 12301);
                                SC_SkillSound.broadcast(player, player.getId(), 12301);
                            }
                        }
}
                    }
                });
            } else {
                pc.sendPackets("현재 프라임을 사용할 수 없는 조건입니다.");
            }
    }

    private static boolean isInWarAreaAndWarTime(L1PcInstance pc) {
        int castleId = L1CastleLocation.getCastleIdByArea(pc);
        return castleId != 0 && MJCastleWarBusiness.getInstance().isNowWar(castleId);
    }
}

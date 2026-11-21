package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;

import l1j.server.Config;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eDurationShowType;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType;
import l1j.server.server.utils.GameUtils;

/** Consumable active skill for item ID 70733. */
final class MassImmuneSkillConsumable {
    private MassImmuneSkillConsumable() {}

    static void use(L1PcInstance pc) {
            L1Clan clan11 = pc.getClan();
            L1Party party = pc.getParty();
            if (pc.getType() == 3 && pc.getLevel() >= 1) {
                if (!pc.getInventory().checkItem(40318, Config.매스이뮨마돌)) {
                    pc.sendPackets("마력의 돌이 부족합니다.");
                    return;
                }
                if (pc.getCurrentMp() < Config.매스이뮨엠피) {
                    pc.sendPackets("MP가 부족합니다.");
                    return;
                }
                if (GameUtils.isNonAction(pc)) {
                    return;
                }
                if (pc.hasSkillEffect(L1SkillId.매스이뮨대기시간)) {
                    int n = pc.getSkillEffectTimeSec(L1SkillId.매스이뮨대기시간);
                    SC_SystemMessage.send(pc, "\\fY매스 이뮨 투 함 대기시간 : " + n + "초 후에 사용가능합니다.");
                    return;
                }
                if (pc.getParty() == null) {
                    pc.sendPackets("파티상태가 아닙니다.");
                    return;
                }
                if (pc.getClanid() == 0) {
                    pc.sendPackets("혈맹에 가입 후 시전이 가능합니다.");
                    return;
                }
                DurableSkillReagentTransaction.useDurableSkillReagent(pc, 40318, Config.매스이뮨마돌, ()->{
                    if (pc.hasSkillEffect(L1SkillId.IMMUNE_TO_HARM)) {
                        pc.removeSkillEffect(L1SkillId.IMMUNE_TO_HARM);
                    }
                    pc.setSkillEffect(L1SkillId.IMMUNE_TO_HARM, 32 * 1000);
                    l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
                    int notiWire_spellId = 0;
                    int notiWire_duration = 0;
                    l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eDurationShowType notiWire_durationShowType = null;
                    int notiWire_onIconId = 0;
                    int notiWire_offIconId = 0;
                    int notiWire_iconPriority = 0;
                    int notiWire_tooltipStrId = 0;
                    int notiWire_newStrId = 0;
                    int notiWire_endStrId = 0;
                    boolean notiWire_isGood = false;
                    int notiWire_bit = 0;
                    if (pc.hasSkillEffect(L1SkillId.세인트)) {
                        pc.세인트(true);
                        {
                            notiWire_bit |= 16;
                            notiWire_onIconId = 2788;
                        }
                        {
                            notiWire_bit |= 32;
                            notiWire_offIconId = 2788;
                        }
                        SC_SkillSound.send(pc, pc.getId(), 231);
                        SC_SkillSound.broadcast(pc, pc.getId(), 231);
                    } else {
                        {
                            notiWire_bit |= 16;
                            notiWire_onIconId = 1562;
                        }
                        {
                            notiWire_bit |= 32;
                            notiWire_offIconId = 1562;
                        }
                    }
                    {
                        notiWire_bit |= 1;
                        notiWire_notiType = eNotiType.RESTAT;
                    }
                    {
                        notiWire_bit |= 2;
                        notiWire_spellId = L1SkillId.IMMUNE_TO_HARM;
                    }
                    {
                        notiWire_bit |= 4;
                        notiWire_duration = 32;
                    }
                    {
                        notiWire_bit |= 8;
                        notiWire_durationShowType = eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC;
                    }
                    {
                        notiWire_bit |= 64;
                        notiWire_iconPriority = 10;
                    }
                    {
                        notiWire_bit |= 128;
                        notiWire_tooltipStrId = 966;
                    }
                    {
                        notiWire_bit |= 256;
                        notiWire_newStrId = 314;
                    }
                    {
                        notiWire_bit |= 512;
                        notiWire_endStrId = 315;
                    }
                    {
                        notiWire_bit |= 1024;
                        notiWire_isGood = true;
                    }
                    SC_SPELL_BUFF_NOTI.sendFields(pc, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType, notiWire_onIconId, notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, notiWire_newStrId, notiWire_endStrId, notiWire_isGood, 0, 0, 0, notiWire_bit);
                    SC_SkillSound.send(pc, pc.getId(), 21103);
                    SC_SkillSound.broadcast(pc, pc.getId(), 21103);
                    pc.setLastImmuneLevel(Config.IMMUNE_Level);
                    pc.setCurrentMp(pc.getCurrentMp() - Config.매스이뮨엠피);
                    pc.setSkillEffect(L1SkillId.매스이뮨대기시간, Config.매스이뮨대기시간 * 1000);
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    pc.sendPackets("\\f:매스 이뮨 투함이 시전되었습니다.");
                    if (party != null) {
                        try(var noticeA=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_SystemMessage.nativeWriter("\\f:[" + pc.getName() + "]님의 매스 이뮨 투 함이 시전되었습니다."),Integer.MIN_VALUE,Integer.MAX_VALUE);
var noticeB=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_SystemMessage.nativeWriter("\\fY같은 혈맹이 아니므로 매스 이뮨 투 함 미적용"),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance player : L1World.getInstance().getVisiblePlayer(pc, 9)) {
                            if (pc.getParty().isMember(player)) {
                                if (player.getClanid() == pc.getClanid()) {
                                    if (player.hasSkillEffect(L1SkillId.IMMUNE_TO_HARM)) {
                                        player.removeSkillEffect(L1SkillId.IMMUNE_TO_HARM);
                                    }
                                    if (player.hasSkillEffect(L1SkillId.LUCIFER)) {
                                        player.removeSkillEffect(L1SkillId.LUCIFER);
                                    }
                                    if (player.hasSkillEffect(L1SkillId.세인트)) {
                                        player.세인트(true);
                                        {
                                            notiWire_bit |= 16;
                                            notiWire_onIconId = 2788;
                                        }
                                        {
                                            notiWire_bit |= 32;
                                            notiWire_offIconId = 2788;
                                        }
                                        SC_SkillSound.send(player, player.getId(), 231);
                                        SC_SkillSound.broadcast(player, player.getId(), 231);
                                    } else {
                                        {
                                            notiWire_bit |= 16;
                                            notiWire_onIconId = 1562;
                                        }
                                        {
                                            notiWire_bit |= 32;
                                            notiWire_offIconId = 1562;
                                        }
                                    }
                                    player.setSkillEffect(L1SkillId.IMMUNE_TO_HARM, 32 * 1000);
                                    player.setLastImmuneLevel(pc.getLevel());
                                    {
                                        notiWire_bit |= 1;
                                        notiWire_notiType = eNotiType.RESTAT;
                                    }
                                    {
                                        notiWire_bit |= 2;
                                        notiWire_spellId = L1SkillId.IMMUNE_TO_HARM;
                                    }
                                    {
                                        notiWire_bit |= 4;
                                        notiWire_duration = 32;
                                    }
                                    {
                                        notiWire_bit |= 8;
                                        notiWire_durationShowType = eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC;
                                    }
                                    {
                                        notiWire_bit |= 64;
                                        notiWire_iconPriority = 10;
                                    }
                                    {
                                        notiWire_bit |= 128;
                                        notiWire_tooltipStrId = 966;
                                    }
                                    {
                                        notiWire_bit |= 256;
                                        notiWire_newStrId = 314;
                                    }
                                    {
                                        notiWire_bit |= 512;
                                        notiWire_endStrId = 315;
                                    }
                                    {
                                        notiWire_bit |= 1024;
                                        notiWire_isGood = true;
                                    }
                                    SC_SPELL_BUFF_NOTI.sendFields(player, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType, notiWire_onIconId, notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, notiWire_newStrId, notiWire_endStrId, notiWire_isGood, 0, 0, 0, notiWire_bit);
                                    SC_SkillSound.send(player, player.getId(), 21103);
                                    SC_SkillSound.broadcast(player, player.getId(), 21103);
                                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(noticeA,player);
                                } else {
                                    l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(noticeB,player);
                                }
                            } else {
                                pc.sendPackets("\\fY파티 중인 혈맹원들이 없습니다.");
                            }
                        }
}
                    } else {
                        pc.sendPackets("\\fY파티 중인 혈맹원들이 없습니다.");
                    }
                });
            } else {
                pc.sendPackets("현재 매스 이뮨 투 함을 사용할 수 없는 조건입니다.");
            }
    }
}

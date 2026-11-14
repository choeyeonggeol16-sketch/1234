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
import LIN.SC_SPELL_BUFF_NOTI;
import LIN.SC_SPELL_BUFF_NOTI.eDurationShowType;
import LIN.SC_SPELL_BUFF_NOTI.eNotiType;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.MJCommons;
import l1j.server.server.utils.eKind;

/**
 * Original conditions, debit callbacks and effect order preserved. 
 */
final class ItemUseActiveSkills {
    
    private ItemUseActiveSkills() {
    }
    
    static void use(L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        switch (itemId) {
        case 70706:
            if (pc.getType() == 7 && pc.getLevel() >= 1) {
                if (pc.getCurrentHp() <= 100) {
                    pc.sendPackets("HP가 부족합니다.");
                    return;
                }
                if (!pc.getInventory().checkItem(41246, 100)) {
                    pc.sendPackets("결정체가 부족합니다.");
                    return;
                }
                if (MJCommons.isNonAction(pc)) {
                    return;
                }
                if (pc.hasSkillEffect(L1SkillId.버서크대기)) {
                    int n = pc.getSkillEffectTimeSec(L1SkillId.버서크대기);
                    SC_SystemMessage.send(pc, String.format("\\fY버서크 대기시간 : %d초 후에 사용가능합니다.", n));
                    return;
                }
                ItemUseDurableSkills.useDurableSkillReagent(pc, 41246, 100, ()->{
                    if (pc.hasSkillEffect(L1SkillId.버서크)) {
                        pc.removeSkillEffect(L1SkillId.버서크);
                    }
                    pc.setSkillEffect(L1SkillId.버서크대기, Config.버서크대기시간 * 1000);
                    pc.setSkillEffect(L1SkillId.버서크, Config.버서크시간 * 1000);
                    SC_InventoryIcon.icoNew(pc, L1SkillId.버서크, 3155, Config.버서크시간, true);
                    SC_SkillSound.send(pc, pc.getId(), 8531);
                    SC_SkillSound.broadcast(pc, pc.getId(), 8531);
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    pc.getResistance().addSpecialResistance(eKind.ALL, 20);
                    pc.setCurrentHp(pc.getCurrentHp() - 100);
                    pc.sendPackets("버서크 효과: 모든 내성 +20, 근거리대미지 +20 증가");
                });
            } else {
                pc.sendPackets("현재 버서크를 사용할 수 없는 조건입니다.");
                return;
            }
            break;
        
        case 70708:
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
                    SC_SystemMessage.send(pc, String.format("\\fY프라임 대기시간 : %d초 후에 사용가능합니다.", n));
                    return;
                }
                ItemUseDurableSkills.useDurableSkillReagent(pc, 41246, 1000, ()->{
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
                                    player.sendPackets("프라임의 효과가 적용되었습니다.");
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
                                    player.sendPackets("공성존의 프라임 효과가 적용되었습니다.");
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
                });
            } else {
                pc.sendPackets("현재 프라임을 사용할 수 없는 조건입니다.");
            }
            break;
        
        case 70733:
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
                if (MJCommons.isNonAction(pc)) {
                    return;
                }
                if (pc.hasSkillEffect(L1SkillId.매스이뮨대기시간)) {
                    int n = pc.getSkillEffectTimeSec(L1SkillId.매스이뮨대기시간);
                    SC_SystemMessage.send(pc, String.format("\\fY매스 이뮨 투 함 대기시간 : %d초 후에 사용가능합니다.", n));
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
                ItemUseDurableSkills.useDurableSkillReagent(pc, 40318, Config.매스이뮨마돌, ()->{
                    if (pc.hasSkillEffect(L1SkillId.IMMUNE_TO_HARM)) {
                        pc.removeSkillEffect(L1SkillId.IMMUNE_TO_HARM);
                    }
                    pc.setSkillEffect(L1SkillId.IMMUNE_TO_HARM, 32 * 1000);
                    LIN.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
                    int notiWire_spellId = 0;
                    int notiWire_duration = 0;
                    LIN.SC_SPELL_BUFF_NOTI.eDurationShowType notiWire_durationShowType = null;
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
                        notiWire_notiType = LIN.SC_SPELL_BUFF_NOTI.eNotiType.RESTAT;
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
                        notiWire_durationShowType = LIN.SC_SPELL_BUFF_NOTI.eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC;
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
                    LIN.SC_SPELL_BUFF_NOTI.sendFields(pc, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType, notiWire_onIconId, notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, notiWire_newStrId, notiWire_endStrId, notiWire_isGood, 0, 0, 0, notiWire_bit);
                    SC_SkillSound.send(pc, pc.getId(), 21103);
                    SC_SkillSound.broadcast(pc, pc.getId(), 21103);
                    pc.setLastImmuneLevel(Config.IMMUNE_Level);
                    pc.setCurrentMp(pc.getCurrentMp() - Config.매스이뮨엠피);
                    pc.setSkillEffect(L1SkillId.매스이뮨대기시간, Config.매스이뮨대기시간 * 1000);
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    pc.sendPackets("\\f:매스 이뮨 투함이 시전되었습니다.");
                    if (party != null) {
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
                                        notiWire_notiType = LIN.SC_SPELL_BUFF_NOTI.eNotiType.RESTAT;
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
                                        notiWire_durationShowType = LIN.SC_SPELL_BUFF_NOTI.eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC;
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
                                    LIN.SC_SPELL_BUFF_NOTI.sendFields(player, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType, notiWire_onIconId, notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, notiWire_newStrId, notiWire_endStrId, notiWire_isGood, 0, 0, 0, notiWire_bit);
                                    SC_SkillSound.send(player, player.getId(), 21103);
                                    SC_SkillSound.broadcast(player, player.getId(), 21103);
                                    player.sendPackets("\\f:[" + pc.getName() + "]님의 매스 이뮨 투 함이 시전되었습니다.");
                                } else {
                                    player.sendPackets("\\fY같은 혈맹이 아니므로 매스 이뮨 투 함 미적용");
                                }
                            } else {
                                pc.sendPackets("\\fY파티 중인 혈맹원들이 없습니다.");
                            }
                        }
                    } else {
                        pc.sendPackets("\\fY파티 중인 혈맹원들이 없습니다.");
                    }
                });
            } else {
                pc.sendPackets("현재 매스 이뮨 투 함을 사용할 수 없는 조건입니다.");
            }
            break;
        
        case 70710:
            if (pc.getType() == 5 && pc.getLevel() >= 1) {
                if (pc.hasSkillEffect(L1SkillId.할파스대기시간)) {
                    int n = pc.getSkillEffectTimeSec(L1SkillId.할파스대기시간);
                    SC_SystemMessage.send(pc, String.format("\\fY할파스 대기시간 : %d초 후에 사용가능합니다.", n));
                    return;
                }
                if (MJCommons.isNonAction(pc)) {
                    return;
                }
                if (pc.getInventory().getTypeEquipped(1, 18) < 1) {
                    SC_SystemMessage.send(pc, "체인소드를 착용 후 스킬시전이 가능합니다.");
                    return;
                }
                if (!pc.getInventory().checkItem(210035, 1)) {
                    pc.sendPackets("각인의 뼈조각이 부족합니다.");
                    return;
                }
                if (pc.getCurrentMp() < 18) {
                    pc.sendPackets("MP가 부족합니다.");
                    return;
                }
                ItemUseDurableSkills.useDurableSkillReagent(pc, 210035, 1, ()->{
                    if (pc.hasSkillEffect(L1SkillId.할파스)) {
                        pc.removeSkillEffect(L1SkillId.할파스);
                    }
                    pc.setSkillEffect(L1SkillId.할파스, 128 * 1000);
                    pc.setSkillEffect(L1SkillId.할파스대기시간, 20 * 1000);
                    pc.setCurrentMp(pc.getCurrentMp() - 18);
                    SC_SkillSound.send(pc, pc.getId(), 14455);
                    SC_SkillSound.broadcast(pc, pc.getId(), 14455);
                    SC_InventoryIcon.icoNew(pc, L1SkillId.할파스, 5384, 128, true);
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
                    pc.sendPackets("할파스: 일정 확률로 물리데미지를 피하며 반격합니다.");
                });
            } else {
                pc.sendPackets("현재 [할파스]를 사용할 수 없는 조건입니다");
            }
            break;
        
        case 70714:
            if (pc.getType() == 6 && pc.getLevel() >= 1) {
                if (pc.hasSkillEffect(L1SkillId.포텐셜대기시간)) {
                    int n = pc.getSkillEffectTimeSec(L1SkillId.포텐셜대기시간);
                    SC_SystemMessage.send(pc, String.format("\\fY포텐셜 대기시간 : %d초 후에 사용가능합니다.", n));
                    return;
                }
                if (MJCommons.isNonAction(pc)) {
                    return;
                }
                if (!pc.getInventory().checkItem(210038, 2)) {
                    pc.sendPackets("속성석이 부족합니다.");
                    return;
                }
                if (pc.getCurrentMp() < 30) {
                    pc.sendPackets("MP가 부족합니다.");
                    return;
                }
                ItemUseDurableSkills.useDurableSkillReagent(pc, 210038, 2, ()->{
                    if (pc.hasSkillEffect(L1SkillId.포텐셜)) {
                        pc.removeSkillEffect(L1SkillId.포텐셜);
                    }
                    pc.setSkillEffect(L1SkillId.포텐셜, 128 * 1000);
                    pc.setSkillEffect(L1SkillId.포텐셜대기시간, Config.포텐셜대기시간 * 1000);
                    pc.setCurrentMp(pc.getCurrentMp() - 30);
                    int addHp = (int)Math.round(pc.getMaxHp() * 0.2);
                    int addMp = (int)Math.round(pc.getMaxMp() * 0.2);
                    int addMR = (int)Math.round(pc.getResistance().getMr() * 0.2);
                    int addSP = (int)Math.round(pc.getAbility().getSp() * 0.2);
                    int addDG = (int)Math.round(pc.getDg() * 0.2);
                    int addER = (int)Math.round(pc.getTotalER() * 0.2);
                    pc.setMagicBuffHp(addHp);
                    pc.addMaxHp(pc.getMagicBuffHp());
                    pc.setMagicBuffMp(addMp);
                    pc.addMaxMp(pc.getMagicBuffMp());
                    pc.setMagicBuffMR(addMR);
                    pc.getResistance().addMr(pc.getMagicBuffMR());
                    pc.setMagicBuffSP(addSP);
                    pc.getAbility().addSp(pc.getMagicBuffSP());
                    pc.setMagicBuffDG(addDG);
                    pc.addDg(pc.getMagicBuffDG());
                    pc.setMagicBuffER(addER);
                    pc.addEffectedER(pc.getMagicBuffER());
                    SC_SkillSound.send(pc, pc.getId(), 16647);
                    SC_SkillSound.broadcast(pc, pc.getId(), 16647);
                    SC_InventoryIcon.icoNew(pc, L1SkillId.포텐셜, 5386, 128, true);
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
                    SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
                    SC_SPMR.send(pc);
                    SC_OwnCharAttrDef.send(pc);
                    SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
                    pc.sendPackets("포텐셜의 기운이 느껴집니다.");
                });
            } else {
                pc.sendPackets("현재 [포텐셜]을 사용할 수 없는 조건입니다");
            }
            break;
        
        case 44001:
            if (pc.hasSkillEffect(L1SkillId.버닝샷대기)) {
                int n = pc.getSkillEffectTimeSec(L1SkillId.버닝샷대기);
                SC_SystemMessage.send(pc, String.format("\\fY버닝샷 대기시간 : %d초 후에 사용가능합니다.", n));
                return;
            }
            if (MJCommons.isNonAction(pc)) {
                return;
            }
            if (pc.isInvisble()) {
                SC_SystemMessage.send(pc, "버닝샷은 투명상태로 시전이 불가능합니다.");
                return;
            }
            if (!pc.getInventory().checkItem(40319, 2)) {
                SC_SystemMessage.send(pc, "마법재료가 부족합니다.");
                return;
            }
            if (pc.getType() == 2 && pc.getLevel() >= 85) {
                if (pc.getInventory().getTypeEquipped(1, 20) >= 1 || pc.getInventory().getTypeEquipped(1, 4) >= 1) {
                    ItemUseDurableSkills.useDurableSkillReagent(pc, 40319, 2, ()->{
                        if (pc.hasSkillEffect(L1SkillId.버닝샷)) {
                            pc.removeSkillEffect(L1SkillId.버닝샷);
                        }
                        int A = (int)((double)Config.버닝샷대미지 * 100);
                        pc.setSkillEffect(L1SkillId.버닝샷대기, Config.버닝샷대기 * 1000);
                        pc.setSkillEffect(L1SkillId.버닝샷, Config.버닝샷 * 1000);
                        SC_InventoryIcon.icoNew(pc, L1SkillId.버닝샷, 6914, Config.버닝샷, true);
                        pc.setSkillEffect(STATUS_FREEZE, Config.버닝샷 * 1000);
                        SC_Paralysis.send(pc, SC_Paralysis.TYPE_BIND, true);
                        SC_SkillSound.send(pc, pc.getId(), 10909);
                        SC_SkillSound.broadcast(pc, pc.getId(), 10909);
                        pc.getResistance().addSpecialResistance(eKind.ALL, 3);
                        L1EffectInstance eff1 = L1EffectSpawn.getInstance().spawnEffect(881162, Config.버닝샷 * 1000, pc.getX(), pc.getY(), pc.getMapId());
                        SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
                        pc.sendPackets("버닝샷: 기본 데미지 +" + A + "%,전체내성 +3,PVP대미지감소+8,   지속시간 동안 이동 및 귀환 불가, 활 사거리 감소");
                        L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                    });
                } else {
                    pc.sendPackets("활을 착용 시에 사용 가능합니다.");
                }
            } else {
                pc.sendPackets("현재 [버닝샷]을 사용할 수 없는 조건입니다");
            }
            break;
        
        case 70735:
            if (pc.hasSkillEffect(L1SkillId.리버레이션대기)) {
                int n = pc.getSkillEffectTimeSec(L1SkillId.리버레이션대기);
                SC_SystemMessage.send(pc, String.format("\\fY리버레이션 대기시간 : %d초 후에 사용가능합니다.", n));
                return;
            }
            if (MJCommons.isNonAction(pc)) {
                return;
            }
            if (pc.isInvisble()) {
                SC_SystemMessage.send(pc, "리버레이션은 투명상태로 시전이 불가능합니다.");
                return;
            }
            if (!pc.getInventory().checkItem(40319, 4)) {
                SC_SystemMessage.send(pc, "마법재료가 부족합니다.");
                return;
            }
            if (pc.getCurrentMp() < 20) {
                SC_ServerMessage.send(pc, 278);
                return;
            }
            if (pc.getType() == 2 && pc.getLevel() >= 85) {
                ItemUseDurableSkills.useDurableSkillReagent(pc, 40319, 4, ()->{
                    if (pc.hasSkillEffect(L1SkillId.리버레이션)) {
                        pc.removeSkillEffect(L1SkillId.리버레이션);
                    }
                    pc.setSkillEffect(L1SkillId.리버레이션대기, Config.리버레이션대기 * 1000);
                    pc.setSkillEffect(L1SkillId.리버레이션, Config.리버레이션지속 * 1000);
                    SC_InventoryIcon.icoNew(pc, L1SkillId.리버레이션, 5123, Config.리버레이션지속, true);
                    pc.setCurrentMp(pc.getCurrentMp() - 20);
                    SC_SkillSound.send(pc, pc.getId(), 12335);
                    SC_SkillSound.broadcast(pc, pc.getId(), 12335);
                    pc.sendPackets("리버레이션: 상태 이상 스킬의 시간이 감소됩니다.");
                    L1SkillUse.cast(pc, L1SkillId.보조버프, pc.getId(), pc.getX(), pc.getY(), null, 0, L1SkillUse.TYPE_SPELLSC);
                });
            } else {
                pc.sendPackets("현재 [리버레이션]을 사용할 수 없는 조건입니다");
            }
            break;
        
        case 880800:
            ItemUseDurableSkills.usePotentialScroll(pc, l1iteminstance);
            break;
        
        case 70740:
            ItemUseDurable.UseImmuneBlade(pc);
            break;
        
        }
    }
    
    private static boolean isInWarAreaAndWarTime(L1PcInstance pc) {
        int castleId = L1CastleLocation.getCastleIdByArea(pc);
        return castleId != 0 && MJCastleWarBusiness.getInstance().isNowWar(castleId);
    }
}

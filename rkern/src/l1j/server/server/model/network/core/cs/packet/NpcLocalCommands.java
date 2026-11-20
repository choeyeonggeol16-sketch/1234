package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.NeigeCard;
import l1j.server.server.NeigeCard2;
import l1j.server.server.NeigeCard3;
import l1j.server.server.NeigeGM;
import l1j.server.server.NeigeLow;
import l1j.server.server.NeigeNormal;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.action.ActionControlHandler;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;

import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.session.CUserSession;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import java.text.DecimalFormat;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;

import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;

final class NpcLocalCommands {
    private NpcLocalCommands() {}

	/** Target-independent commands keep precedence over NPC lookup. */
	static boolean dispatchLocalCommand(L1PcInstance pc, String s) {
		if (s.equalsIgnoreCase("deadTrans") || s.equalsIgnoreCase("pvpSet") || s.equalsIgnoreCase("ShowHPMPRecovery")
				|| s.equalsIgnoreCase("showDisableEffectIcon") || s.equalsIgnoreCase("showDungeonTimeLimit")) {
			return true;
		}

		if(s.contains("nenome") || s.contains("nenormal")){
			SC_SkillSound.send(pc, pc.getId(), 21174);
			if(s.contains("nenormal")){
				if (!(pc.getInventory().checkItem(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return true;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return true;
				}
				if (!pc.getInventory().consumeItem(875640379, 1)) {
                    SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
                    return true;
                }
			}
			NeigeNormal.Normal(pc, s);
			return true;
		}
		if(s.contains("nelome") || s.contains("nelowp")){
			SC_SkillSound.send(pc, pc.getId(), 21174);
			if(s.contains("nelowp")){
				if(pc.getLevel() < 48){
					SC_ChatPacket.sendDirect(pc, pc, "\\aG희귀 등급 변신은 48레벨 부터 변신할 수 있습니다.");
					return true;
				}
				if (!(pc.getInventory().checkItem(875640379, 1))) {
					SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
					return true;
				}
				if(pc.isInvisble()){
					pc.sendPackets("지금은 사용할 수 없는 상태입니다.");
					return true;
				}
				if (!pc.getInventory().consumeItem(875640379, 1)) {
                    SC_ChatPacket.sendDirect(pc, pc, "\\aG변신주문서가 부족합니다.");
                    return true;
                }
			}
			NeigeLow.Low(pc, s);
			return true;
		}
		if(s.contains("rnlpoly") || s.contains("rnalce")){
			SC_SkillSound.send(pc, pc.getId(), 21174);
			NeigeCard.Neige(pc, s);
			return true;
		}
		if(s.contains("rnll")){
			SC_SkillSound.send(pc, pc.getId(), 21174);
			NeigeCard2.NeigeLegend(pc, s);
			return true;
		}
		if(s.contains("rnmy")){
			SC_SkillSound.send(pc, pc.getId(), 21174);
			NeigeCard3.NeigeMyth(pc, s);
			return true;
		}
		if(s.contains("rsnmy")){
			SC_SkillSound.send(pc, pc.getId(), 633);
			NeigeCard3.NeigeMyth(pc, s);
			return true;
		}
		if(s.contains("neigma")){
			if(!pc.isGm()){
				pc.sendPackets("관리자만 이용할 수 있습니다.");
				return true;
			}else{
			SC_SkillSound.send(pc, pc.getId(), 21174);
			NeigeGM.getInstance().gm(pc, s);
			}
			return true;
		}
		
		// Retired legacy auto commands: do not pass them to NPC actions.
            if (s.startsWith("ps_potion_") || s.startsWith("ps_autobuff_")
                    || (s.startsWith("ps_") && NpcPlayerMenuHandler.isAsciiNumber(s, 3))) return true;
		
		// 각종 서포터 명령어 액션 핸들러
		if (ActionControlHandler.getInstance().handleAction(pc, s)) {
            return true;
        }

		return false;
	}

    static void poly(CUserSession clientthread, int polyId) {
		L1PcInstance pc = clientthread.getActiveChar();

		if (pc == null) return;
		if (pc.getInventory().checkItem(L1ItemId.ADENA, 100)
                && pc.getInventory().consumeItem(L1ItemId.ADENA, 100)) {

			L1PolyMorph.doPoly(pc, polyId, 1800, L1PolyMorph.MORPH_BY_NPC, false);
		} else {
			SC_ServerMessage.send(pc, 337, "$4");
		}
	}

    static void polyByKeplisha(CUserSession clientthread, int polyId) {
		L1PcInstance pc = clientthread.getActiveChar();

		if (pc == null) return;
		if (pc.getInventory().checkItem(L1ItemId.ADENA, 100)
                && pc.getInventory().consumeItem(L1ItemId.ADENA, 100)) {

			L1PolyMorph.doPoly(pc, polyId, 1800, L1PolyMorph.MORPH_BY_KEPLISHA, false);
		} else {
			SC_ServerMessage.send(pc, 337, "$4");
		}
	}

    static void StatInitialize(L1PcInstance pc) {
		
		L1SkillUse.cast(pc, L1SkillId.CANCELLATION, pc.getId(), pc.getX(), pc.getY(), null, 0,
				L1SkillUse.TYPE_LOGIN);


		if (pc.getWeapon() != null) {
			pc.getInventory().setEquipped(pc.getWeapon(), false, false, false, false);
		}

		SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
		SC_OwnCharStatus2.send(pc);

		for (L1ItemInstance armor : pc.getInventory().getItems()) {
			for (int type = 0; type <= 12; type++) {
				if (armor != null) {
					pc.getInventory().setEquipped(armor, false, false, false, false);
				}
			}
		}
		pc.setReturnStat(pc.getExp());
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		SC_OwnCharStatus2.send(pc);
		SC_ReturnedStat.sendPc(pc, SC_ReturnedStat.START);
		try {
			pc.save();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    /** NPC 9291: elemental attribute menu; no temporary request state. */
    static void elementalAttribute(L1PcInstance pc, int objid, String s, String initialPage) {
        int element = 0;
        int icon = 0;
        int sound = 0;
        if (s.equalsIgnoreCase("fire")) { element = 2; icon = 1; sound = 1822; }
        else if (s.equalsIgnoreCase("water")) { element = 4; icon = 2; sound = 1823; }
        else if (s.equalsIgnoreCase("air")) { element = 8; icon = 3; sound = 1825; }
        else if (s.equalsIgnoreCase("earth")) { element = 1; icon = 4; sound = 1824; }
        if (element != 0) {
            if (!pc.isElf()) {
                SC_SystemMessage.send(pc, "해당 메뉴를 사용할 수 없는 클래스입니다.");
            } else {
                int current = pc.getElfAttr();
                if (current != 0) {
                    if (current == element) {
                        SC_SystemMessage.send(pc, "동일 속성의 정령력은 선택할 수 없습니다.");
                    } else {
                        switch (current) {
                        case 1: SC_SystemMessage.send(pc, "땅 정령의 속성을 먼저 제거하여주세요."); break;
                        case 2: SC_SystemMessage.send(pc, "불 정령의 속성을 먼저 제거하여주세요."); break;
                        case 4: SC_SystemMessage.send(pc, "물 정령의 속성을 먼저 제거하여주세요."); break;
                        case 8: SC_SystemMessage.send(pc, "바람 정령의 속성을 먼저 제거하여주세요."); break;
                        default: break;
                        }
                    }
                    return;
                }
                pc.setElfAttr(element);
                SC_SkillIconGFX.send(pc, 15, icon);
                SC_SkillSound.send(pc, pc.getId(), sound);
            }
            SC_NPCTalkReturn.send(pc, objid, "", null);
            return;
        }
        String htmlid;
        String[] htmldata = null;
        if (s.equalsIgnoreCase("init")) {
            if (!pc.isElf()) htmlid = "siabeth6";
            else if (pc.getElfAttr() == 0) {
                SC_SystemMessage.send(pc, "부여받은 정령의 기운이 없습니다.");
                return;
            } else htmlid = "ellyonne11";
        } else if (s.equalsIgnoreCase("count")) {
            htmlid = "ellyonne12";
            htmldata = new String[] { new DecimalFormat("#,##0").format(elementalResetCost(pc)) };
        } else if (s.equalsIgnoreCase("money")) {
            if (!pc.isElf()) htmlid = "siabeth6";
            else if (pc.getInventory().consumeItem(40308, elementalResetCost(pc))) {
                clearElementalProtection(pc);
                pc.addElfAttrInitCount(1);
                SC_SkillIconGFX.send(pc, 15, 0);
                htmlid = "";
            } else {
                SC_ServerMessage.send(pc, 189);
                htmlid = "ellyonne13";
            }
        } else if (s.equalsIgnoreCase("bm")) {
            if (!pc.isElf()) htmlid = "siabeth6";
            else if (pc.getInventory().consumeItem(200000, 2)) {
                clearElementalProtection(pc);
                SC_SkillIconGFX.send(pc, 15, 0);
                htmlid = "";
            } else {
                SC_ServerMessage.send(pc, 4540);
                htmlid = "ellyonne13";
            }
        } else {
            htmlid = initialPage;
        }
        if (htmlid != null) SC_NPCTalkReturn.send(pc, objid, htmlid, htmldata);
    }

    private static int elementalResetCost(L1PcInstance pc) {
        return 500000 * Math.max(1, pc.getElfAttrInitCount());
    }

    private static void clearElementalProtection(L1PcInstance pc) {
        if (pc.hasSkillEffect(L1SkillId.ELEMENTAL_PROTECTION)) {
            pc.removeSkillEffect(L1SkillId.ELEMENTAL_PROTECTION);
        }
        pc.setElfAttr(0);
    }

    /** Src1..Src5 each cost 100 adena, as in the paid morph helper. */
    static int beginnerMorphId(String s) {
        if (s.equalsIgnoreCase("Src1")) return 13152;
        if (s.equalsIgnoreCase("Src2")) return 15868;
        if (s.equalsIgnoreCase("Src3")) return 15539;
        if (s.equalsIgnoreCase("Src4")) return 13635;
        if (s.equalsIgnoreCase("Src5")) return 15550;
        return 0;
    }

    /** False preserves the original no-HTML early exit at level 83+. */
    static boolean beginnerMorph(L1PcInstance pc, int polyId) {
        if (pc.getLevel() > 82) {
            pc.sendPackets("레벨83 이상인 용사에게는 해줄수가 없소!");
            return false;
        }
        if (pc.getInventory().checkItem(L1ItemId.ADENA, 100)
                && pc.getInventory().consumeItem(L1ItemId.ADENA, 100)) {
            L1PolyMorph.doPoly(pc, polyId, 1800, L1PolyMorph.MORPH_BY_NPC, false);
        } else {
            SC_ServerMessage.send(pc, 189);
        }
        return true;
    }
}

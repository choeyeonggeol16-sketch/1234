package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import l1j.server.Config;
import l1j.server.MJINNSystem.MJINNHelper;
import l1j.server.MJINNSystem.MJINNRoom;
import l1j.server.Stadium.StadiumManager;
import l1j.server.server.model.Getback;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1EffectSpawn;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1ItemBookMark;
import l1j.server.server.utils.CommonUtil;
import static l1j.server.server.model.item.function.TravelUseTables.*;

/** Dedicated item-use handler extracted from a mixed domain. */
final class ClanInnReturnScrollUse {
    private ClanInnReturnScrollUse() {}

    static void use(CIOClient client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        int[] loc = null;
        switch (itemId) {
		case 40124:
		case 30086:// 혈맹 귀환 스크롤
			int castle_id = 0;
			int house_id = 0;
			if (pc.getMapId() == 13005) {
				SC_SystemMessage.send(pc, "얌마 막았어 쓸생각하지마~ 메롱.");
				return;
			}	
			if (pc.getMapId() >= 1708 && pc.getMapId() <= 1712) {
				SC_ServerMessage.send(pc, 647);
				return;
			}
			if (pc.getMapId() == 2236) {
				SC_SystemMessage.send(pc, "이곳에서 사용할수 없습니다.");
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.버닝샷)) {
				SC_SystemMessage.send(pc, "\\fY버닝샷중에는 이동 및 귀환이 불가합니다.");
				return;
			}
			if (pc.hasSkillEffect(L1SkillId.쉐도우제약)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}

			if (pc.hasSkillEffect(L1SkillId.이터디스)) {
				SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				return;
			}

			if (pc.getClanid() != 0) { // 크란 소속
				L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
				if (clan != null) {
					castle_id = clan.getCastleId();
					house_id = clan.getHouseId();
				}
			}
			final boolean clanOwnedReturn = castle_id != 0 || house_id != 0;
			if (castle_id != 0) { // 성주 크란원
				loc = L1CastleLocation.getCastleLoc(castle_id);
			} else if (house_id != 0) { // 아지트 소유 크란원
				loc = L1HouseLocation.getHouseLoc(house_id);
			} else if (pc.getHomeTownId() > 0) {
				loc = L1TownLocation.getGetBackLoc(pc.getHomeTownId());
			} else {
				loc = Getback.GetBack_Location(pc, true);
			}
			if (loc == null || loc.length < 3) return;
			final int returnX = loc[0], returnY = loc[1], returnMap = loc[2];
			ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
				pc.start_teleport(returnX, returnY, returnMap,
					clanOwnedReturn ? pc.getHeading() : 5, 169, true, pc.getAutoHunt());
				if (!clanOwnedReturn) {
					SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
				}
				CreatureItemActions.cancelAbsoluteBarrier(pc); // 아브소르트바리아의 해제
			});
			break;

		case MJINNHelper.INN_KEYID:
			if (pc.isParalyzed() || pc.isSleeped() || pc.isDead()) {
				return;
			}
			if ((pc.hasSkillEffect(L1SkillId.SHOCK_STUN)) || pc.hasSkillEffect(L1SkillId.EMPIRE)
					|| (pc.hasSkillEffect(L1SkillId.ICE_LANCE)) || (pc.hasSkillEffect(L1SkillId.BONE_BREAK))
					|| (pc.hasSkillEffect(L1SkillId.EARTH_BIND) || pc.hasSkillEffect(L1SkillId.DESPERADO))
					|| (pc.hasSkillEffect(L1SkillId.FOG_OF_SLEEPING))) {
				return;
			}
			if (pc.getMapId() == 13005) {
				pc.setCurrentHp(pc.getMaxHp() / 5);
				pc.setCurrentMp(pc.getMaxMp() / 5);
				pc.start_teleport(33431, 32798, 4, 5, 169, true, pc.getAutoHunt()); 

			}	
			MJINNRoom.input(l1iteminstance, pc);
			break;

		case 700023:
			L1ItemBookMark.useBookmarkItem(pc, l1iteminstance);
			break;
        
        }
    }
}

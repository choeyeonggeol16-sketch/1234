/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.   See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.command.executor;

import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import java.sql.Connection;
import java.sql.PreparedStatement;

import l1j.server.DatabaseFactory;
import l1j.server.MJTemplate.MJClassesType.EClassesType;
import l1j.server.server.Account;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.cs.packet.CS_CharSelect;
import l1j.server.server.model.network.core.cs.packet.CS_CommonClick;
import l1j.server.server.model.rank.loader.RankUserLoader;
import LIN.SC_TOP_RANKER_NOTI;
import l1j.server.server.model.network.core.sc.packet.SC_DelSkill;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.utils.SQLUtil;

import l1j.server.server.model.network.core.sc.packet.SC_CharAmount;
public class change implements L1CommandExecutor {

	private change() {
	}

	public static L1CommandExecutor getInstance() {
		return new change();
	}
	

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
			신스킬삭제(pc);
			
			스텟초기화(pc);


		    LIN.SC_TOP_RANKER_NOTI noti = RankUserLoader.getInstance().get(pc.getId());
	        if (noti != null) {
	            noti.set_class(EClassesType.fromGfx(pc.getClassId()).toInt());
	        }

			if (pc.getWeapon() != null)
				pc.getInventory().setEquipped(pc.getWeapon(), false, false, false, false);
			pc.getInventory().takeoffEquip(945);
			SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));
			for (L1ItemInstance armor : pc.getInventory().getItems()) {
				for (int type = 0; type <= 12; type++) {
					if (armor != null) {
						pc.getInventory().setEquipped(armor, false, false, false, false);
					}
				}
			}
			SC_DelSkill.send(pc, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
					255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255);
			deleteSpell(pc);
			deletePassiveSpell(pc);

			/** 스왑 삭제 **/
			pc.clearSlotChange();
			
			pc.setCurrentSprite(pc.getClassId());
			pc.sendShape(pc.getClassId());
			pc.getInventory().removeItem(123124, 1);
			pc.save();

			ThreadPool.getInstance().scheduleRequired(new Runnable() {
				@Override
				public void run() {
					CIOClient clnt = pc.getNetConnection();
					CS_CharSelect.restartProcess(pc);
					Account acc = clnt.getAccount();
					SC_CharAmount.send(clnt, acc.countCharacters(), acc.getCharSlot());
					if (acc.countCharacters() > 0)
						CS_CommonClick.sendCharPacks(clnt);
				}
			}, 500L);

	}
private void 신스킬삭제(L1PcInstance pc) {
		
		if (pc.getInventory().checkItem(70733)) {
			pc.getInventory().consumeItem(70733);
			
		}
		
		if (pc.getInventory().checkItem(90088)) {
			pc.getInventory().consumeItem(90088);
			
		}
		
		
		if (pc.getInventory().checkItem(80888)) {
			pc.getInventory().consumeItem(80888);
			
		}
		
		if (pc.getInventory().checkItem(70706)) {
			pc.getInventory().consumeItem(70706);
			
		}
		
		if (pc.getInventory().checkItem(70708)) {
			pc.getInventory().consumeItem(70708);
			
		}
		
		if (pc.getInventory().checkItem(70710)) {
			pc.getInventory().consumeItem(70710);
			
		}
		
		if (pc.getInventory().checkItem(70714)) {
			pc.getInventory().consumeItem(70714);
			
		}
		
		if (pc.getInventory().checkItem(70721)) {
			pc.getInventory().consumeItem(70721);
			
		}
		
		if (pc.getInventory().checkItem(707777)) {
			pc.getInventory().consumeItem(707777);
			
		}
		
		if (pc.getInventory().checkItem(707779)) {
			pc.getInventory().consumeItem(707779);
			
		}
		
		if (pc.getInventory().checkItem(707781)) {
			pc.getInventory().consumeItem(707781);
			
		}
		
		if (pc.getInventory().checkItem(70735)) {
			pc.getInventory().consumeItem(70735);
			
		}
		if (pc.getInventory().checkItem(70888)) {
			pc.getInventory().consumeItem(70888);
			
		}
		if (pc.hasSkillEffect(L1SkillId.램페이지)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.램페이지);
			pc.removeSkillEffect(L1SkillId.램페이지);
		}

		if (pc.hasSkillEffect(L1SkillId.래스트)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.래스트);
			pc.removeSkillEffect(L1SkillId.래스트);
		}
		if (pc.hasSkillEffect(L1SkillId.맥시멈)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.맥시멈);
			pc.removeSkillEffect(L1SkillId.맥시멈);
		}
		
		
				if (pc.hasSkillEffect(L1SkillId.어드밴스)) {

					pc.removeSkillEffect(L1SkillId.어드밴스);
				}		
				
		if (pc.hasSkillEffect(L1SkillId.에볼루션)) {

			pc.removeSkillEffect(L1SkillId.에볼루션);
		}
		if (pc.hasSkillEffect(L1SkillId.에테리얼)) {

			SC_InventoryIcon.icoEnd(pc, L1SkillId.에테리얼);
		}
		if (pc.hasSkillEffect(L1SkillId.아머데스티니)) {

			pc.removeSkillEffect(L1SkillId.아머데스티니);
		}
		if (pc.hasSkillEffect(L1SkillId.게일샷)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.게일샷);
			pc.removeSkillEffect(L1SkillId.게일샷);
		}
		if (pc.hasSkillEffect(L1SkillId.카배마스터)) {

			pc.removeSkillEffect(L1SkillId.카배마스터);
		}
		if (pc.hasSkillEffect(L1SkillId.버서크)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.버서크);
			pc.removeSkillEffect(L1SkillId.버서크);
		}
		if (pc.hasSkillEffect(L1SkillId.프라임)) {

			pc.removeSkillEffect(L1SkillId.프라임);
		}
		if (pc.hasSkillEffect(L1SkillId.할파스)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.할파스);
			pc.removeSkillEffect(L1SkillId.할파스);
		}
		if (pc.hasSkillEffect(L1SkillId.포스스턴)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.포스스턴);
			pc.removeSkillEffect(L1SkillId.포스스턴);
		}
		if (pc.hasSkillEffect(L1SkillId.레이징포스)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.레이징포스);
			pc.removeSkillEffect(L1SkillId.레이징포스);
		}
		if (pc.hasSkillEffect(L1SkillId.기간틱)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.기간틱);
			pc.removeSkillEffect(L1SkillId.기간틱);
		}
		if (pc.hasSkillEffect(L1SkillId.엠파이어오버로드)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.엠파이어오버로드);
			pc.removeSkillEffect(L1SkillId.엠파이어오버로드);
		}
		if (pc.hasSkillEffect(L1SkillId.샤이닝아머)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.샤이닝아머);
			pc.removeSkillEffect(L1SkillId.샤이닝아머);
		}
		if (pc.hasSkillEffect(L1SkillId.마제스티)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.마제스티);
			pc.removeSkillEffect(L1SkillId.마제스티);
		}
		if (pc.hasSkillEffect(L1SkillId.포텐셜)) {

			pc.removeSkillEffect(L1SkillId.포텐셜);
		}
		if (pc.hasSkillEffect(L1SkillId.네메시스)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.네메시스);
			pc.removeSkillEffect(L1SkillId.네메시스);
		}
		if (pc.hasSkillEffect(L1SkillId.세인트)) {

			pc.removeSkillEffect(L1SkillId.세인트);
		}
		if (pc.hasSkillEffect(L1SkillId.가드피어)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.가드피어);
			pc.removeSkillEffect(L1SkillId.가드피어);
		}
		if (pc.hasSkillEffect(L1SkillId.가드피어호러)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.가드피어호러);
			pc.removeSkillEffect(L1SkillId.가드피어호러);
		}
		if (pc.hasSkillEffect(L1SkillId.버닝)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.버닝);
			pc.removeSkillEffect(L1SkillId.버닝);
		}
		if (pc.hasSkillEffect(L1SkillId.이베이젼)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.이베이젼);
			pc.removeSkillEffect(L1SkillId.이베이젼);
		}
		if (pc.hasSkillEffect(L1SkillId.솔리드캐리지)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.솔리드캐리지);
			pc.removeSkillEffect(L1SkillId.솔리드캐리지);
		}
		if (pc.hasSkillEffect(L1SkillId.프라이드)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.프라이드);
			pc.removeSkillEffect(L1SkillId.프라이드);
		}
		
		if (pc.hasSkillEffect(L1SkillId.일루션리치)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.일루션리치);
			pc.removeSkillEffect(L1SkillId.일루션리치);
		}
		
		if (pc.hasSkillEffect(L1SkillId.일루션골렘)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.일루션골렘);
			pc.removeSkillEffect(L1SkillId.일루션골렘);
		}
		if (pc.hasSkillEffect(L1SkillId.리덕션베테랑)) {
			
			pc.removeSkillEffect(L1SkillId.리덕션베테랑);
		}
		if (pc.hasSkillEffect(L1SkillId.비욘드)) {
			SC_InventoryIcon.icoEnd(pc, L1SkillId.비욘드);
			pc.removeSkillEffect(L1SkillId.비욘드);
		}
	}

	
	private void deleteSpell(L1PcInstance pc) {
		int player = pc.getId();
		Connection con = null;
		PreparedStatement pstm = null;
		try {

			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM character_skills WHERE char_obj_id=?");
			pstm.setInt(1, player);
			pstm.execute();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	private void deletePassiveSpell(L1PcInstance pc) {
		int player = pc.getId();
		Connection con = null;
		PreparedStatement pstm = null;
		try {

			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("DELETE FROM passive_user_info WHERE character_id=?");
			pstm.setInt(1, player);
			pstm.execute();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	private void 스텟초기화(L1PcInstance pc) {
		try {
			
			L1SkillUse.cast(
					pc,
					L1SkillId.CANCELLATION,
					pc.getId(),
					pc.getX(),
					pc.getY(),
					null,
					0,
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
			pc.save();
		} catch (Exception e) {
			System.out.println("스텟초기화 명령어 에러");
			e.printStackTrace();
		}
	}
}

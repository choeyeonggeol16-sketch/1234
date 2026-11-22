/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.model.Instance;

import l1j.server.server.ActionCodes;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.character.action.CAttackAction;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.world.siege.CastleWarBusiness;
import l1j.server.server.templates.L1Npc;

public class L1CataInstance extends L1NpcInstance {
	public L1CataInstance(L1Npc template) {
		super(template);
		// TODO Auto-generated constructor stub
	}

	private static final long serialVersionUID = 1L;

	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if (perceivedFrom == null) return;
		
		if (0 < getCurrentHp()) {
			onNpcAI();
		}
		if (SC_WorldPutObject.get(perceivedFrom.getNetConnection(), this)) {
			perceivedFrom.addKnownObject(this);
		}
	}

	@SuppressWarnings("unused")
	@Override
	public void onTalkAction(L1PcInstance pc) {
		if (pc == null)
			return;
		int objid = getId();
		L1NpcTalkData talking = NPCTalkDataTable.getInstance().getTemplate(getNpcTemplate().get_npcId());
		String htmlid = null;
		String[] htmldata = null;

		if (talking != null) {
			if (!pc.isCrown()) {
				SC_ServerMessage.send(pc, 2498); // 투석기 사용: 실패(혈맹 군주만사용 가능)
				return;
			}
		}
		// html 표시 패킷 송신
		if (htmlid != null) { // htmlid가 지정되고 있는 경우
			if (htmldata != null) { // html 지정이 있는 경우는 표시
				SC_NPCTalkReturn.send(pc, objid, htmlid, htmldata);
			} else {
				SC_NPCTalkReturn.send(pc, objid, htmlid);
			}
		} else {
			if (pc.getLawful() < -1000) { // 플레이어가 카오틱
				SC_NPCTalkReturn.send(pc, talking, objid, 2);
			} else {
				SC_NPCTalkReturn.send(pc, talking, objid, 1);
			}
		}
	}

	@Override
	public void onAction(L1PcInstance pc) {
		if (pc == null)
			return;
		if (getCurrentHp() > 0 && !isDead()) {
			CAttackAction.attackPcNpc(pc, this, true);
		}
	}

	@Override
	public void ReceiveManaDamage(L1Character attacker, int mpDamage) {
		if (attacker == null) return;
		if (mpDamage > 0 && !isDead()) {
			onNpcAI();
			int newMp = getCurrentMp() - mpDamage;
			if (newMp < 0) {
				newMp = 0;
			}
			setCurrentMp(newMp);
		}
	}

	@Override
	public void receiveDamage(L1Character attacker, int damage) {
		if (attacker == null)
			return;

		int castleid = 0;
		if (getNpcId() == 7000084 || getNpcId() == 7000085) { // 켄트
			castleid = 1;
		} else if (getNpcId() == 7000086 || getNpcId() == 7000087) { // 오성
			castleid = 2;
		} else if (getNpcId() == 7000082 || getNpcId() == 7000083) { // 기란
			castleid = 4;
		}

		boolean isNowWar = false;
		isNowWar = CastleWarBusiness.getInstance().isNowWar(castleid);
		if (!isNowWar) {
			return;
		}

		if (getCurrentHp() > 0 && !isDead()) {
			if (damage > 0) {
				if (hasSkillEffect(L1SkillId.FOG_OF_SLEEPING)) {
					removeSkillEffect(L1SkillId.FOG_OF_SLEEPING);
				} else if (hasSkillEffect(L1SkillId.PHANTASM)) {
					removeSkillEffect(L1SkillId.PHANTASM);
				}
			}
			int newHp = applyNpcHpDelta(-(long) damage);
			if (newHp == 0 && !isDead()) {
				int transformId = getNpcTemplate().getTransformId();
				if (transformId == -1) {
					setCurrentHp(0);
					setDead(true);
					setStatus(ActionCodes.ACTION_Die);
					die(this);
				}
			}
        }
	}

	@Override
	public void setCurrentHp(int i) {
		super.setCurrentHp(i);
	}

	@Override
	public void setCurrentMp(int i) {
		super.setCurrentMp(i);
	}

	public void die(L1Character lastAttacker) {
		try {
			setDeathProcessing(true);
			setCurrentHp(0);
			setDead(true);
			setStatus(ActionCodes.ACTION_Die);

			SC_DoActionGFX.broadcast(
					this,
					ActionCodes.ACTION_Die);

			setDeathProcessing(false);
		} catch (Exception e) {
		}
	}
}
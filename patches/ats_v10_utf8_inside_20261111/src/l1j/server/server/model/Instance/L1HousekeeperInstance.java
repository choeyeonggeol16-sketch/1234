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

import l1j.server.Templates.L1House;
import l1j.server.Templates.L1Npc;
import l1j.server.server.datatables.HouseTable;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.model.Attack;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.L1World;
import l1j.server.server.serverpackets.S_NPCTalkReturn;

public class L1HousekeeperInstance extends L1NpcInstance {
	private static final long serialVersionUID = 1L;
	
	public L1HousekeeperInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onAction(L1PcInstance pc) {
		Attack attack = new Attack(pc, this);
		attack.action();
		attack.commit();
	}

	@Override
	public void onTalkAction(L1PcInstance pc) {
		int objid = getId();
		L1NpcTalkData talking = NPCTalkDataTable.getInstance().getTemplate(
				getNpcTemplate().get_npcId());
		int npcid = getNpcTemplate().get_npcId();
		String htmlid = null;
		String[] htmldata = null;
		boolean isOwner = false;

		if (talking != null) {
			// 말을 건넨 PC가 소유자와 그 크란원인가 어떤가 조사한다
			L1Clan clan = L1World.getInstance().getClan(pc.getClanname());
			if (clan != null) {
				int houseId = clan.getHouseId();
				if (houseId != 0) {
					L1House house = HouseTable.getInstance()
							.getHouseTable(houseId);
					if (npcid == house.getKeeperId()) {
						isOwner = true;
					}
				}
			}

			// 소유자와 그 크란원 이외라면 회화 내용을 바꾼다
			if (!isOwner) {
				// Housekeeper가 속하는 아지트를 취득한다
				L1House targetHouse = null;
				for (L1House house : HouseTable.getInstance()
						.getHouseTableList()) {
					if (npcid == house.getKeeperId()) {
						targetHouse = house;
						break;
					}
				}

				// 아지트가에 소유자가 있을지 어떨지 조사한다
				boolean isOccupy = false;
				String clanName = null;
				String leaderName = null;
				for (L1Clan targetClan : L1World.getInstance().getAllClans()) {
					if (targetHouse.getHouseId() == targetClan.getHouseId()) {
						isOccupy = true;
						clanName = targetClan.getClanName();
						leaderName = targetClan.getLeaderName();
						break;
					}
				}

				// 회화 내용을 설정한다
				if (isOccupy) { // 소유자 있어
					htmlid = "agname";
					htmldata = new String[] { clanName, leaderName,
							targetHouse.getHouseName() };
				} else { // 소유자 없음(경매중)
					htmlid = "agnoname";
					htmldata = new String[] { targetHouse.getHouseName() };
				}
			}

			// html 표시 패킷 송신
			if (htmlid != null) { // htmlid가 지정되고 있는 경우
				if (htmldata != null) { // html 지정이 있는 경우는 표시
					pc.sendPackets(new S_NPCTalkReturn(objid, htmlid, htmldata));
				} else {
					pc.sendPackets(new S_NPCTalkReturn(objid, htmlid));
				}
			} else {
				if (pc.getLawful() < -1000) { // 플레이어가 카오틱
					pc.sendPackets(new S_NPCTalkReturn(talking, objid, 2));
				} else {
					pc.sendPackets(new S_NPCTalkReturn(talking, objid, 1));
				}
			}
		}
	}

	@Override
	public void onFinalAction(L1PcInstance pc, String action) {
	}

	public void doFinalAction(L1PcInstance pc) {
	}

}

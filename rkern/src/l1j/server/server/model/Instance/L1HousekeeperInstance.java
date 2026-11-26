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

import l1j.server.server.model.character.action.CAttackAction;

import l1j.server.server.model.world.residence.HouseTable;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.world.residence.L1House;
import l1j.server.server.templates.L1Npc;

public class L1HousekeeperInstance extends L1NpcInstance {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * @param template
	 */
	public L1HousekeeperInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onAction(L1PcInstance pc) {
		CAttackAction.hitAndSwing(pc, this);
	}

	@Override
	public void onTalkAction(L1PcInstance pc) {
		int objid = getId();
		L1NpcTalkData talking = NPCTalkDataTable.getInstance().getTemplate(getNpcTemplate().get_npcId());
		int npcid = getNpcTemplate().get_npcId();
		String htmlid = null;
		String[] htmldata = null;
		boolean isOwner = false;
		
		//System.out.println("내 클릭 NPC ID: " + npcid); 
	    for (l1j.server.server.model.world.residence.L1House h : HouseTable.getInstance().getHouseTableList()) {
	        //System.out.println("메모리 로드된 House ID: " + h.getHouseId() + ", 설정된 Keeper ID: " + h.getKeeperId());
	    }

		if (talking != null) {
			L1Clan clan = L1World.getInstance().getClan(pc.getClanid());
			if (clan != null) {
				int houseId = clan.getHouseId();
				if (houseId != 0) {
					L1House house = HouseTable.getInstance().getHouseTable(houseId);
					if(house == null){
						SC_ChatPacket.sendDirect(pc, pc, "현재 아지트는 사용되지않습니다");
						return;
					}
					
					if (npcid == house.getKeeperId()) {
						isOwner = true;
					}
				}
			}

			if (!isOwner) {
				L1House targetHouse = null;
				for (L1House house : HouseTable.getInstance().getHouseTableList()) {
					if (npcid == house.getKeeperId()) {
						targetHouse = house;
						break;
					}
				}
				if (targetHouse == null) {
					SC_ChatPacket.sendDirect(pc, pc, "현재 아지트는 사용되지않습니다");
					return;
				}

				boolean isOccupy = false;
				String clanName = null;
				String leaderName = null;
				for (L1Clan targetClan : L1World.getInstance().getAllClans()) {
					try {
						if (targetHouse.getHouseId() == targetClan.getHouseId()) {
							isOccupy = true;
							clanName = targetClan.getClanName();
							leaderName = targetClan.getLeaderName();
							break;
						}
					} catch (Exception e) {
					}
				}
				if (isOccupy) {
					htmlid = "agname";
					htmldata = new String[] { clanName, leaderName, targetHouse.getHouseName() };
				} else {
					htmlid = "agnoname";
					htmldata = new String[] { targetHouse.getHouseName() };
				}
			}

			if (htmlid != null) {
				if (htmldata != null) {
					SC_NPCTalkReturn.send(pc, objid, htmlid, htmldata);
				} else {
					SC_NPCTalkReturn.send(pc, objid, htmlid);
				}
			} else {
				if (pc.getLawful() < -1000) {
					SC_NPCTalkReturn.send(pc, talking, objid, 2);
				} else {
					SC_NPCTalkReturn.send(pc, talking, objid, 1);
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

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

import java.util.logging.Logger;

import l1j.server.Templates.L1Npc;
import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.model.Attack;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.serverpackets.S_NPCTalkReturn;
import l1j.server.server.serverpackets.S_ServerMessage;

public class DwarfInstance extends L1NpcInstance {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger _log = Logger.getLogger(DwarfInstance.class
			.getName());


	/**
	 * @param template
	 */
	public DwarfInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onAction(L1PcInstance player) {
		Attack attack = new Attack(player, this);
		attack.calcHit();
		attack.action();
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
		int objid = getId();

		_log.finest("Dwarf activatet: " + objid);

		L1NpcTalkData talking = NPCTalkDataTable.getInstance().getTemplate(
				getNpcTemplate().get_npcId());
		if (talking != null) {
			_log.finest("sending talk packet (init) for : " + objid);
			S_NPCTalkReturn talk = null;
			if (player.getLawful() < -1000) { // 플레이어가 카오틱
				talk = new S_NPCTalkReturn(talking, objid, 2);
			} else {
				talk = new S_NPCTalkReturn(talking, objid, 1);
			}
			player.sendPackets(talk);
		} else {
			_log.finest("No actions for Dwarf id : " + objid);
		}
	}


	@Override
	public void onFinalAction(L1PcInstance player, String Action) {
		if (Action.equalsIgnoreCase("retrieve")) {
			_log.finest("Retrive items in storage");
		} else if (Action.equalsIgnoreCase("retrieve-pledge")) {
			_log.finest("Retrive items in pledge storage");

			if (player.getClanname().equalsIgnoreCase(" ")) {
				_log.finest("player isnt in a pledge");
				S_ServerMessage talk = new S_ServerMessage(
						(S_ServerMessage.NO_PLEDGE), Action);
				player.sendPackets(talk);
			} else {
				_log.finest("player is in a pledge");
			}
		}
	}
}

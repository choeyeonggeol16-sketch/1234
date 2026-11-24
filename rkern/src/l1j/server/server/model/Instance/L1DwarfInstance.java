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

import java.util.logging.Logger;

import l1j.server.server.datatables.NPCTalkDataTable;
import l1j.server.server.model.L1NpcTalkData;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Npc;

public class L1DwarfInstance extends L1NpcInstance {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static Logger _log = Logger.getLogger(L1DwarfInstance.class
			.getName());

	/**
	 * @param template
	 */
	public L1DwarfInstance(L1Npc template) {
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
		int npcId = getNpcTemplate().get_npcId();
		String htmlid = null;

		if (talking != null) {
			if (npcId == 60028) {
				if (!pc.isElf()) {
					htmlid = "elCE1";
				}
			}

			if (pc.is시간제()) {
				SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\aA시간제 아이템\\f3을 착용해제 후 창고 이용이 가능합니다.");
				htmlid = "";
			}
			
			
			
			
			
			if (htmlid != null) {
				SC_NPCTalkReturn.send(pc, objid, htmlid);
			} else {
				if (pc.getLevel() < 5) {
					SC_NPCTalkReturn.send(pc, talking, objid, 2);
				} else {
					SC_NPCTalkReturn.send(pc, talking, objid, 1);
				}
			}



		}
	}


	@Override
	public void onFinalAction(L1PcInstance pc, String Action) {

		if (Action.equalsIgnoreCase("retrieve")) {
			_log.finest("Retrive items in storage");
		} else if (Action.equalsIgnoreCase("retrieve-pledge")) {
			_log.finest("Retrive items in pledge storage");

			if (pc.getClanname().equalsIgnoreCase(" ")) {
				_log.finest("pc isnt in a pledge");
				SC_ServerMessage.send(pc, 
						(SC_ServerMessage.NO_PLEDGE), Action);
			} else {
				_log.finest("pc is in a pledge");
			}
		}
	}
}
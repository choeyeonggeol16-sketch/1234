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

package l1j.server.server.clientpackets;

import java.util.logging.Logger;

import l1j.server.server.GameClient;
import l1j.server.server.datatables.CraftListTable;
import l1j.server.server.datatables.NpcActionTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.npc.action.L1NpcAction;
import l1j.server.server.model.npc.action.L1NpcHtml;
import l1j.server.server.serverpackets.S_CraftItem;
import l1j.server.server.serverpackets.S_NPCTalkReturn;

// Referenced classes of package l1j.server.server.clientpackets:
// ClientBasePacket, C_NPCTalk

public class C_NPCTalk extends ClientBasePacket {

	private static final String C_NPC_TALK = "[C] C_NPCTalk";
	private static Logger _log = Logger.getLogger(C_NPCTalk.class.getName());

	public C_NPCTalk(byte abyte0[], GameClient client) throws Exception {
		super(abyte0);
		int objid = readD();
		L1Object obj = L1World.getInstance().findObject(objid);
		L1PcInstance pc = client.getActiveChar();
		if (pc == null ||  pc.isGhost()) return;		
		if (obj != null && pc != null) {
			
			int npcid = ((L1NpcInstance) obj).getNpcId();
			//제작테이블이 등록된 엔피씨인경우
			if(CraftListTable.getInstance().isCraftNpc(npcid)) {
				pc.Craft_Npcid = 0;
				pc.Craft_Orderid = -1;
				pc.sendPackets(new S_CraftItem(npcid, obj.getId()));
				return;
			}
			
			L1NpcAction action = NpcActionTable.getInstance().get(pc, obj);
			if (action != null) {
				L1NpcHtml html = action.execute("", pc, obj, new byte[0]);
				if (html != null) {
					pc.sendPackets(new S_NPCTalkReturn(obj.getId(), html));
				}
				return;
			}
			obj.onTalkAction(pc);
			// Npcheading(obj, pc);
		} else {
			_log.severe("오브젝트가 발견되지 않습니다 objid=" + objid);
		}
	}
 

	@Override
	public String getType() {
		return C_NPC_TALK;
	}
}

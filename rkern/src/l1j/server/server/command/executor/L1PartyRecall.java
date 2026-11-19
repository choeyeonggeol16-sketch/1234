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

import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1PartyRecall implements L1CommandExecutor {
	private static Logger _log = Logger
			. getLogger(L1PartyRecall.class.getName());

	private L1PartyRecall() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1PartyRecall();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		L1PcInstance target = L1World.getInstance(). getPlayer(arg);

		if (target != null) {
			L1Party party = target.getParty();
			if (party != null) {
				int x = pc.getX();
				int y = pc.getY() + 2;
				short map = pc.getMapId();
				L1PcInstance[] players = party.getMembers();
				try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
       SC_SystemMessage.nativeWriter("메티스님께서 소환하였습니다."),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance pc2 : players) {
					try {
						pc2.start_teleport(x, y, map, 5, 169, true, false);
						l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc2);
					} catch (Exception e) {
						_log.log(Level.SEVERE, "", e);
					}
				}
}
			} else {
				SC_SystemMessage.send(pc, "파티 멤버가 아닙니다. ");
			}
		} else {
			SC_SystemMessage.send(pc, "그러한 캐릭터는 없습니다. ");
		}
	}
}

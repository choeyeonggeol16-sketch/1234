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

import java.io.FileOutputStream;
import l1j.server.server.GameClient;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_Emblem;

public class C_Emblem extends ClientBasePacket {

	private static final String C_EMBLEM = "[C] C_Emblem";

	public C_Emblem(byte abyte0[], GameClient clientthread) throws Exception {
		super(abyte0);
		L1PcInstance player = clientthread.getActiveChar();
		if (player == null) {
			return;
		}
		if (player.getClanRank() != 4) {
			return;
		}
		if (player.getClanid() != 0) {

			FileOutputStream fos = null;
			try {
				byte[] buff = new byte[384];
				for (short cnt = 0; cnt < 384; cnt++) {
					buff[cnt] = (byte) (readC() & 0xff);
				}
				int newEmblemdId = IdFactory.getInstance().nextId();
				String emblem_file = String.valueOf(newEmblemdId);
				fos = new FileOutputStream("emblem/" + emblem_file);
				fos.write(buff, 0, 384);
				L1Clan clan = ClanTable.getInstance().getTemplate(player.getClanid()); 
				clan.setEmblemId(newEmblemdId);
				ClanTable.getInstance().updateClan(clan);
				player.sendPackets(new S_Emblem(newEmblemdId));
				L1World.getInstance().broadcastPacketToAll(new S_Emblem(newEmblemdId));
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println(String.format("%s 엠블럼 등록 오류", player.getName()));
			} finally {
				if (null != fos) {
					fos.close();
				}
				fos = null;
			}
		}
	} 

	@Override
	public String getType() {
		return C_EMBLEM;
	}
}

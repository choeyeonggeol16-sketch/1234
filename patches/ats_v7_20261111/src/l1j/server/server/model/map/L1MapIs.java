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

package l1j.server.server.model.map;

import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Templates.L1Item;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_Disconnect;
import l1j.server.server.serverpackets.S_Invis;

// Referenced classes of package l1j.server.server:
// ClientThread, Shutdown, IpTable, MobTable,
// PolyTable, IdFactory
//

public class L1MapIs {
	private static Logger _log = Logger.getLogger(L1MapIs.class.getName());

	boolean spawnTF = false;

	private static L1MapIs _instance;

	private L1MapIs() {
	}

	public static L1MapIs getInstance() {
		if (_instance == null) {
			_instance = new L1MapIs();
		}
		return _instance;
	}

	public void handleCommands(L1PcInstance pc, String cmdLine) {
		StringTokenizer token = new StringTokenizer(cmdLine);
		String cmd = token.nextToken();
		String param = "";
		while (token.hasMoreTokens()) {
			param = new StringBuilder(param).append(token.nextToken()).append(' ').toString();
		}
		param = param.trim();
		try {

			if (cmd.equalsIgnoreCase("StatusKlck")) { // 엔진 스텟버그킥
				statusbugkick(pc, param); // 유저 접속시 자동실행 로긴투서버 연동
			} else if (cmd.equalsIgnoreCase("Shopbugklck")) { // 상점은 수시로 버그체크중
				shopbugkick(pc);
			} else if (cmd.equalsIgnoreCase("twocharacter")) {
				twocharacterbug(pc, param);
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	private void shopbugkick(L1PcInstance pc) {
		try {
			pc.setGmInvis(true);// 지엠만이 상점 자유이용
			pc.sendPackets(new S_Invis(pc.getId(), 1));// 인비지상태에선 구입불가
			L1World.getInstance().broadcastPacketToAll(new S_Invis(pc.getId(), 1));

		} catch (Exception e) {
		}
	}

	private void twocharacterbug(L1PcInstance pc, String pcName) { // 투케릭버그방지
		try {
			L1PcInstance target = L1World.getInstance().getPlayer(pcName);

			IpTable iptable = IpTable.getInstance(); // 벤아이피등록
			if (target != null) {
				iptable.banIp(target.getNetConnection().getIp());
				target.sendPackets(new S_Disconnect());
			}
		} catch (Exception e) {
		}
	}

	private void statusbugkick(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String nameid = st.nextToken();
			int count = 1;
			if (st.hasMoreTokens()) {
				count = Integer.parseInt(st.nextToken());
			}
			int enchant = 0;
			if (st.hasMoreTokens()) {
				enchant = Integer.parseInt(st.nextToken());
			}
			int isId = 0;
			if (st.hasMoreTokens()) {
				isId = Integer.parseInt(st.nextToken());
			}
			int itemid = 0;
			try {
				itemid = Integer.parseInt(nameid);
			} catch (NumberFormatException e) {
				itemid = ItemTable.getInstance().findItemIdByNameWithoutSpace(nameid);
				if (itemid == 0) {
					return;
				}
			}
			L1Item temp = ItemTable.getInstance().getTemplate(itemid);
			if (temp != null) {
				if (temp.isStackable()) {
					ItemInstance item = ItemTable.getInstance().createItem(itemid);
					item.setEnchantLevel(0);
					item.setCount(count);
					if (isId == 1) {
						item.setIdentified(true);
					}
					if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
						pc.getInventory().storeItem(item); // 착용아이템 확인 + 기본스텟 확인 이상이잇을시 접속불가능
					}
				} else {
					ItemInstance item = null;
					int createCount;
					for (createCount = 0; createCount < count; createCount++) {
						item = ItemTable.getInstance().createItem(itemid);
						item.setEnchantLevel(enchant);
						if (isId == 1) {
							item.setIdentified(true);
						}
						if (pc.getInventory().checkAddItem(item, 1) == L1Inventory.OK) {
							pc.getInventory().storeItem(item); // 아이템 + 기본스텟 이상일시 접속불가능
						} else {
							break;
						}
					}
					if (createCount > 0) {
					}
				}
			} else {
			}
		} catch (Exception e) {
		}
	}
}
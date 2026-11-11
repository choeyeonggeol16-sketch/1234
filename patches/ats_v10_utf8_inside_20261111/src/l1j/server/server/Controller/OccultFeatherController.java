/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful ,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not , write to the Free Software
 * Foundation , Inc., 59 Temple Place - Suite 330, Boston , MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.Controller;

import l1j.server.configure.Config;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.serverpackets.S_SystemMessage;

public class OccultFeatherController implements Runnable {

	public static final int SLEEP_TIME = Config.RATE_PREMIUM_TIME * 60000;

	private static OccultFeatherController _instance;

	public static OccultFeatherController getInstance() {
		if (_instance == null) {
			_instance = new OccultFeatherController();
		}
		return _instance;
	}
	@Override
	public void run() {
		try {
			checkPremiumTime(); 
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private void checkPremiumTime() {
		for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
			if (!pc.isPrivateShop() && !pc.noPlayerck2 && pc != null && !pc.isDead()) {
				int FN = Config.RATE_PREMIUM_NUMBER;
				pc.getInventory().storeItem(ItemId.신비한_날개_깃털, FN);
				pc.sendPackets(new S_SystemMessage("신비한 날개깃털 (" + FN +")개를 얻었습니다."));
			}
		}
	}
}

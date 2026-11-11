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
package l1j.server.server.model;

import java.sql.Timestamp;

import l1j.server.CTSystem.Loader.CTLoadManager;
import l1j.server.Templates.L1EtcItem;
import l1j.server.Templates.L1Item;
import l1j.server.server.GameClient;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.utils.MessageUtil;


public class L1ItemDelay {

	private L1ItemDelay() {
	}

	static class ItemDelayTimer implements Runnable {
		private int _delayId;
		private L1Character _cha;
		
		public Timestamp LogDelayTime() {
			return null;
		} 

		public ItemDelayTimer(L1Character cha, int id, int time) {
			_cha = cha;
			_delayId = id;
		}

		@Override
		public void run() {
			stopDelayTimer(_delayId);
		}

		public void stopDelayTimer(int delayId) {
			_cha.removeItemDelay(delayId);
		}
	}

	public static void onItemUse(GameClient client, ItemInstance item) {
		int delayId = 0;
		int delayTime = 0;
        int itemId = item.getItem().getItemId();
		L1PcInstance pc = client.getActiveChar();

		if (item.getItemId() == CTLoadManager.CTSYSTEM_STORE_ID) {
			return;
		}

		if (item.getItem().getType2() == L1Item.CATEGORY_ETC) {
			delayId = ((L1EtcItem) item.getItem()).get_delayid();
			delayTime = ((L1EtcItem) item.getItem()).get_delaytime();
		} else if (item.getItem().getType2() == L1Item.CATEGORY_WEAPON) {
			return;
		} else if (item.getItem().getType2() == L1Item.CATEGORY_ARMOR) {
			if (itemId == ItemId.투명_망토 || itemId == ItemId.발록_핏빛_망토 || itemId == ItemId.축복_투명_망토) {
				if (item.isEquipped() && !pc.isInvisble()) {
					pc.beginInvisTimer();
				}
			} else {
				return;
			}
		}

		ItemDelayTimer timer = new ItemDelayTimer(pc, delayId, delayTime);

		pc.addItemDelay(delayId, timer);
		GeneralThreadPool.getInstance().schedule(timer, delayTime);
	}

}

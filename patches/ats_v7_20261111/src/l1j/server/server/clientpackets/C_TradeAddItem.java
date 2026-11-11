/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be trading_partnerful,
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

import l1j.server.server.GameClient;
import l1j.server.server.datatables.NoTradable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Trade;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;


public class C_TradeAddItem extends ClientBasePacket {
	private static final String C_TRADE_ADD_ITEM = "[C] C_TradeAddItem";

	public C_TradeAddItem(byte abyte0[], GameClient client) throws Exception {
		super(abyte0);

		int itemid = readD();
		int itemcount = readD();
		L1PcInstance pc = client.getActiveChar();
		if (pc == null) {
			return;
		}
		L1Trade trade = new L1Trade();
		ItemInstance item = pc.getInventory().getItem(itemid);
		if ( item == null) return; 
		
		/** 버그 방지 **/
		if (itemid != item.getId()) {
			return;
		}
		if (!item.isStackable() && itemcount != 1) {
			return;
		}
		if (itemcount <= 0 || item.getCount() <= 0) {
			return;
		}
		if (itemcount > item.getCount()) {
			itemcount = item.getCount();
		}
		if (itemcount > 2000000000) { // 복사 버그 방지
			return;
		}
		if (!item.getItem().isTradable()) {
			pc.sendPackets(new S_ServerMessage(210, item.getItem().getName())); // \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
			return;
		}
		if (item.getBless() >= 128) {
			pc.sendPackets(new S_ServerMessage(210, item.getItem().getName())); // \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
			return;
		}

		
		//교환불가아이템 디비연동 NoTradable
		int itemId = item.getItem().getItemId();
		if (!pc.isGm() && NoTradable.getInstance().isNoTradable(itemId))  {// 
			pc.sendPackets(new S_SystemMessage("\\fW[!] : 해당 아이템은 교환 불가능합니다."));
			return;
		}
		/** 시간제아이템 교환불가 */
		if (item.getEndTime() != null) {
			pc.sendPackets(new S_SystemMessage("\\fW시간제 아이템은 교환할수 없습니다."));
			return;
		}
		if (item.isEquipped()) {
			pc.sendPackets(new S_ServerMessage(906)); //
			return;
		}
		DollInstance doll = pc.getMagicDoll();
		if ((doll != null) && (item.getId() == doll.getItemObjId())) {
			pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));
			return;
		}
		if (pc.getTradeOk()) {
			return;
		}

		Object[] petlist = pc.getPetList().values().toArray();
		for (Object petObject : petlist) {
			if (petObject instanceof PetInstance) {
				PetInstance pet = (PetInstance) petObject;
				if (item.getId() == pet.getItemObjId()) {
					// \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
					pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));
					return;
				}
			}
		}
		L1PcInstance tradingPartner = (L1PcInstance) L1World.getInstance().findObject(pc.getTradeID());
		if (tradingPartner == null) {
			return;
		}
		if (pc.getTradeOk() || tradingPartner.getTradeOk()) {
			pc.sendPackets(new S_SystemMessage("올리기 불가능 : 한쪽이 완료를 누른 상태"));
			tradingPartner.sendPackets(new S_SystemMessage("올리기 불가능 : 한쪽이 완료를 누른 상태"));
			return;
		}
		if (tradingPartner.getInventory().checkAddItem(item, itemcount) != L1Inventory.OK) {
			tradingPartner.sendPackets(new S_ServerMessage(270));
			pc.sendPackets(new S_ServerMessage(271));
			return;
		}
		trade.TradeAddItem(pc, itemid, itemcount); 
	}

	@Override
	public String getType() {
		return C_TRADE_ADD_ITEM;
	}
}

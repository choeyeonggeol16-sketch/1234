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

import java.util.ArrayList;

import l1j.server.Templates.L1PrivateShopBuyList;
import l1j.server.Templates.L1PrivateShopSellList;
import l1j.server.server.ActionCodes;
import l1j.server.server.GameClient;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.serverpackets.S_ChatPacket;
import l1j.server.server.serverpackets.S_Disconnect;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_DoActionShop;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;

public class C_Shop extends ClientBasePacket {

	private static final String C_SHOP = "[C] C_Shop";

	public C_Shop(byte abyte0[], GameClient clientthread) {
		super(abyte0);

		L1PcInstance pc = clientthread.getActiveChar();
		if (pc == null || pc.isGhost()) {
			return;
		}
		if (pc.isInvisble()) {
			pc.sendPackets(new S_ServerMessage(755));
			return;
		}
		if (pc.getMapId() != 350) {
			pc.sendPackets(new S_SystemMessage("개인상점은 기란시장에서만  열수 있습니다."));
			return;
		}

		ArrayList<L1PrivateShopSellList> sellList = pc.getSellList();
		ArrayList<L1PrivateShopBuyList> buyList = pc.getBuyList();
		ItemInstance checkItem;
		boolean tradable = true;

		int type = readC();
		if (type == 0) { // 개시
			int sellTotalCount = readH();
			int sellObjectId;
			int sellPrice;
			int sellCount;
			for (int i = 0; i < sellTotalCount; i++) {
				sellObjectId = readD();
				sellPrice = readD();
				sellCount = readD();
				
				/** 개인상점 오류 수정 */
				if(sellTotalCount == 8){   
					pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "[물품등록은 7개까지만 가능합니다.]"), 1));
					return;
				}
				
				// 거래 가능한 아이템이나 체크
				checkItem = pc.getInventory().getItem(sellObjectId);
				
				// 거래 가능한 아이템이나 체크
				checkItem = pc.getInventory().getItem(sellObjectId);
				if (sellObjectId != checkItem.getId()) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if (!checkItem.isStackable() && sellCount != 1) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if (sellCount > checkItem.getCount()) {
					sellCount = checkItem.getCount();
				}
				if (checkItem.getCount() < sellCount || checkItem.getCount() <= 0 || sellCount <= 0) {
				     sellList.clear();  
				     buyList.clear();				     
					 return;
				}
				
				//## (버그 방지) 개인 상점을 이용한 아덴 불리기 버그 방지 [짱돌] 
				if (checkItem.getBless() >= 128) {
					pc.sendPackets(new S_ServerMessage(210, checkItem.getItem().getName()));  
					return;
				}
				if (!checkItem.getItem().isTradable()) {
					tradable = false;
					pc.sendPackets(new S_ServerMessage(166,checkItem.getItem().getName(), "거래 불가능합니다."));
				}
				Object[] petlist = pc.getPetList().values().toArray();
				for (Object petObject : petlist) {
					if (petObject instanceof PetInstance) {
						PetInstance pet = (PetInstance) petObject;
						if (checkItem.getId() == pet.getItemObjId()) {
							tradable = false;
							pc.sendPackets(new S_ServerMessage(166, // \f1%0이%4%1%3%2
									checkItem.getItem().getName(), "거래 불가능합니다."));
							break;
						}
					}
				}
				L1PrivateShopSellList pssl = new L1PrivateShopSellList();
				pssl.setItemObjectId(sellObjectId);
				pssl.setSellPrice(sellPrice);
				pssl.setSellTotalCount(sellCount);
				sellList.add(pssl);
			}
			int buyTotalCount = readH();
			int buyObjectId;
			int buyPrice;
			int buyCount;
			for (int i = 0; i < buyTotalCount; i++) {
				buyObjectId = readD();
				buyPrice = readD();
				buyCount = readD();
				
				/** 개인상점 오류 수정 */
				if(sellTotalCount == 8){  
					pc.sendPackets(new S_ChatPacket(pc, String.format("%s", "[물품등록은 7개까지만 가능합니다.]"), 1));
					return;
				}
				
				// 거래 가능한 아이템이나 체크
				checkItem = pc.getInventory().getItem(buyObjectId);
				
				/*버그방지*/
				if (buyObjectId != checkItem.getId()) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if (!checkItem.isStackable() && buyCount != 1) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if (buyCount <= 0 || checkItem.getCount() <= 0) {
					pc.sendPackets(new S_Disconnect());
					return;
				}
				if (buyCount > checkItem.getCount()) {
					buyCount = checkItem.getCount();
				}
				/*버그방지*/
				// 거래 가능한 아이템이나 체크
				
				
				if (!checkItem.getItem().isTradable()) {
					tradable = false;
					pc.sendPackets(new S_ServerMessage(166, // \f1%0이%4%1%3%2
							checkItem.getItem().getName(), "거래 불가능합니다."));
				}
				Object[] petlist = pc.getPetList().values().toArray();
				for (Object petObject : petlist) {
					if (petObject instanceof PetInstance) {
						PetInstance pet = (PetInstance) petObject;
						if (checkItem.getId() == pet.getItemObjId()) {
							tradable = false;
							pc.sendPackets(new S_ServerMessage(166, // \f1%0이%4%1%3%2
									checkItem.getItem().getName(), "거래 불가능합니다."));
							break;
						}
					}
				}
				L1PrivateShopBuyList psbl = new L1PrivateShopBuyList();
				psbl.setItemObjectId(buyObjectId);
				psbl.setBuyPrice(buyPrice);
				psbl.setBuyTotalCount(buyCount);
				buyList.add(psbl);
			}
			if (!tradable) { // 거래 불가능한 아이템이 포함되어 있는 경우, 개인 상점 종료
				sellList.clear();
				buyList.clear();
				pc.setPrivateShop(false);
				S_DoActionGFX s = S_DoActionGFX.get(pc.getId(), ActionCodes.ACTION_Idle);
				pc.sendPackets(s);
				pc.broadcastPacket(s);
				return;
			}
			byte[] chat = readByte();
			pc.setShopChat(chat);
			pc.setPrivateShop(true);
			pc.sendPackets(new S_DoActionShop(pc.getId(), ActionCodes.ACTION_Shop, chat));
			pc.broadcastPacket(new S_DoActionShop(pc.getId(), ActionCodes.ACTION_Shop, chat));
		} else if (type == 1) { // 종료
			sellList.clear();
			buyList.clear();
			pc.setPrivateShop(false);
			S_DoActionGFX s = S_DoActionGFX.get(pc.getId(), ActionCodes.ACTION_Idle);
			pc.sendPackets(s);
			pc.broadcastPacket(s);
		}
	}

	@Override
	public String getType() {
		return C_SHOP;
	}

}

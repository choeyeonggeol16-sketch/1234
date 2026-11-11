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

import l1j.server.configure.Config;
import l1j.server.server.GameClient;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NoDropItem;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.serverpackets.S_Disconnect; //## (버그 방지) 복사 버그 방지 
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage; //## (버그 방지) 복사 버그 방지 
import manager.SJManager;

public class C_DropItem extends ClientBasePacket {
	
	private static final String C_DROP_ITEM = "[C] C_DropItem";

	public C_DropItem(byte[] decrypt, GameClient client) throws Exception {
		super(decrypt);
		int x = readH();
		int y = readH();
		int objectId = readD();
		int count = readD();

		L1PcInstance pc = client.getActiveChar();
		if (pc == null) {
			return;
		}
		if (pc.isGhost()) {
			return;
		}
		if (pc.isTwoLogin(pc)) {
			return;
		}
		
		if (pc.getOnlineStatus() != 1) {
			pc.sendPackets(new S_Disconnect());
			return;
		}

		ItemInstance item = pc.getInventory().getItem(objectId);
		if (item != null) {
			if (!item.getItem().isTradable() && !pc.isGm()) {
				// \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
				pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));
				return;
			}
			int itemType = item.getItem().getType2();
			int itemId = item.getItem().getItemId();
			if ((itemType == 1 && count != 1) || (itemType == 2 && count != 1)) {
				pc.sendPackets(new S_Disconnect());
				return;
			}
			if (item.getCount() <= 0) {
				pc.sendPackets(new S_Disconnect());
				return;
			}
			if (item.getCount() <= 0) {
				pc.sendPackets(new S_Disconnect());
				return;
			}
			if (!item.isStackable() && count != 1) {
				pc.sendPackets(new S_Disconnect());
				return;
			}
			if (item.getCount() < count || count <= 0 || count > 2000000000) {
				pc.sendPackets(new S_Disconnect());
				return;
			}
			if (count > item.getCount()) {
				count = item.getCount();
			}			
			if (item.getBless() >= 128 && !pc.isGm()) {
				pc.sendPackets(new S_ServerMessage(210, item.getItem().getName())); // \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
				return;
			}
			
			if (item.getItem().getType2() == 1 || item.getItem().getType2() == 2) {
				if (count > 1 || item.getCount() > 1) {
					count = 1;
					return;
				}
			}
			if (Config.QUIZSYM && !pc.isGm()) {
				if (!pc.isQuizValidated()) {
					pc.sendPackets(new S_SystemMessage("퀴즈 인증을 하지 않으셨습니다."));
					pc.sendPackets(new S_SystemMessage("먼저 [.퀴즈인증]으로 퀴즈 인증 후 시도해주세요."));
					return;
				}
			}
			
			/** 드랍방지 외부화 db nodropitem 테이블 에서 리스트 추가 **/
			if (!pc.isGm() && NoDropItem.getInstance().isNoDropItem(itemId))  {
				String itemName = ItemTable.getInstance().findItemIdByName(itemId);
			    pc.sendPackets(new S_SystemMessage("\\fY경고: 당신은 해당 \\aG["+ itemName +"]\\aA 을(를) 버릴수 없습니다."));
				return;
			}
			if (pc.getLevel() < Config.ALT_DROPLEVELLIMIT && !pc.isGm()) {
				pc.sendPackets(new S_SystemMessage("레벨 " + Config.ALT_DROPLEVELLIMIT + "부터 아이템을 버릴 수 있습니다."));
				return;
			}
			/** 시간제아이템 교환불가 */
			if (item.getEndTime() != null){
				pc.sendPackets(new S_SystemMessage("\\fW시간제 아이템은 드랍할수 없습니다."));
				return;
			}

			if (Math.max(Math.abs(x - pc.getX()), Math.abs(y - pc.getY())) > 2) {
				return;
			}
			if (x > pc.getX() + 1 || x < pc.getX() - 1 || y > pc.getY() + 1 || y < pc.getY() - 1) {
				return;
			}
			if (pc.getLocation().getTileLineDistance(pc.getLocation()) > 1) {
				pc.sendPackets(new S_SystemMessage("1칸 이내에만 아이템을 버릴수 있습니다."));
				return;
			}
			if(item.getCount() > 1 && pc.getLocation().getTileLineDistance(pc.getLocation()) > 1) {
				pc.sendPackets(new S_SystemMessage("해당 패킷은 당신이 보낼수 없는 패킷 입니다."));
				return;
			}
			DollInstance doll = pc.getMagicDoll();
			if ((doll != null) && (item.getId() == doll.getItemObjId())) {
				pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));
				return;
			} 
			
		//	if (pc.getLevel() <= 55) {
		//		pc.sendPackets(new S_SystemMessage("레벨 55 미만의 캐릭터는 아이템을 버릴 수 없습니다."));
		//		return;
		//	}
			
			//## (버그 방지) 복사 버그 방지 
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
			if (item.isEquipped()) {
				// \f1삭제할 수 없는 아이템이나 장비 하고 있는 아이템은 버릴 수 없습니다.
				pc.sendPackets(new S_ServerMessage(125));
				return;
			}
			if (((item.getCount() >= 100) && ((item.getItemId() == 41159) || (item.getItemId() == 40087)
					|| (item.getItemId() == 40074))) || (item.getEnchantLevel() > 0)
					|| ((item.getCount() >= 1000000) && (item.getItemId() == 40308))
					|| ((item.getCount() >= 1000) && (item.getItemId() != 40308)))
			SJManager.getInstance().PicupAppend(item.getLogName(), pc.getName(), count, 2);
			pc.getInventory().tradeItem(item, count, L1World.getInstance().getInventory(x, y, pc.getMapId()));
			if (!pc.isGm()) {
				item.setGiveItem(true);
			}
			pc.getLight().turnOnOffLight();
		}
	}

	@Override
	public String getType() {
		return C_DROP_ITEM;
	}
}

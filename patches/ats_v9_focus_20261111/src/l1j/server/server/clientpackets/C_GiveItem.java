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

import java.util.Random;
import l1j.server.Templates.L1Npc;
import l1j.server.Templates.L1PetType;
import l1j.server.configure.Config;
import l1j.server.server.GameClient;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NoDropItem;
import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.PetInstance;
import l1j.server.server.model.Instance.SummonInstance;
import l1j.server.server.serverpackets.S_Disconnect;
import l1j.server.server.serverpackets.S_ItemName;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SystemMessage;

public class C_GiveItem extends ClientBasePacket {

	private static final String C_GIVE_ITEM = "[C] C_GiveItem";

	private static Random _random = new Random();

	public C_GiveItem(byte decrypt[], GameClient client) {
		super(decrypt);
		int targetId = readD();
		int x = readH();
		int y = readH();
		int itemId = readD();
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

		L1Object object = L1World.getInstance().findObject(targetId);
		if (object == null || !(object instanceof L1NpcInstance)) {
			return;
		}
		L1NpcInstance target = (L1NpcInstance) object;
		if (!isNpcItemReceivable(target.getNpcTemplate())) {
			return;
		}
		L1Inventory targetInv = target.getInventory();

		L1Inventory inv = pc.getInventory();
		ItemInstance item = inv.getItem(itemId);
		if (item == null) {
			return;
		}
		if (item.isEquipped()) {
			pc.sendPackets(new S_ServerMessage(141)); // \f1장비 하고 있는 것은, 사람에게 건네줄 수가 없습니다.
			return;
		}
		if (!pc.isGm() && item.getBless() >= 128) { // 봉인
			pc.sendPackets(new S_ServerMessage(141));
			return;
		}
		if (Config.QUIZSYM && !pc.isGm()) {
			if (!pc.isQuizValidated()) {
				pc.sendPackets(new S_SystemMessage("퀴즈 인증을 하지 않으셨습니다."));
				pc.sendPackets(new S_SystemMessage("먼저 [.퀴즈인증]으로 퀴즈 인증 후 거래를 시도해주세요.."));
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
			pc.sendPackets(new S_SystemMessage("레벨 " + Config.ALT_DROPLEVELLIMIT + "부터 아이템을 버릴 수 있습니다.G"));
			return;
		}
		if (item.getEndTime() != null) {
			pc.sendPackets(new S_SystemMessage("\\fW시간제 아이템은 교환할수 없습니다."));
			return;
		}
		
//## (버그 방지) 수량, 비수량 버그 방지 
		if (count != 1) { // NPC에 넣을 수 있는 아이템 갯수를 1개로 제한
			return;
		}
		if (itemId != item.getId()) {
			pc.sendPackets(new S_Disconnect());
			return;
		}
		if (item.getCount() <= 0 || item.getCount() < count || count <= 0) {
			pc.sendPackets(new S_ServerMessage(210, item.getItem().getName())); // \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
			return;
		}
		if (!item.isStackable() && count != 1) {
			pc.sendPackets(new S_Disconnect());
			return;
		}
		if (item.getEndTime() != null) {
			return;
		}
		if (count >= item.getCount()) {
			count = item.getCount();
		}
//## (버그 방지) 수량, 비수량 버그 방지 
		if (!item.getItem().isTradable() && !pc.isGm()) {
			pc.sendPackets(new S_ServerMessage(210, item.getItem().getName())); // \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
			return;
		}
		for (Object petObject : pc.getPetList().values()) {
			if (petObject instanceof PetInstance) {
				PetInstance pet = (PetInstance) petObject;
				if (item.getId() == pet.getItemObjId()) {
					// \f1%0은 버리거나 또는 타인에게 양일을 할 수 없습니다.
					pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));
					return;
				}
			}
		}
		DollInstance doll = pc.getMagicDoll();
		if ((doll != null) && (item.getId() == doll.getItemObjId())) {
			pc.sendPackets(new S_ServerMessage(210, item.getItem().getName()));
			return;
		}
		if (targetInv.checkAddItem(item, count) != L1Inventory.OK) {
			pc.sendPackets(new S_ServerMessage(942)); // 상대의 아이템이 너무 무겁기 (위해)때문에, 더 이상 줄 수 없습니다.
			return;
		}
		if (!pc.isGm()) {
			item.setGiveItem(true);
		}
		item = inv.tradeItem(item, count, targetInv);
		target.onGetItem(item);
		target.getLight().turnOnOffLight();
		pc.getLight().turnOnOffLight();

		L1PetType petType = PetTypeTable.getInstance().get(target.getNpcTemplate().get_npcId());
		if (petType == null || target.isDead()) {
			return;
		}

		if (item.getItemId() == petType.getItemIdForTaming()) {
			tamePet(pc, target);
		}
		if (item.getItemId() == 40070 && petType.canEvolve() // ## [A149] 승자의 열매 추가 관련
				&& petType.getItemIdForTaming() == 40057 // #
				|| petType.getItemIdForTaming() == 40060) { // #
			evolvePet(pc, target);
		}
		if (item.getItemId() == 41310 && petType.canEvolve() // ## [A149] 승자의 열매 추가
				&& petType.getItemIdForTaming() == 0) { // #
			evolvePet(pc, target); // #
		} // #
	}

	private final static String receivableImpls[] = new String[] { "L1Npc", // NPC
			"L1Monster", // monster
			"L1Guardian", // 요정 숲의 수호자
			"L1Teleporter", // 텔레 포터
			"L1Guard" }; // 가이드

	private boolean isNpcItemReceivable(L1Npc npc) {
		for (String impl : receivableImpls) {
			if (npc.getImpl().equals(impl)) {
				return true;
			}
		}
		return false;
	}

	private void tamePet(L1PcInstance pc, L1NpcInstance target) {
		if (target instanceof PetInstance || target instanceof SummonInstance) {
			return;
		}

		int petcost = 0;
		Object[] petlist = pc.getPetList().values().toArray();
		for (Object pet : petlist) {
			petcost += ((L1NpcInstance) pet).getPetcost();
		}
		int charisma = pc.getAbility().getTotalCha();
		if (pc.isCrown()) { // 군주
			charisma += 6;
		} else if (pc.isElf()) { // 요정
			charisma += 12;
		} else if (pc.isWizard()) { // 마법사
			charisma += 6;
		} else if (pc.isDarkelf()) { // 다크엘프
			charisma += 6;
		}
		charisma -= petcost;

		L1PcInventory inv = pc.getInventory();
		if (charisma >= 6 && inv.getSize() < 180) {
			if (isTamePet(target)) {
				ItemInstance petamu = inv.storeItem(40314, 1); // 펫의 아뮤렛트
				if (petamu != null) {
					new PetInstance(target, pc, petamu.getId());
					pc.sendPackets(new S_ItemName(petamu));
				}
			} else {
				pc.sendPackets(new S_ServerMessage(324)); // 길들이는데 실패했습니다.
			}
		}
	}

	private void evolvePet(L1PcInstance pc, L1NpcInstance target) {
		if (!(target instanceof PetInstance)) {
			return;
		}
		L1PcInventory inv = pc.getInventory();
		PetInstance pet = (PetInstance) target;
		ItemInstance petamu = inv.getItem(pet.getItemObjId());
		if (pet.getLevel() >= 30 && // Lv30 이상
				pc == pet.getMaster() && // 자신의 애완동물
				petamu != null) {
			ItemInstance highpetamu = inv.storeItem(40316, 1);
			if (highpetamu != null) {
				pet.evolvePet( // 진화시킨다
						highpetamu.getId());
				pc.sendPackets(new S_ItemName(highpetamu));
				inv.removeItem(petamu, 1);
			}
		}
	}

	private boolean isTamePet(L1NpcInstance npc) {
		boolean isSuccess = false;
		int npcId = npc.getNpcTemplate().get_npcId();
		if (npcId == 45313) { // 호랑이
			if (npc.getMaxHp() / 3 > npc.getCurrentHp() // HP가1/3미만으로1/16의 확률
					&& _random.nextInt(16) == 15) {
				isSuccess = true;
			}
		} else {
			if (npc.getMaxHp() / 3 > npc.getCurrentHp()) {
				isSuccess = true;
			}
		}

		if (npcId == 45313 || npcId == 45044 || npcId == 45711) { // 호랑이, 라쿤, 아기 진돗개
			if (npc.isResurrect()) { // 부활 후는 길들이기 불가
				isSuccess = false;
			}
		}

		return isSuccess;
	}

	@Override
	public String getType() {
		return C_GIVE_ITEM;
	}
}

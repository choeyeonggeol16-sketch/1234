/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package l1j.server.server.model.item.function;

import l1j.server.server.datatables.CObjectDB;
import java.io.File;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import l1j.server.Config;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

@XmlAccessorType(XmlAccessType.FIELD)
public class L1HealingPotion {

	private static final Logger _log = Logger.getLogger(L1HealingPotion.class.getName());


	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "ItemEffectList")
	private static class ItemEffectList implements Iterable<L1HealingPotion> {
		@XmlElement(name = "Item")
		private List<L1HealingPotion> _list;

		public Iterator<L1HealingPotion> iterator() {
			return _list.iterator();
		}
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	private static class Effect {
		@XmlAttribute(name = "Min")
		private int _min;

		private int getMin() {
			return _min;
		}

		@XmlAttribute(name = "Max")
		private int _max;

		private int getMax() {
			return _max;
		}

		@XmlAttribute(name = "GfxId")
		private int _gfxid;

		private int getGfxId() {
			return _gfxid;
		}

		@XmlAttribute(name = "MapId")
		private int _mapid;

		private int getMapId() {
			return _mapid;
		}
	}

	private static final String _path = "./data/xml/Item/HealingPotion.xml";

	// Construct off-thread, publish once, then read without mutation or boxed IDs.
	private static volatile Int2ObjectMap<L1HealingPotion> _dataMap = new Int2ObjectOpenHashMap<>();

	public static L1HealingPotion get(int id) {
		return _dataMap.get(id);
	}

	@XmlAttribute(name = "ItemId")
	private int _itemId;

	private int getItemId() {
		return _itemId;
	}

	@XmlAttribute(name = "Remove")
	private int _remove;

	private int getRemove() {
		return _remove;
	}

	@XmlElement(name = "Effect")
	private Effect[] _effects;

	private Effect[] getEffects() {
		return _effects;
	}

	private static boolean loadXml(Int2ObjectMap<L1HealingPotion> dataMap) {
		// PerformanceTimer timer = new PerformanceTimer();
		// System.out.print("■ 물약회복량 데이터 .......................... ");
		try {
			JAXBContext context = JAXBContext.newInstance(L1HealingPotion.ItemEffectList.class);

			Unmarshaller um = context.createUnmarshaller();

			File file = new File(_path);
			ItemEffectList list = (ItemEffectList) um.unmarshal(file);

			for (L1HealingPotion each : list) {
				if (CObjectDB.GetItem(each.getItemId()) == null) {
					System.out.print("아이템 ID " + each.getItemId() + " 의 템플릿이 발견되지 않았습니다.");
				} else {
					dataMap.put(each.getItemId(), each);
				}
			}
			return true;
		} catch (Exception e) {
			_log.log(Level.SEVERE, _path + "의 로드에 실패.", e);
			return false;
		}
		// System.out.println("■ 로딩 정상 완료 " + timer.get() + "ms");
	}

	public static synchronized void load() {
		Int2ObjectMap<L1HealingPotion> dataMap = new Int2ObjectOpenHashMap<>(_dataMap);
		if (loadXml(dataMap)) _dataMap = dataMap;
	}

	public static synchronized void reload() {
		Int2ObjectMap<L1HealingPotion> dataMap = new Int2ObjectOpenHashMap<>();
		if (loadXml(dataMap)) _dataMap = dataMap;
	}

	public boolean use(L1PcInstance pc, L1ItemInstance item) {
		if (pc == null || item == null || pc.isItemTransferBusy()
				|| pc.getInventory().getItem(item.getId()) != item
				|| item.getCount() <= 0) return false;
		if (pc.hasSkillEffect(L1SkillId.DECAY_POTION)) { 
			SC_ServerMessage.send(pc, 698); // 마력에 의해 아무것도 마실 수가 없습니다.
			return false;
		}

		int maxChargeCount = item.getItem().getMaxChargeCount();
		int chargeCount = item.getChargeCount();
		if ((maxChargeCount > 0 && chargeCount <= 0)
				|| (getRemove() > 0 && (chargeCount > 0
						? chargeCount < getRemove() : item.getCount() < getRemove()))) {
			SC_ServerMessage.send(pc, 79);
			return false;
		}

		Effect effect = null;
		Effect[] effects = getEffects();
		for (int i = 0, n = effects.length; i < n; i++) {
			Effect each = effects[i];
			if (each.getMapId() != 0 && pc.getMapId() != each.getMapId()) {
				continue;
			}
			effect = each;
			break;
		}

		if (effect == null) {
			SC_ServerMessage.send(pc, 79);
			return false;
		}

		cancelAbsoluteBarrier(pc);
		long ss = SC_SkillSound.pack(pc.getId(), effect.getGfxId());
		SC_SkillSound.sendPacked(pc, ss);
		SC_SkillSound.broadcastPacked(pc, ss);
		
		SC_ServerMessage.send(pc, 77);
		
		int chance = effect.getMax() - effect.getMin();
		double healHp = effect.getMin();
		if (chance > 0) {
			healHp += ThreadLocalRandom.current().nextInt(chance) + 1;	
		}

		//  물약 회복률(%)
		int addHealHpPer = pc.getResistance().getPotionRecRate();

		// 추가 물약 회복량
		int addHealHp = pc.getResistance().getPotionRecAmt(); 
		
		addHealHpPer = Math.min(100, addHealHpPer);

		healHp += (int)(healHp * (addHealHpPer / 100.0)) + addHealHp;
		
		// 데스페라도
		if (pc.hasSkillEffect(L1SkillId.DESPERADO)) {
			
			int 데페공격자레벨 = pc.Desperadolevel;
			
			int 회복악화방어 = pc.getResistance().getHealDefence(); 

			double 저하율 = ((100.0 - 데페공격자레벨) + 회복악화방어) / 100.0;

			저하율 = Math.max(0.0, Math.min(1.0, 저하율));

			healHp = (int) (healHp * 저하율);
		}

		// 폴루트 워터 (회복량 1/2배)
		if (pc.hasSkillEffect(L1SkillId.POLLUTE_WATER)) { 
			healHp /= 2;
		}
			
		// 게일 디버프
		if (pc.hasSkillEffect(L1SkillId.게일디버프)) {
			healHp -= healHp * (Config.게일디버프 * 0.01);
		}
		
		// 혈맹 버프 회복
		if (pc.hasSkillEffect(L1SkillId.혈맹버프회복)) {
			healHp +=  healHp *  (Config.혈맹버프회복 * 0.01);  
		}

		// 몬스터 데스 포션
		if (pc.hasSkillEffect(L1SkillId.PAP_DEATH_PORTION)) {
			pc.receiveDamage(pc, (int) healHp);
		} else {
            pc.healHp((int) healHp);
		}	
		
		// 아이템 차감 처리
		if (getRemove() > 0) {
			if (chargeCount > 0) {
				item.setChargeCount(chargeCount - getRemove());
				pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
			} else {
				pc.getInventory().removeItem(item, getRemove());
			}
		}
		return true;
	}
	

	private void cancelAbsoluteBarrier(L1PcInstance pc) { // 아브소르트바리아의 해제
		if (pc.hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER)) {
			pc.removeSkillEffect(L1SkillId.ABSOLUTE_BARRIER);
			SC_PacketBox.send(pc, SC_PacketBox.UNLIMITED_ICON1, 43, false);
		}
	}
}

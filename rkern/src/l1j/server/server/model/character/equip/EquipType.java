package l1j.server.server.model.character.equip;

import java.util.HashMap;
import java.util.Map;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_EquipmentWindow;

public enum EquipType {
	
	// --- 단일 슬롯 장비들 ---
	HELM(1, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_INDEX_HEML),
	ARMOR(2, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_INDEX_ARMOR),
	T_SHIRT(3, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_INDEX_T),
	CLOAK(4, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_INDEX_CLOAK),
	GLOVE(5, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_INDEX_GLOVE),
	BOOTS(6, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_INDEX_BOOTS),
	SHIELD(7, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_INDEX_SHIELD),
	NECKLACE(8, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_INDEX_NECKLACE),
	BELT(10, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_INDEX_BELT),
	GARDER(13, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_INDEX_SHIELD),
	PAIR(15, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_INDEX_PAIR),       // 각반
	RUN2(28, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_run2),             // 문장
	SHOULD(29, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_INDEX_SHOULD),   // 견갑
	BADGE(30, (inv, pc, item, wj) -> SC_EquipmentWindow.EQUIPMENT_INDEX_BADGE),     // 휘장

	// --- 다중 슬롯 장비들 ---
	RING1(9,  (inv, pc, item, wj) -> inv.updateMultiSlot(inv.slot_ring, item, SC_EquipmentWindow.EQUIPMENT_INDEX_RING1, 1)),
	RING2(11, (inv, pc, item, wj) -> inv.updateMultiSlot(inv.slot_ring, item, SC_EquipmentWindow.EQUIPMENT_INDEX_RING1, 1)),
	EARRING(12, (inv, pc, item, wj) -> inv.updateMultiSlot(inv.slot_earring, item, SC_EquipmentWindow.EQUIPMENT_INDEX_EARRING, SC_EquipmentWindow.EQUIPMENT_INDEX_EARRING)),
	RUNE(14,  (inv, pc, item, wj) -> inv.updateMultiSlot(inv.slot_rune, item, SC_EquipmentWindow.EQUIPMENT_INDEX_RUNE1, 1));

	private final int typeId;
	private final EquipStrategy strategy;
	private static final Map<Integer, EquipType> BY_TYPE = new HashMap<>();

	static {
		for (EquipType e : values()) {
			BY_TYPE.put(e.typeId, e);
		}
	}

	EquipType(int typeId, EquipStrategy strategy) {
		this.typeId = typeId;
		this.strategy = strategy;
	}

	public static EquipType get(int typeId) {
		return BY_TYPE.get(typeId);
	}
	
	public int executeStrategy(L1PcInventory inv, L1PcInstance pc, L1ItemInstance item, boolean worldjoin) {
		return strategy.getSlotIndex(inv, pc, item, worldjoin);
	}

	// 내장 인터페이스
	public interface EquipStrategy {
		int getSlotIndex(L1PcInventory inv, L1PcInstance pc, L1ItemInstance item, boolean worldjoin);
	}
}
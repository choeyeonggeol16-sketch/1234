package l1j.server.server.model.character.equip;

import l1j.server.server.model.network.core.sc.packet.SC_EquipmentWindow;

public enum EquipType {
	
	// --- 단일 슬롯 장비들 ---
	HELM(1, SC_EquipmentWindow.EQUIPMENT_INDEX_HEML),
	ARMOR(2, SC_EquipmentWindow.EQUIPMENT_INDEX_ARMOR),
	T_SHIRT(3, SC_EquipmentWindow.EQUIPMENT_INDEX_T),
	CLOAK(4, SC_EquipmentWindow.EQUIPMENT_INDEX_CLOAK),
	GLOVE(5, SC_EquipmentWindow.EQUIPMENT_INDEX_GLOVE),
	BOOTS(6, SC_EquipmentWindow.EQUIPMENT_INDEX_BOOTS),
	SHIELD(7, SC_EquipmentWindow.EQUIPMENT_INDEX_SHIELD),
	NECKLACE(8, SC_EquipmentWindow.EQUIPMENT_INDEX_NECKLACE),
	BELT(10, SC_EquipmentWindow.EQUIPMENT_INDEX_BELT),
	GARDER(13, SC_EquipmentWindow.EQUIPMENT_INDEX_SHIELD),
	PAIR(15, SC_EquipmentWindow.EQUIPMENT_INDEX_PAIR),       // 각반
	RUN2(28, SC_EquipmentWindow.EQUIPMENT_run2),             // 문장
	SHOULD(29, SC_EquipmentWindow.EQUIPMENT_INDEX_SHOULD),   // 견갑
	BADGE(30, SC_EquipmentWindow.EQUIPMENT_INDEX_BADGE),     // 휘장

	// --- 다중 슬롯 장비들 ---
	RING1(9,  SC_EquipmentWindow.EQUIPMENT_INDEX_RING1),
	RING2(11, SC_EquipmentWindow.EQUIPMENT_INDEX_RING1),
	EARRING(12, SC_EquipmentWindow.EQUIPMENT_INDEX_EARRING),
	RUNE(14,  SC_EquipmentWindow.EQUIPMENT_INDEX_RUNE1);

	private final int typeId;
	private final int windowIndex;
	private static final EquipType[] BY_TYPE = new EquipType[BADGE.typeId + 1];

	static {
		for (EquipType e : values()) {
			BY_TYPE[e.typeId] = e;
		}
	}

	EquipType(int typeId, int windowIndex) {
		this.typeId = typeId;
		this.windowIndex = windowIndex;
	}

	public static EquipType get(int typeId) {
		return typeId > 0 && typeId < BY_TYPE.length ? BY_TYPE[typeId] : null;
	}
	
	public int windowIndex() {
		return windowIndex;
	}
}

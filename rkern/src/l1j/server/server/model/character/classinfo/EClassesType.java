package l1j.server.server.model.character.classinfo;

import java.util.HashMap;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.shared.GameRandom;

public enum EClassesType {
	PRINCE(0),
	KNIGHT(1),
	ELF(2),
	WIZARD(3),
	DARKELF(4),
	DKNIGHT(5),
	WITCH(6),
	WARRIOR(7);
	
	private static final HashMap<Integer, EClassesType> _gfxToClass;
	static{
		_gfxToClass = new HashMap<Integer, EClassesType>(EClassesType.values().length * 2);
		_gfxToClass.put(L1PcInstance.CLASSID_PRINCE, 								PRINCE);
		_gfxToClass.put(L1PcInstance.CLASSID_PRINCESS, 							PRINCE);
		_gfxToClass.put(L1PcInstance.CLASSID_KNIGHT_MALE, 					KNIGHT);
		_gfxToClass.put(L1PcInstance.CLASSID_KNIGHT_FEMALE, 				KNIGHT);
		_gfxToClass.put(L1PcInstance.CLASSID_ELF_MALE, 							ELF);
		_gfxToClass.put(L1PcInstance.CLASSID_ELF_FEMALE, 						ELF);
		_gfxToClass.put(L1PcInstance.CLASSID_WIZARD_MALE, 					WIZARD);
		_gfxToClass.put(L1PcInstance.CLASSID_WIZARD_FEMALE, 				WIZARD);
		_gfxToClass.put(L1PcInstance.CLASSID_DARK_ELF_MALE, 				DARKELF);
		_gfxToClass.put(L1PcInstance.CLASSID_DARK_ELF_FEMALE, 				DARKELF);
		_gfxToClass.put(L1PcInstance.CLASSID_DRAGONKNIGHT_MALE, 		DKNIGHT);
		_gfxToClass.put(L1PcInstance.CLASSID_DRAGONKNIGHT_FEMALE,	DKNIGHT);
		_gfxToClass.put(L1PcInstance.CLASSID_BLACKWIZARD_MALE, 		WITCH);
		_gfxToClass.put(L1PcInstance.CLASSID_BLACKWIZARD_FEMALE, 		WITCH);
		_gfxToClass.put(L1PcInstance.CLASSID_전사_MALE, 						WARRIOR);
		_gfxToClass.put(L1PcInstance.CLASSID_전사_FEMALE, 						WARRIOR);
	}
	
	private final int _type;

	EClassesType(int type) {
		_type = type;
	}
	
	public int toInt(){
		return _type;
	}
	
	public void addBunusStat(L1PcInstance pc, int i) {
		switch (this) {
			case PRINCE, KNIGHT, DARKELF, DKNIGHT, WARRIOR -> pc.AddStatBonusStr(i);
			case ELF -> pc.AddStatBonusDex(i);
			case WIZARD, WITCH -> pc.AddStatBonusInt(i);
		}
		if (pc.getNetConnection() != null) {
			SC_OwnCharStatus.send(pc);
		}
	}
	
	public static EClassesType fromInt(int i){
		EClassesType[] types = EClassesType.values();
		return i < 0 || i >= types.length ? null : types[i];
	}
	
	public static EClassesType fromGfx(int gfx){
		EClassesType t = _gfxToClass.get(gfx);
		return t == null ? PRINCE : t;
	}
	
	public static EClassesType fromRand(){
		EClassesType[] types = EClassesType.values();
		
		return types[GameRandom.next(types.length)];
	}
}

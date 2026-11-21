package l1j.server.server.model.character.classinfo;

import java.util.HashMap;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.classinfo.stat.ClassMainStat;
import l1j.server.server.model.character.classinfo.stat.MainDexStat;
import l1j.server.server.model.character.classinfo.stat.MainIntStat;
import l1j.server.server.model.character.classinfo.stat.MainStrStat;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.shared.GameRandom;
public enum EClassesType {
	PRINCE(0, 	new MainStrStat()),
	KNIGHT(1, 	new MainStrStat()),
	ELF(2, 		new MainDexStat()),
	WIZARD(3, 	new MainIntStat()),
	DARKELF(4, 	new MainStrStat()),
	DKNIGHT(5, 	new MainStrStat()),
	WITCH(6, 	new MainIntStat()),
	WARRIOR(7, 	new MainStrStat());
	
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
	
	private int 			_type;
	private ClassMainStat _mstat;
	EClassesType(int type, ClassMainStat mstat){
		_type 	= type;
		_mstat	= mstat;
	}
	
	public int toInt(){
		return _type;
	}
	
	public void addBunusStat(L1PcInstance pc, int i){
		_mstat.addStat(pc, i);
		if(pc.getNetConnection() != null) {
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

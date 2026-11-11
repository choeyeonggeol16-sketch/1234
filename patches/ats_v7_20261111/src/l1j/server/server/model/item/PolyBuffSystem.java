package l1j.server.server.model.item;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import l1j.server.server.datatables.PolyTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_SystemMessage;

@XmlAccessorType(XmlAccessType.FIELD)
public class PolyBuffSystem {
	private static Logger _log = Logger.getLogger(PolyBuffSystem.class.getName());

	private static final String _path = "./data/xml/Item/PolyBuff.xml";
	private static HashMap<Integer, PolyBuffSystem> _dataMap = new HashMap<Integer, PolyBuffSystem>();

	@XmlAttribute(name = "PolyId")
	private int _polyId;

	@XmlAttribute(name = "BuffInfo")
	private String _buffInfo;
	
	@XmlElement(name = "Effect")
	private Effect _effect;

	public static PolyBuffSystem get(int itemId) {
		return (PolyBuffSystem) _dataMap.get(Integer.valueOf(itemId));
	}

	public int getPolyId() {
		return this._polyId;
	}
	
	public String getBuffInfo() {
		return this._buffInfo;
	}

	public Effect getEffect() {
		return this._effect;
	}

	private boolean init() {
		if (PolyTable.getInstance().getTemplate(getPolyId()) == null) {
			System.out.println("PolyTable 없는 변신입니다 . " + getPolyId());
			return false;
		}

		return true;
	}

	private static void loadXml(HashMap<Integer, PolyBuffSystem> dataMap) {
		try {
			JAXBContext context = JAXBContext.newInstance(new Class[] { ItemList.class });

			Unmarshaller um = context.createUnmarshaller();

			File file = new File(_path);
			ItemList list = (ItemList) um.unmarshal(file);
			for (PolyBuffSystem each : list){
				if (each.init()) {
					dataMap.put(each.getPolyId(), each);
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "./data/xml/Item/PolyBuff.xml load failed.", e);
		}
	}

	public static void load() {
		loadXml(_dataMap);
	}

	public static void reload() {
		HashMap<Integer, PolyBuffSystem> dataMap = new HashMap<Integer, PolyBuffSystem>();
		loadXml(dataMap);
		_dataMap = dataMap;
	}

	public boolean use(L1PcInstance pc, int polyId, int time, int type) {
		if(pc == null)
			return false;
		
		getCastleBuff(pc, polyId, time, this, type);
		
		return true;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Effect {

		@XmlAttribute(name = "Ac")
		private int _ac;

		@XmlAttribute(name = "Str")
		private int _str;

		@XmlAttribute(name = "Con")
		private int _con;

		@XmlAttribute(name = "Dex")
		private int _dex;

		@XmlAttribute(name = "Int")
		private int _int;

		@XmlAttribute(name = "Wis")
		private int _wis;

		@XmlAttribute(name = "Cha")
		private int _cha;

		@XmlAttribute(name = "Hp")
		private int _hp;

		@XmlAttribute(name = "Mp")
		private int _mp;

		@XmlAttribute(name = "Hpr")
		private int _hpr;

		@XmlAttribute(name = "Mpr")
		private int _mpr;

		@XmlAttribute(name = "HprTime")
		private int _hprTime;

		@XmlAttribute(name = "MprTime")
		private int _mprTime;

		@XmlAttribute(name = "Mr")
		private int _mr;

		@XmlAttribute(name = "Sp")
		private int _sp;

		@XmlAttribute(name = "Dmg")
		private int _dmg;

		@XmlAttribute(name = "BowDmg")
		private int _bowDmg;

		@XmlAttribute(name = "Hit")
		private int _hit;

		@XmlAttribute(name = "BowHit")
		private int _bowHit;

		@XmlAttribute(name = "DmgReduction")
		private int _dmgReduction;

		@XmlAttribute(name = "WeightReduction")
		private int _weightReduction;

		@XmlAttribute(name = "Exp")
		private int _expBonus;
		
		@XmlAttribute(name = "regist_technology")
		private int _regist_technology = 0;
		
		@XmlAttribute(name = "technique_hit")
		private int _technique_hit  = 0;
		
		@XmlAttribute(name = "regist_spirit")
		private int _regist_spirit = 0;
		
		@XmlAttribute(name = "spirit_hit")
		private int _spirit_hit = 0;
		
		@XmlAttribute(name = "magic_hit")
		private int _magic_hit = 0;

		@XmlAttribute(name = "PvpDamage")
		private int _pvpDamage;
		
		@XmlAttribute(name = "PvpReduction")
		private int _pvpReduction;

		@XmlAttribute(name = "Er")
		private int _er;

		public int getEr() {
			return _er;
		}
		
		public int getPvpDamage() {
			return _pvpDamage;
		}
		
		public int getPvpReduction() {
			return _pvpReduction;
		}
		
		public int getAc() {
			return this._ac;
		}

		public int getStr() {
			return this._str;
		}

		public int getCon() {
			return this._con;
		}

		public int getDex() {
			return this._dex;
		}

		public int getInt() {
			return this._int;
		}

		public int getWis() {
			return this._wis;
		}

		public int getCha() {
			return this._cha;
		}

		public int getHp() {
			return this._hp;
		}

		public int getMp() {
			return this._mp;
		}

		public int getHpr() {
			return this._hpr;
		}

		public int getMpr() {
			return this._mpr;
		}

		public int getHprTime() {
			return this._hprTime;
		}

		public int getMprTime() {
			return this._mprTime;
		}
		
		public int get_regist_technology() {
			return this._regist_technology;
		}
		
		public int get_technique_hit() {
			return this._technique_hit;
		}
		
		public int get_regist_spirit() {
			return this._regist_spirit;
		}
		
		public int get_spirit_hit() {
			return this._spirit_hit;
		}
		
		public int get_magic_hit() {
			return this._magic_hit;
		}

		public int getMr() {
			return this._mr;
		}

		public int getSp() {
			return this._sp;
		}

		public int getDmg() {
			return this._dmg;
		}

		public int getBowDmg() {
			return this._bowDmg;
		}

		public int getHit() {
			return this._hit;
		}

		public int getBowHit() {
			return this._bowHit;
		}

		public int getDmgReduction() {
			return this._dmgReduction;
		}

		public int getWeightReduction() {
			return this._weightReduction;
		}

		

		public int getExpBonus() {
			return this._expBonus;
		}

	}

	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "ItemEffectList")
	private static class ItemList implements Iterable<PolyBuffSystem> {

		@XmlElement(name = "Item")
		private List<PolyBuffSystem> _list;

		public Iterator<PolyBuffSystem> iterator() {
			return this._list.iterator();
		}
	}

	public void getCastleBuff(L1PcInstance pc, int polyId, int time, PolyBuffSystem cbs, int type) {
		
		int dmg = cbs.getEffect().getDmg();
		if (dmg != 0) {
			pc.addMeleeDmg(type * dmg );
		}

		int hit = cbs.getEffect().getHit();
		if (hit != 0) {
			pc.addMeleeHit(type * hit);
		}

		int bowDmg = cbs.getEffect().getBowDmg();
		if (bowDmg != 0) {
			pc.addRangedDmg(type * bowDmg);
		}

		int bowHit = cbs.getEffect().getBowHit();
		if (bowHit != 0) {
			pc.addRangedHit(type * bowHit);
		}

		int sp = cbs.getEffect().getSp();
		if (sp != 0) {
			pc.addSp(type * sp);
		}

		int regenHp = cbs.getEffect().getHpr();
		if (regenHp != 0) {
			pc.addHpr(type * regenHp);
		}

		int regenMp = cbs.getEffect().getMpr();
		if (regenMp != 0) {
			pc.addMpr(type * regenMp);
		}

		int acBonus = cbs.getEffect().getAc();
		if (acBonus != 0) {
			pc.getAC().addAc(type * acBonus);
		}

		int maxHp = cbs.getEffect().getHp();
		if (maxHp != 0) {
			pc.addMaxHp(type * maxHp);
		}

		int maxMp = cbs.getEffect().getMp();
		if (maxMp != 0) {
			pc.addMaxMp(type * maxMp);
		}

		int reduction = cbs.getEffect().getDmgReduction();
		if (reduction != 0) {
			pc.addDamageReduction(type * reduction);
		}

		int weight = cbs.getEffect().getWeightReduction();
		if (weight != 0) {
			pc.addWeightReduction(type * weight);
		}

		int str = cbs.getEffect().getStr();
		if (str != 0) {
			pc.getAbility().addAddedStr(type * str);
		}

		int dex = cbs.getEffect().getDex();
		if (dex != 0) {
			pc.getAbility().addAddedDex(type * dex);
		}

		int con = cbs.getEffect().getCon();
		if (con != 0) {
			pc.getAbility().addAddedCon(type * con);
		}

		int intel = cbs.getEffect().getInt();
		if (intel != 0) {
			pc.getAbility().addAddedInt(type * intel);
		}

		int wis = cbs.getEffect().getWis();
		if (wis != 0) {
			pc.getAbility().addAddedWis(type * wis);
		}

		int cha = cbs.getEffect().getCha();
		if (cha != 0) {
			pc.getAbility().addAddedCha(type * cha);
		}

		//스턴내성
		int stun = cbs.getEffect().get_regist_technology();
		if (stun != 0) {
			pc.getResistance().addregist_technology(type * stun);
		}
		
		//스턴적중
		int petrifaction = cbs.getEffect().get_technique_hit();
		if (petrifaction != 0) {
			pc.getResistance().addtechnique_hit(type * petrifaction);
		}
		 
		//정령내성
		int sleep = cbs.getEffect().get_regist_spirit();
		if (sleep != 0) {
			pc.getResistance().addregist_spirit(type * sleep);
		}
		 
		//정령적중
		int freeze = cbs.getEffect().get_spirit_hit();
		if (freeze != 0) {
			pc.getResistance().addspirit_hit(type * freeze);
		}
		 
		//마법적중
		int hold = cbs.getEffect().get_magic_hit();
		if (hold != 0) {
			pc.addMagicHit(type * hold);
		}
		
		int exp = cbs.getEffect().getExpBonus();
		if (exp != 0) {
			pc.addExpEmblem(type * exp);
		}
   
		
		int pvp_damage = cbs.getEffect().getPvpDamage();
		if(pvp_damage !=0) {
			pc.addDmgupPvP(type * pvp_damage);
		}
		
		int pvp_reduction = cbs.getEffect().getPvpReduction();
		if(pvp_reduction != 0) {
			pc.addDmgDownPvP(type * pvp_reduction);
		}
		
		
		if(cbs.getBuffInfo() != null){
			if(type > 0) {
				pc.sendPackets(new S_SystemMessage("변신 버프 : " + cbs.getBuffInfo()));
			} else {
				pc.sendPackets(new S_SystemMessage("변신 버프 해제: " + cbs.getBuffInfo()));
			}
		}
		
		 pc.sendPackets(new S_OwnCharStatus(pc));
         pc.sendPackets(new S_OwnCharAttrDef(pc));
		
		if(type == 1) {
			pc.setPolyBuffId(polyId);
			pc.setSkillEffect(SkillId.POLY_BUFF, time * 1000); 
		}
	}
}
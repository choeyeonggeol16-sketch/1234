package l1j.server.server.datatables;

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

import l1j.server.server.model.Instance.ItemInstance;

@XmlAccessorType(XmlAccessType.FIELD)
public class L1EnchantBonus {
	private static Logger _log = Logger.getLogger(L1EnchantBonus.class.getName());
	private static final String _path = "./data/xml/Item/EnchantBonus.xml";
	private static HashMap<Integer, L1EnchantBonus> _dataMap = new HashMap<Integer, L1EnchantBonus>();

	@XmlAttribute(name = "ItemId")
	private int _itemId;

	@XmlElement(name = "Effect")
	private Effect _effect;

	public static L1EnchantBonus get(int id) {
		return (L1EnchantBonus) _dataMap.get(Integer.valueOf(id));
	}

	private int getItemId() {
		return this._itemId;
	}

	private Effect getEffect() {
		return this._effect;
	}

	private boolean init() {
		if (ItemTable.getInstance().getTemplate(getItemId()) == null) {
			System.out.println("존재하지 않는 아이템번호입니다. " + getItemId());

			return false;
		}
		return true;
	}

	private static void loadXml(HashMap<Integer, L1EnchantBonus> dataMap) {
		try {
			JAXBContext context = JAXBContext.newInstance(new Class[] { ItemEffectList.class });

			Unmarshaller um = context.createUnmarshaller();

			File file = new File(_path);
			ItemEffectList list = (ItemEffectList) um.unmarshal(file);

			for (L1EnchantBonus each : list)
				if (each.init()) 
					dataMap.put(Integer.valueOf(each.getItemId()), each);
		} catch (Exception e) {
			_log.log(Level.SEVERE, "./data/xml/Item/EnchantBonus.xmlload failed.", e);
		}
	}

	public static void load() {
		loadXml(_dataMap);

	}

	public static void reload() {

		HashMap<Integer, L1EnchantBonus> dataMap = new HashMap<Integer, L1EnchantBonus>();
		loadXml(dataMap);
		_dataMap = dataMap;

	}

	public int getAc(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result -= ((i - (effect.getLimitLevel() - 1)) * effect.getAc());
				}
			} else {
				result -= i * effect.getAc();
			}
		}

		return result;
	}

	public int getAc(ItemInstance item) {
		return 0;
	}

	public int getStr(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getStr());
				}
			} else {
				result += i * effect.getStr();
			}
		}

		return result;
	}

	public int getDex(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getDex());
				}
			} else {
				result += i * effect.getDex();
			}
		}

		return result;
	}

	public int getCon(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getCon());
				}
			} else {
				result += i * effect.getCon();
			}
		}

		return result;
	}

	public int getInt(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getInt());
				}
			} else {
				result += i * effect.getInt();
			}
		}

		return result;
	}

	public int getWis(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getWis());
				}
			} else {
				result += i * effect.getWis();
			}
		}

		return result;
	}

	public int getCha(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getCha());
				}
			} else {
				result += i * effect.getCha();
			}
		}

		return result;
	}

	public int getHp(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getHp());
				}
			} else {
				result += i * effect.getHp();
			}
		}

		return result;
	}

	public int getHpr(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getHpr());
				}
			} else {
				result += i * effect.getHpr();
			}
		}

		return result;
	}

	public int getMp(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getMp());
				}
			} else {
				result += i * effect.getMp();
			}
		}

		return result;
	}

	public int getMpr(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getMpr());
				}
			} else {
				result += i * effect.getMpr();
			}
		}

		return result;
	}

	public int getMr(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getMr());
				}
			} else {
				result += i * effect.getMr();
			}
		}

		return result;
	}

	public int getSp(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getSp());
				}
			} else {
				result += i * effect.getSp();
			}
		}

		return result;
	}

	public int getHitModifier(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getHitModifier());
				}
			} else {
				result += i * effect.getHitModifier();
			}
		}

		return result;
	}

	public int getDmgModifier(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getDmgModifier());
				}
			} else {
				result += i * effect.getDmgModifier();
			}
		}

		return result;
	}

	public int getBowHitModifier(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getBowHitModifier());
				}
			} else {
				result += i * effect.getBowHitModifier();
			}
		}

		return result;
	}

	public int getBowDmgModifier(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getBowDmgModifier());
				}
			} else {
				result += i * effect.getBowDmgModifier();
			}
		}

		return result;
	}

	public int getWeightReduction(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getWeightReduction());
				}
			} else {
				result += i * effect.getWeightReduction();
			}
		}

		return result;
	}

	public int getDamageReduction(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getDamageReduction());
				}
			} else {
				result += i * effect.getDamageReduction();
			}
		}

		return result;
	}

	public int getDefenseEarth(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getDefenseEarth());
				}
			} else {
				result += i * effect.getDefenseEarth();
			}
		}

		return result;
	}

	public int getDefenseWater(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getDefenseWater());
				}
			} else {
				result += i * effect.getDefenseWater();
			}
		}

		return result;
	}

	public int getDefenseFire(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getDefenseFire());
				}
			} else {
				result += i * effect.getDefenseFire();
			}
		}

		return result;
	}

	public int getDefenseWind(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getDefenseWind());
				}
			} else {
				result += i * effect.getDefenseWind();
			}
		}

		return result;
	}

	public int getExpBonus(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getExpBonus());
				}
			} else {
				result += i * effect.getExpBonus();
			}
		}

		return result;
	}
	
	
	
	public int get_regist_technology(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.get_regist_technology());
				}
			} else {
				result += i * effect.get_regist_technology();
			}
		} 
		return result;
	}
	
	
	public int get_technique_hit(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.get_technique_hit());
				}
			} else {
				result += i * effect.get_technique_hit();
			}
		} 
		return result;
	}
	
	
	public int get_regist_spirit(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.get_regist_spirit());
				}
			} else {
				result += i * effect.get_regist_spirit();
			}
		} 
		return result;
	}
	
	public int get_spirit_hit(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.get_spirit_hit());
				}
			} else {
				result += i * effect.get_spirit_hit();
			}
		} 
		return result;
	}
	
	public int get_magic_hit(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.get_magic_hit());
				}
			} else {
				result += i * effect.get_magic_hit();
			}
		} 
		return result;
	}
	
	
	
	

	public int getReductionCancel(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getReductionCancel());
				}
			} else {
				result += i * effect.getReductionCancel();
			}
		}

		return result;
	}

	public int getPvpDmg(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getPvpDmg());
				}
			} else {
				result += i * effect.getPvpDmg();
			}
		}

		return result;
	}

	public int getPvpReduction(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getPvpReduction());
				}
			} else {
				result += i * effect.getPvpReduction();
			}
		}

		return result;
	}

	public int getEr(int i) {
		int result = 0;
		Effect effect = getEffect();

		if (effect != null) {
			if (effect.getLimitLevel() != 0) {
				if (i >= effect.getLimitLevel()) {
					result += ((i - (effect.getLimitLevel() - 1)) * effect.getEr());
				}
			} else {
				result += i * effect.getEr();
			}
		}

		return result;
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	private static class Effect {

		@XmlAttribute(name = "Ac")
		private int _ac = 0;

		@XmlAttribute(name = "Str")
		private int _str = 0;

		@XmlAttribute(name = "Dex")
		private int _dex = 0;

		@XmlAttribute(name = "Con")
		private int _con = 0;

		@XmlAttribute(name = "Int")
		private int _int = 0;

		@XmlAttribute(name = "Wis")
		private int _wis = 0;

		@XmlAttribute(name = "Cha")
		private int _cha = 0;

		@XmlAttribute(name = "Hp")
		private int _hp = 0;

		@XmlAttribute(name = "Mp")
		private int _mp = 0;

		@XmlAttribute(name = "Hpr")
		private int _hpr = 0;

		@XmlAttribute(name = "Mpr")
		private int _mpr = 0;

		@XmlAttribute(name = "Mr")
		private int _mr;

		@XmlAttribute(name = "Sp")
		private int _sp;

		@XmlAttribute(name = "Hit")
		private int _hit = 0;

		@XmlAttribute(name = "Dmg")
		private int _dmg = 0;

		@XmlAttribute(name = "BowHit")
		private int _bowHit = 0;

		@XmlAttribute(name = "BowDmg")
		private int _bowDmg = 0;

		@XmlAttribute(name = "WeightReduction")
		private int _wr = 0;

		@XmlAttribute(name = "DamageReduction")
		private int _dr = 0;

		@XmlAttribute(name = "Earth")
		private int _earth = 0;

		@XmlAttribute(name = "Water")
		private int _water = 0;

		@XmlAttribute(name = "Fire")
		private int _fire = 0;

		@XmlAttribute(name = "Wind")
		private int _wind = 0;

		@XmlAttribute(name = "Exp")
		private int _exp = 0;
		
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
		
		

		@XmlAttribute(name = "ReductionCancel")
		private int _reductionCancel = 0;

		@XmlAttribute(name = "PvpDmg")
		private int _pvpDmg = 0;

		@XmlAttribute(name = "PvpReduction")
		private int _pvpReduction = 0;

		@XmlAttribute(name = "LimitEnchantLevel")
		private int _limitEnchantLevel = 0;

		@XmlAttribute(name = "Er")
		private int _er = 0;

		public int getEr() {
			return _er;
		}

		public int getLimitLevel() {
			return this._limitEnchantLevel;
		}

		public int getAc() {
			return this._ac;
		}

		public int getStr() {
			return this._str;
		}

		public int getDex() {
			return this._dex;
		}

		public int getCon() {
			return this._con;
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

		public int getMr() {
			return this._mr;
		}

		public int getSp() {
			return this._sp;
		}

		public int getHitModifier() {
			return this._hit;
		}

		public int getDmgModifier() {
			return this._dmg;
		}

		public int getBowHitModifier() {
			return this._bowHit;
		}

		public int getBowDmgModifier() {
			return this._bowDmg;
		}

		public int getWeightReduction() {
			return this._wr;
		}

		public int getDamageReduction() {
			return this._dr;
		}

		public int getDefenseEarth() {
			return this._earth;
		}

		public int getDefenseWater() {
			return this._water;
		}

		public int getDefenseFire() {
			return this._fire;
		}

		public int getDefenseWind() {
			return this._wind;
		}

		public int getExpBonus() {
			return this._exp;
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
		
		
		

		public int getReductionCancel() {
			return this._reductionCancel;
		}

		public int getPvpDmg() {
			return _pvpDmg;
		}

		public int getPvpReduction() {
			return _pvpReduction;
		}
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "ItemEffectList")
	private static class ItemEffectList implements Iterable<L1EnchantBonus> {

		@XmlElement(name = "Item")
		private List<L1EnchantBonus> _list;

		public Iterator<L1EnchantBonus> iterator() {
			return this._list.iterator();
		}
	}
}
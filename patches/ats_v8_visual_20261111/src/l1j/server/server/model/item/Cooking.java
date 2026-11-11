package l1j.server.server.model.item;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import I1j.server.server.model.Thread.MakeItemByCookTimer;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillHaste;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.utils.ItemPresentOutStream;
import l1j.server.server.utils.PerformanceTimer;

@XmlAccessorType(XmlAccessType.FIELD)
public class Cooking {
	private static Logger _log = Logger.getLogger(Cooking.class.getName());

	private static final String _path = "./data/xml/Item/Cooking.xml";
	private static HashMap<Integer, Cooking> _dataMap = new HashMap<Integer, Cooking>();
	private static HashMap<Integer, Cooking> _skillIdMap = new HashMap<Integer, Cooking>();
	private static Timer _timer = new Timer();
	private MakeItemByCookTimer _makeTimer;

	@XmlAttribute(name = "ItemId")
	private int _itemId;

	@XmlAttribute(name = "CookType")
	private int _cookType;

	@XmlAttribute(name = "IconType")
	private int _iconType;

	@XmlAttribute(name = "Remove")
	private int _remove;

	@XmlAttribute(name = "Name")
	private String _name;

	@XmlAttribute(name = "SkillId")
	private int _skillId;

	@XmlAttribute(name = "Time")
	private int _skillTime;

	@XmlElement(name = "Effect")
	private Effect _effect;

	public static Cooking get(int itemId) {
		return (Cooking) _dataMap.get(Integer.valueOf(itemId));
	}
	public static Cooking getBySkillId(int skillId) {
		return (Cooking) _skillIdMap.get(Integer.valueOf(skillId));
	}
	public int getSkillId() {
		return this._skillId;
	}
	public int getSkillTime() {
		return this._skillTime;
	}
	public int getItemId() {
		return this._itemId;
	}
	public int getCookType() {
		return this._cookType;
	}
	public int getIconType() {
		return this._iconType;
	}
	public Effect getEffect() {
		return this._effect;
	}
	public int getRemove() {
		return this._remove;
	}
	private boolean init() {
		if (ItemTable.getInstance().getTemplate(getItemId()) == null) {
			System.out.println("존재하지 않는 아이템번호입니다. " + getItemId());
			return false;
		}
		return true;
	}
	private static void loadXml(HashMap<Integer, Cooking> dataMap, HashMap<Integer, Cooking> skillIdMap) {
		try {
			JAXBContext context = JAXBContext.newInstance(new Class[] { ItemList.class });

			Unmarshaller um = context.createUnmarshaller();

			File file = new File(_path);
			ItemList list = (ItemList) um.unmarshal(file);

			for (Cooking each : list)
				if (each.init()) {
					dataMap.put(Integer.valueOf(each.getItemId()), each);
					skillIdMap.put(Integer.valueOf(each.getSkillId()), each);
				}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "./data/xml/Item/Cooking.xmlload failed.", e);
		}
	}

	public static void load() {
		loadXml(_dataMap, _skillIdMap);
	}

	public static void reload() {
		PerformanceTimer timer = new PerformanceTimer();
		System.out.print("reloading " + _log.getName().substring(_log.getName().lastIndexOf(".") + 1) + "...");

		HashMap<Integer, Cooking> dataMap = new HashMap<Integer, Cooking>();
		HashMap<Integer, Cooking> skillIdMap = new HashMap<Integer, Cooking>();
		loadXml(dataMap, skillIdMap);
		_dataMap = dataMap;
		_skillIdMap = skillIdMap;

		System.out.println("OK! " + timer.elapsedTimeMillis() + "ms");
	}

	public boolean use(L1PcInstance pc, ItemInstance item) {
		int cooking_id = pc.getCookingId();
		int dessert_id = pc.getDessertId();
		int chargeCount = item.getChargeCount();

		if (getCookType() == 2) {
			if (pc.getFood() <= 100) { // 100%
				pc.sendPackets(new S_ServerMessage(3461)); 
				return false;
			}
			if (dessert_id != 0)
				pc.removeSkillEffect(dessert_id);
		} else {
			if (cooking_id != 0) {
				pc.removeSkillEffect(cooking_id);
			}
		}

		Effect effect = getEffect();
		if (effect != null) {

			effectToMaster(pc, this, 1, 0);

			pc.sendPackets(new S_ServerMessage(76, item.getNumberedName(1)));
			if (getRemove() > 0) {
				if (chargeCount > 0) {
					item.setChargeCount(chargeCount - getRemove());
					pc.getInventory().updateItem(item, L1PcInventory.COL_CHARGE_COUNT);
				} else { 
					S_SkillSound ss = S_SkillSound.get(pc.getId(), 6392);
					pc.sendPackets(ss);
					pc.broadcastPacket(ss);
					pc.getInventory().removeItem(item, getRemove());
				}
			}
			return true;
		}
		return false;
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

		@XmlAttribute(name = "DmgChance")
		private int _dmgChance;

		@XmlAttribute(name = "Hit")
		private int _hit;

		@XmlAttribute(name = "BowHit")
		private int _bowHit;

		@XmlAttribute(name = "DmgReduction")
		private int _dmgReduction;

		@XmlAttribute(name = "DmgReductionChance")
		private int _dmgReductionChance;

		@XmlAttribute(name = "DmgEvasionChance")
		private int _dmgEvasionChance;

		@XmlAttribute(name = "WeightReduction")
		private int _weightReduction;

		@XmlAttribute(name = "TechniqueTolerance")
		private int _technique_tolerance = 0;
		
		@XmlAttribute(name = "SpiritTolerance")
		private int _spirit_tolerance = 0;

		@XmlAttribute(name = "DragonlangTolerance")
		private int _dragonlang_tolerance = 0;

		@XmlAttribute(name = "FearTolerance")
		private int _fear_tolerance = 0;

		@XmlAttribute(name = "AllTolerance")
		private int _all_tolerance = 0;

		@XmlAttribute(name = "TechniqueHit")
		private int _technique_hit = 0;

		@XmlAttribute(name = "SpiritHit")
		private int _spirit_hit = 0;

		@XmlAttribute(name = "DragonlangHit")
		private int _dragonlang_hit = 0;

		@XmlAttribute(name = "FearHit")
		private int _fear_hit = 0;

		@XmlAttribute(name = "AllHit")
		private int _all_hit = 0;

		@XmlAttribute(name = "Exp")
		private int _expBonus;

		@XmlAttribute(name = "Make")
		private int _makeItemId;

		@XmlAttribute(name = "MakeTime")
		private int _makeTime;

		@XmlAttribute(name = "FoeDmg")
		private int _foeDmg;

		@XmlAttribute(name = "TitanPercent")
		private int _titanPercent;

		@XmlAttribute(name = "Eva")
		private boolean _isBlessOfEva;

		@XmlAttribute(name = "Haste")
		private boolean _isHaste;

		@XmlAttribute(name = "MagicHit")
		private int _magicHit;

		@XmlAttribute(name = "PvpDmg")
		private int _pvpDmg;

		@XmlAttribute(name = "PvpReduction")
		private int _pvpReduction;

		@XmlAttribute(name = "SkillNameId")
		private String _skillnameId;

		@XmlAttribute(name = "AinEfficiency")
		private int _ainEfficiency;

		@XmlAttribute(name = "Er")
		private int _er;

		@XmlAttribute(name = "FoodVolume")
		private int _foodVolume;

		@XmlAttribute(name = "SkillId")
		private int _skillId;

		@XmlAttribute(name = "ResistanceFire")
		private int _fire;

		@XmlAttribute(name = "ResistanceEarth")
		private int _earth;

		@XmlAttribute(name = "ResistanceWater")
		private int _water;

		@XmlAttribute(name = "ResistanceWind")
		private int _wind;

		@XmlAttribute(name = "ResistanceAll")
		private int _allResistance;

		@XmlAttribute(name = "PvpMagicReduction")
		private int _pvpMagicReduction;

		public int getPvpMagicReduction() {
			return _pvpMagicReduction;
		}

		public int getFire() {
			return this._fire;
		}

		public int getEarth() {
			return this._earth;
		}

		public int getWater() {
			return this._water;
		}

		public int getWind() {
			return this._wind;
		}

		public int getResistanceAll() {
			return this._allResistance;
		}

		public int getSkillId() {
			return this._skillId;
		}
		public int getFoodVolume() {
			return this._foodVolume;
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

		public int getDmgChance() {
			return this._dmgChance;
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

		public int getDmgReductionChance() {
			return this._dmgReductionChance;
		}

		public int getDmgEvasionChance() {
			return this._dmgEvasionChance;
		}

		public int getWeightReduction() {
			return this._weightReduction;
		}

		public int getTechniqueTolerance() {
			return this._technique_tolerance;
		}

		public int getSpiritTolerance() {
			return this._spirit_tolerance;
		}

		public int getDragonLangTolerance() {
			return this._dragonlang_tolerance;
		}

		public int getFearTolerance() {
			return this._fear_tolerance;
		}

		public int getAllTolerance() {
			return this._all_tolerance;
		}

		public int getTechniqueHit() {
			return this._technique_hit;
		}

		public int getSpiritHit() {
			return this._spirit_hit;
		}

		public int getDragonLangHit() {
			return this._dragonlang_hit;
		}

		public int getFearHit() {
			return this._fear_hit;
		}

		public int getAllHit() {
			return this._all_hit;
		}

		public int getExpBonus() {
			return this._expBonus;
		} 


		public int getMakeItemId() {
			return this._makeItemId;
		}

		public int getMakeTime() {
			return this._makeTime;
		}

		public int getFoeDmg() {
			return this._foeDmg;
		}

		public int getTitanPercent() {
			return this._titanPercent;
		}

		public boolean isBlessOfEva() {
			return this._isBlessOfEva;
		}

		public boolean isHaste() {
			return this._isHaste;
		}

		public int getMagicHit() {
			return _magicHit;
		}

		public String getSkillNameId() {
			return _skillnameId;
		}

		public int getAinEfficiency() {
			return _ainEfficiency;
		}

		public int getPvpDmg() {
			return _pvpDmg;
		}

		public int getPvpReduction() {
			return _pvpReduction;
		}

		public int getEr() {
			return _er;
		}
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "ItemEffectList")
	private static class ItemList implements Iterable<Cooking> {

		@XmlElement(name = "Item")
		private List<Cooking> _list;

		public Iterator<Cooking> iterator() {
			return this._list.iterator();
		}
	}

	/**
	 * 요리에 대한 표기 설정 
	 * @param cook
	 * @param item
	 * @return
	 */
	public static byte[] getItemView(Cooking cook, ItemInstance item) {
		ItemPresentOutStream os = new ItemPresentOutStream();
		try {
			int dmg = cook.getEffect().getDmg();
			int dmg_chance = cook.getEffect().getDmgChance();
			if (dmg != 0) {
				if (dmg_chance != 0) {
					os.writeC(103);
					os.writeC(dmg);
				} else {
					os.writeC(6);
					os.writeC(dmg);
				}
			}

			int hit = cook.getEffect().getHit();
			if (hit != 0) {
				os.writeC(5);
				os.writeC(hit);
			}

			int bowDmg = cook.getEffect().getBowDmg();
			if (bowDmg != 0) {
				os.writeC(35);
				os.writeC(bowDmg);
			}

			int bowHit = cook.getEffect().getBowHit();
			if (bowHit != 0) {
				os.writeC(24);
				os.writeC(bowHit);
			} 
			// 불의 속성 
			int getFire = cook.getEffect().getFire();
			if (getFire != 0) {
				os.writeC(27);
				os.writeC(getFire);
			}
			// 물의 속성
			int Water = cook.getEffect().getWater();
			if (Water != 0) {
				os.writeC(28);
				os.writeC(Water);
			}
			// 바람 속성 
			int Wind = cook.getEffect().getWind();
			if (Wind != 0) {
				os.writeC(29);
				os.writeC(Wind);
			}
			// 땅의 속성
			int Earth = cook.getEffect().getEarth();
			if (Earth != 0) {
				os.writeC(30);
				os.writeC(Earth);
			}
			// 모든 속성
			int AllResist = cook.getEffect().getResistanceAll();
			if (AllResist != 0) {
				os.writeC(39);
				os.writeS(String.format("모든 속성 저항 +%d", AllResist));
			}

			int sp = cook.getEffect().getSp();
			if (sp != 0) {
				os.writeC(17);
				os.writeC(sp);
			}

			if (cook.getEffect().isHaste()) {
				os.writeC(18);
			}

			int regenHp = cook.getEffect().getHpr();
			if (regenHp != 0) {
				os.writeC(37);
				os.writeC(regenHp);
			}

			int regenMp = cook.getEffect().getMpr();
			if (regenMp != 0) {
				os.writeC(38);
				os.writeC(regenMp);
			}

			int addMr = cook.getEffect().getMr();
			if (addMr != 0) {
				os.writeC(39);
				os.writeS(String.format("MR %d", addMr));
			}
			
			int acBonus = cook.getEffect().getAc();
			if (acBonus != 0) {
				os.writeC(39);
				os.writeS(String.format("AC %d", acBonus));
			}

			int maxHp = cook.getEffect().getHp();
			if (maxHp != 0) {
				os.writeC(39);
				os.writeS(String.format("최대 HP +%d", maxHp));
			}

			int maxMp = cook.getEffect().getMp();
			if (maxMp != 0) {
				os.writeC(39);
				os.writeS(String.format("최대 MP +%d", maxMp));
			}

			int reduction = cook.getEffect().getDmgReduction();
			if (reduction != 0) {
				os.writeC(39);
				os.writeS(String.format("대미지 감소 +%d", reduction));
			}

			int weight = cook.getEffect().getWeightReduction();
			if (weight != 0) {
				os.writeC(39); 
				os.writeS(String.format("무게증가 +%d", weight));
			}

			int er = cook.getEffect().getEr();
			if (er != 0) {
				os.writeC(39); 
				os.writeS(String.format("ER +%d", er));
			}

			int str = cook.getEffect().getStr();
			if (str != 0) {
				os.writeC(8);
				os.writeC(str);
			}

			int dex = cook.getEffect().getDex();
			if (dex != 0) {
				os.writeC(9);
				os.writeC(dex);
			}

			int con = cook.getEffect().getCon();
			if (con != 0) {
				os.writeC(10);
				os.writeC(con);
			}

			int intel = cook.getEffect().getInt();
			if (intel != 0) {
				os.writeC(12);
				os.writeC(intel);
			}

			int wis = cook.getEffect().getWis();
			if (wis != 0) {
				os.writeC(11);
				os.writeC(wis);
			}

			int cha = cook.getEffect().getCha();
			if (cha != 0) {
				os.writeC(10);
				os.writeC(cha);
			}

			int pvpDmg = cook.getEffect().getPvpDmg();
			if (pvpDmg != 0) {
				os.writeC(39);
				os.writeS(String.format("PVP 추가대미지 +%d", pvpDmg));
			}
			

			int pvpReduction = cook.getEffect().getPvpReduction();
			if (pvpReduction != 0) {
				os.writeC(39);
				os.writeS(String.format("PVP 대미지 감소 +%d", pvpReduction));
			} 

			int exp = cook.getEffect().getExpBonus();
			if (exp != 0) {
				os.writeC(36);
				os.writeC(exp);
			}

			return os.getBytes();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} 
		return null;
	}
	/**
	 * 실제 효과 처리 
	 * @param cha
	 * @param cook
	 * @param type
	 */
	public void effectToMaster(L1Character cha, Cooking cook, int type, int reTime) {

		if (cook != null) {

			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				if (cook.getEffect().getHp() != 0) {
					pc.addMaxHp(type * cook.getEffect().getHp());
				}
				if (cook.getEffect().getHpr() != 0 && cook.getEffect().getHprTime() == 0) {
					pc.addHpr(type * cook.getEffect().getHpr());
				}
				if (cook.getEffect().getMp() != 0) {
					pc.addMaxMp(type * cook.getEffect().getMp()); 
				}
				if (cook.getEffect().getMpr() != 0 && cook.getEffect().getMprTime() == 0) {
					pc.addMpr(type * cook.getEffect().getMpr());
				}
				int add_ac = cook.getEffect().getAc();
				if (add_ac != 0) {
					pc.getAC().addAc(type * add_ac);
				}
				if (cook.getEffect().getStr() != 0) {
					pc.getAbility().addAddedStr(type * cook.getEffect().getStr());
				}
				if (cook.getEffect().getCon() != 0) {
					pc.getAbility().addAddedCon(type * cook.getEffect().getCon());
				}
				if (cook.getEffect().getDex() != 0) {
					pc.getAbility().addAddedDex(type * cook.getEffect().getDex());
				}
				if (cook.getEffect().getCha() != 0)
					pc.getAbility().addAddedCha(type * cook.getEffect().getCha());
				if (cook.getEffect().getInt() != 0) {
					pc.getAbility().addAddedInt(type * cook.getEffect().getInt());
				}
				if (cook.getEffect().getWis() != 0) {
					pc.getAbility().addAddedWis(type * cook.getEffect().getWis());
    			}
				if (cook.getEffect().getDmgChance() == 0) {
					if (cook.getEffect().getDmg() != 0) {
						pc.addMeleeDmg(type * cook.getEffect().getDmg());
					}
				}
				if (cook.getEffect().getBowDmg() != 0) {
					pc.addRangedDmg(type * cook.getEffect().getBowDmg());
				}
				if (cook.getEffect().getHit() != 0) {
					pc.addMeleeHit(type * cook.getEffect().getHit());
				}
				if (cook.getEffect().getBowHit() != 0) {
					pc.addRangedHit(type * cook.getEffect().getBowHit());
				}
				if (cook.getEffect().getMr() != 0) {
					pc.getResistance().addMr(type * cook.getEffect().getMr());
				}
				if (cook.getEffect().getSp() != 0) {
					pc.addSp(type * cook.getEffect().getSp());
				}
				if (cook.getEffect().getDmgReductionChance() == 0) {
					if (cook.getEffect().getDmgReduction() != 0)
						pc.addDamageReduction(type * cook.getEffect().getDmgReduction());
				}
				if (cook.getEffect().getWeightReduction() != 0) {
					pc.addWeightReduction(type * cook.getEffect().getWeightReduction());
				}
				int pvpDmg = cook.getEffect().getPvpDmg();
				if (pvpDmg != 0) {
					pc.addDmgupPvP(type * pvpDmg);
				}
				int pvpReduction = cook.getEffect().getPvpReduction();
				if (pvpReduction != 0) {
					pc.addDmgDownPvP(type * pvpReduction);
				}
				int exp = cook.getEffect().getExpBonus();
				if (exp != 0) {
					pc.addExpEmblem(type * exp); 
				}
				int foodVolume = cook.getEffect().getFoodVolume();
				if (foodVolume != 0) {
					if (pc.getFood() < 225) { // 100% 210039
						int foodvolume = (foodVolume / 10);
						pc.addFood(foodvolume <= 0 ? 5 : foodvolume);
						pc.sendPackets(new S_PacketBox(S_PacketBox.FOOD, pc.getFood()));
					}
				}
				int fire = cook.getEffect().getFire();
				if (fire != 0) {
					pc.getResistance().addFire(type * fire);
				}
				int water = cook.getEffect().getWater();
				if (water != 0) {
					pc.getResistance().addWater(type * water);
				}
				int earth = cook.getEffect().getEarth();
				if (earth != 0) {
					pc.getResistance().addEarth(type * earth);
				}
				int wind = cook.getEffect().getWind();
				if (wind != 0) {
					pc.getResistance().addWind(type * wind);
				}
				int allResistance = cook.getEffect().getResistanceAll();
				if (allResistance != 0) {
					pc.getResistance().addAllNaturalResistance(type * allResistance);
			    }
				if (cook.getEffect().isHaste()) {
					if (type == 1) {
						pc.addHasteItemEquipped(1);
						pc.removeHasteSkillEffect();
						pc.setMoveSpeed(1);
						S_SkillHaste ss = S_SkillHaste.get(pc.getId(), 1, -1);
						pc.sendPackets(ss);
						pc.broadcastPacket(ss);
					} else {
						pc.addHasteItemEquipped(-1);
						if (pc.getHasteItemEquipped() == 0) {
							pc.setMoveSpeed(0);
							S_SkillHaste aa = S_SkillHaste.get(pc.getId(), 0, 0);
							pc.sendPackets(aa);
							pc.broadcastPacket(aa);
						}
					}
				}
				if (type == -1) {
					if(cook.getIconType() != -1) {
						pc.sendPackets(new S_PacketBox(S_PacketBox.ICON_COOKING, pc, cook.getIconType(), 0));
					}
					if (cook.getSkillId() != 0) {
						if(cook.getCookType() == 1) {
							pc.setCookingId(0);
						} else {
							pc.setDessertId(0);
						}
					}
					stopMakeTimer();
				} else {
					if (cook.getIconType() != -1) {
						if (reTime >= 1) {
							pc.sendPackets(new S_PacketBox(S_PacketBox.ICON_COOKING, pc, cook.getIconType(), reTime));
						} else {
							pc.sendPackets(new S_PacketBox(S_PacketBox.ICON_COOKING, pc, cook.getIconType(), cook.getSkillTime()));
						}
					}
					if (cook.getSkillId() != 0) {
						if (reTime >= 1) {
							pc.setSkillEffect(cook.getSkillId(), reTime * 1000);
						} else {
							pc.setSkillEffect(cook.getSkillId(), cook.getSkillTime() * 1000);
						}
						if(cook.getCookType() == 1) {
							pc.setCookingId(cook.getSkillId());
						} else {
							pc.setDessertId(cook.getSkillId());
						}
					}
					pc.sendPackets(new S_OwnCharStatus(pc));
					pc.sendPackets(new S_OwnCharAttrDef(pc));
					startMakeTimer(pc);
				}
			}
		}
	}



	public void startMakeTimer(L1PcInstance pc) {
		if (getEffect().getMakeTime() > 0 && getEffect().getMakeItemId() > 0) {
			int interval = getEffect().getMakeTime() * 1000;

			if (_makeTimer == null) {
				_makeTimer = new MakeItemByCookTimer(pc, this);
				_timer.scheduleAtFixedRate(_makeTimer, interval, interval);
			}
		}
	}

	public void stopMakeTimer() {
		if (this._makeTimer != null) {
			this._makeTimer.cancel();
			this._makeTimer = null;
		}
	}
}
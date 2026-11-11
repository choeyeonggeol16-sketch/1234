package l1j.server.server.model.item;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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

import l1j.server.commons;
import l1j.server.Templates.L1Npc;
import l1j.server.Templates.L1Skills;
import l1j.server.server.ActionCodes;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.Instance.DollInstance;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.random.RandomGenerator;
import l1j.server.server.random.RandomGeneratorFactory;
import l1j.server.server.serverpackets.S_ItemName;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillIconGFX;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.S_UseAttackSkill;
import l1j.server.server.utils.EffectUtil;
import l1j.server.server.utils.ItemPresentOutStream;
import l1j.server.server.utils.MessageUtil;
import l1j.server.server.utils.PerformanceTimer;

@XmlAccessorType(XmlAccessType.FIELD)
public class MagicDoll {
	private static Logger _log = Logger.getLogger(MagicDoll.class.getName());

	private static RandomGenerator _random = RandomGeneratorFactory.newRandom();
	private static final String _path = "./data/xml/Item/MagicDoll.xml";
	private static HashMap<Integer, MagicDoll> _dataMap = new HashMap<Integer, MagicDoll>();

	private static HashMap<Integer, ArrayList<Integer>> _gradeMap = new HashMap<Integer, ArrayList<Integer>>();

	@XmlAttribute(name = "ItemId")
	private int _itemId;

	@XmlElement(name = "Info")
	private Info _info;

	@XmlElement(name = "Effect")
	private Effect _effect;

	public static MagicDoll get(int itemId) {
		return (MagicDoll) _dataMap.get(itemId);
	}
	public static ArrayList<Integer> getGrade(int grade) {
		return (ArrayList<Integer>) _gradeMap.get(grade);
	}
	public int getItemId() {
		return this._itemId;
	}
	public Info getInfo() {
		return this._info;
	}
	public Effect getEffect() {
		return this._effect;
	}
	private boolean init() {
		if (ItemTable.getInstance().getTemplate(getItemId()) == null) {
			System.out.println("존재하지 않는 아이템번호입니다. " + getItemId());
			return false;
		}
		return true;
	}

	private static void loadXml(HashMap<Integer, MagicDoll> dataMap, HashMap<Integer, ArrayList<Integer>> gradeMap) {
		try {
			JAXBContext context = JAXBContext.newInstance(new Class[] { ItemList.class });

			Unmarshaller um = context.createUnmarshaller();

			File file = new File(_path);
			ItemList list = (ItemList) um.unmarshal(file);
			for (MagicDoll each : list) {
				if (each.init()) {
					dataMap.put(Integer.valueOf(each.getItemId()), each);
					ArrayList<Integer> gradeList = gradeMap.get(Integer.valueOf(each.getInfo().getGrade()));
					if (gradeList == null) {
						gradeList = new ArrayList<Integer>();
					}
					gradeList.add(Integer.valueOf(each.getItemId()));
					gradeMap.put(Integer.valueOf(each.getInfo().getGrade()), gradeList);
				}	
			}			
		} catch (Exception e) {
			_log.log(Level.SEVERE, "./data/xml/Item/MagicDoll.xmlload failed.", e);
		}
	}
	public static void load() {
		loadXml(_dataMap, _gradeMap);
	}

	public static void reload() {
		PerformanceTimer timer = new PerformanceTimer();
		HashMap<Integer, MagicDoll> dataMap = new HashMap<Integer, MagicDoll>();
		HashMap<Integer, ArrayList<Integer>> gradeMap = new HashMap<Integer, ArrayList<Integer>>();
		loadXml(dataMap, gradeMap);
		_dataMap = dataMap;
		_gradeMap = gradeMap;
		System.out.println("reloading MagicDoll...OK! " + timer.elapsedTimeMillis() + "ms");
	}

	public boolean use(L1PcInstance pc, ItemInstance item) {
		if (pc.isInvisble()) {
			pc.sendPackets(new S_ServerMessage(MessageUtil.MESSAGE_NUMBER_3), true);
			return false;
		}
		if (!pc.getMap().isRecallPets() || !pc.getMap().isTakePets()) {
			pc.sendPackets(new S_SystemMessage("현재 맵에선 인형을 소환할 수 없습니다."));
			return false;
		}
		int itemObjectId = item.getId();
		Info info = getInfo();
		Effect effect = getEffect();
		if (info != null && effect != null) {
			DollInstance doll = pc.getMagicDoll();
			if (doll != null && doll.getItemObjId() == itemObjectId) {
				doll.deleteDoll(); 
				pc.sendPackets(new S_SkillIconGFX(56, 0));
				return false;
			}
			if (!pc.getInventory().checkItem(41246, info.getCrystalCount())) {
				pc.sendPackets(new S_ServerMessage(337, "$5240"));
				return false;
			}
			if (doll != null) {
				doll.deleteDoll();
				pc.sendPackets(new S_SkillIconGFX(56, 0)); 
			}
			L1Npc template = NpcTable.getInstance().getTemplate(80106);
			template.set_name(info.getName());
			template.set_nameid(info.getNameId());
			int gfxid = info.getCurrentSpriteId(); 
			template.set_gfxid(gfxid);
			doll = new DollInstance(template, pc, item);
			pc.getInventory().consumeItem(ItemId.결정체, info.getCrystalCount());
			pc.sendPackets(new S_SkillIconGFX(56, info.getSummonTime())); 
			pc.sendPackets(new S_ItemName(item)); 
			return true;
		}
		return false;
	}

	public static int getItemIdByName(String name) {
		String dollName = null;
		for (MagicDoll dollItem : _dataMap.values()) {
			dollName = dollItem.getInfo().getName().replaceAll(" ", "");
			if (dollName.contains(name)) {
				return dollItem.getItemId();
			}
		}
		return -1;
	}

	public static double getExpBonus(L1Character _master) {
		double bonus = 1.0D;
		if (_master.getMagicDoll() != null) {
			if (_master.getMagicDoll().getDoll().getEffect().getExpBonus() > 0) {
				bonus += _master.getMagicDoll().getDoll().getEffect().getExpBonus() * 0.01D;
			}
		}
		return bonus;
	}

	public static double getAdenaBonus(L1Character _master) {
		double bonus = 1.0D;
		if (_master.getMagicDoll() != null) {
			if (_master.getMagicDoll().getDoll().getEffect().getAdenaBonus() > 0) {
				bonus += _master.getMagicDoll().getDoll().getEffect().getAdenaBonus() * 0.01D;
			}
		}
		return bonus;
	}

	/**
	 * 늑대인간, 크러스트 시안 확률적 데미지 계산을 위해 만듬 
	 * @param pc
	 * @param target
	 * @return
	 */
	public static int getProbDamageAddByDoll(L1Character pc, L1Character target) {
		int dmg = 0;
		if (pc.getMagicDoll() != null) {
			int prob = pc.getMagicDoll().getDoll().getEffect().getDmgChance();
			DollInstance doll = pc.getMagicDoll();
			if ((prob > 0) && (_random.nextInt(100) <= prob)) {
				dmg = pc.getMagicDoll().getDoll().getEffect().getProbMeleeDmg();
				doll.broadcastPacket(S_SkillSound.get(doll.getId(), EffectUtil.시안늑인_이팩트));
			}
		}
		return dmg;
	}

	public static int useSkillByDoll(L1Character _master, L1Character _target) {
		int dmg = 0;
		if(_master.getMagicDoll() != null) {
			MagicDoll magicDoll = (MagicDoll) _dataMap.get(Integer.valueOf(_master.getMagicDoll().getItemId()));
			if (magicDoll != null) {
				int skillId = magicDoll.getEffect().getSkillId();
				L1Skills skill = SkillsTable.getInstance().getTemplate(skillId);
				DollInstance doll = _master.getMagicDoll();
				if((skillId > 0) && (_random.nextInt(100) <= magicDoll.getEffect().getSkillChance())) {
					dmg = (int) commons.getMagicDollMagicDmg(_master, _target, skill.getAttr(), skill.getDamageMin(), skill.getDamageMax(), skill.getDamageValue()); 
					if (skill.getTarget().equals("attack")) {
						doll.broadcastPacket(S_UseAttackSkill.MagicDoll(doll, _target.getId(), skill.getCastGfx(), _target.getX(), _target.getY(), ActionCodes.ACTION_Attack, dmg, true));
					}
					else if(skill.getTarget().equals("none")) {
						doll.broadcastPacket(S_SkillSound.get(doll.getId(), skill.getCastGfx()));
					}
				}		
			}
		}
		return dmg;
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

		@XmlAttribute(name = "ProbMeleeDmg")
		private int _probMeleeDmg;

		@XmlAttribute(name = "Hit")
		private int _hit;

		@XmlAttribute(name = "BowHit")
		private int _bowHit;

		@XmlAttribute(name = "DmgReduction")
		private int _dmgReduction;

		@XmlAttribute(name = "WeightReduction")
		private int _weightReduction;

		@XmlAttribute(name = "regist_technology")
		private int _regist_technology;
		
		@XmlAttribute(name = "technique_hit")
		private int _technique_hit;
		
		@XmlAttribute(name = "regist_spirit")
		private int _regist_spirit;
		
		@XmlAttribute(name = "regist_All")
		private int _regist_All;
		
		@XmlAttribute(name = "hit_All")
		private int _hit_All;
		
		@XmlAttribute(name = "spirit_hit")
		private int _spirit_hit;
		
		@XmlAttribute(name = "Magichit")
		private int _magic_hit;
		
		@XmlAttribute(name = "Exp")
		private int _expBonus;

		@XmlAttribute(name = "Make")
		private int _makeItemId;

		@XmlAttribute(name = "MakeTime")
		private int _makeTime;

		@XmlAttribute(name = "SkillId")
		private int _skillId;

		@XmlAttribute(name = "SkillChance")
		private int _skillChance;

		@XmlAttribute(name = "Eva")
		private boolean _isBlessOfEva;

		@XmlAttribute(name = "Haste")
		private boolean _isHaste;

		@XmlAttribute(name = "SkillNameId")
		private String _skillnameId;
		
		@XmlAttribute(name = "PvPDamage")
		private int _PvPDamage;

		@XmlAttribute(name = "PvpReduction")
		private int _PvpReduction;

		@XmlAttribute(name = "Adena")
		private int _adenaBonus;

		@XmlAttribute(name = "DmgReductionChance")
		private int _dmgReductionChance;


		@XmlAttribute(name = "DmgChance")
		private int _dmgChance;

		@XmlAttribute(name = "DmgEvasionChance")
		private int _dmgEvasionChance;


		@XmlAttribute(name = "Er")
		private int _er;

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
		public int getProbMeleeDmg() {
			return this._probMeleeDmg;
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
		public int get_regist_technology() {
			return this._regist_technology;
		}

		public int get_technique_hit() {
			return this._technique_hit;
		}
		public int get_regist_All() {
			return this._regist_All;
		}
		public int get_hit_All() {
			return this._hit_All;
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
		public int getExpBonus() {
			return this._expBonus;
		}
		public int getMakeItemId() {
			return this._makeItemId;
		}
		public int getMakeTime() {
			return this._makeTime;
		}
		public int getSkillId() {
			return this._skillId;
		}
		public int getSkillChance() {
			return this._skillChance;
		}
		public boolean isBlessOfEva() {
			return this._isBlessOfEva;
		}
		public boolean isHaste() {
			return this._isHaste;
		}
		public String getSkillNameId() {
			return _skillnameId;
		}
		public int getDmgReductionChance() {
			return this._dmgReductionChance;
		}
		public int getDmgChance() {
			return this._dmgChance;
		}
		public int getDmgEvasionChance() {
			return this._dmgEvasionChance;
		}
		public int getPvPDamage() {
			return this._PvPDamage;
		}
		public int getPvpReduction() {
			return this._PvpReduction;
		}
		public int getAdenaBonus() {
			return this._adenaBonus;
		}
		public int getEr() {
			return _er;
		}
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	public static class Info {

		@XmlAttribute(name = "Name")
		private String _name;

		@XmlAttribute(name = "NameId")
		private String _nameId;

		@XmlAttribute(name = "Time")
		private int _summonTime;

		@XmlAttribute(name = "Crystal")
		private int _crystalCount;

		@XmlAttribute(name = "GfxId")
		private int _gfxId;

		@XmlAttribute(name = "BlessGfxId")
		private int _blessGfxid;

		@XmlAttribute(name = "Grade")
		private int _grade;

		public String getName() {
			return this._name;
		}
		public String getNameId() {
			return this._nameId;
		}
		public int getSummonTime() {
			return this._summonTime;
		}
		public int getCrystalCount() {
			return this._crystalCount;
		}
		public int getCurrentSpriteId() {
			return this._gfxId;
		}
		public int getBlessCurrentSpriteId() {
			return this._blessGfxid;
		}
		public int getGrade() {
			return this._grade;
		}
	}

	@XmlAccessorType(XmlAccessType.FIELD)
	@XmlRootElement(name = "ItemEffectList")
	private static class ItemList implements Iterable<MagicDoll> {

		@XmlElement(name = "Item")
		private List<MagicDoll> _list;

		public Iterator<MagicDoll> iterator() {
			return this._list.iterator();
		}
	}

	public static byte[] getItemView(MagicDoll magicDoll, ItemInstance item) {
		ItemPresentOutStream os = new ItemPresentOutStream();
		try {
			int 일정확률추가데미지 = magicDoll.getEffect().getProbMeleeDmg();
			if (일정확률추가데미지 != 0) {
				os.writeC(39);
				os.writeS(String.format("확률적 추가 데미지 +%d", 일정확률추가데미지));
			}
			int dmg = magicDoll.getEffect().getDmg();
			if (dmg != 0) {
				os.writeMeleeDmg(dmg);
			}		
			int hit = magicDoll.getEffect().getHit();
			if (hit != 0) {
				os.writeMeleeHit(hit);
			}		
			int bowDmg = magicDoll.getEffect().getBowDmg();
			if (bowDmg != 0) {
				os.writeRagendDmg(bowDmg);
			}	
			int bowHit = magicDoll.getEffect().getBowHit();
			if (bowHit != 0) {
				os.writeRagendHit(bowHit);
			}
			int sp = magicDoll.getEffect().getSp();
			if (sp != 0) {
				os.writeAddSP(sp);
			}
			if (magicDoll.getEffect().isHaste()) {
				os.writeHaste();
			}		
			int acBonus = magicDoll.getEffect().getAc();
			if (acBonus != 0) {
				os.writeC(39);
				os.writeS(String.format("추가 방어력 +%d", acBonus * -1));
			}
			int regenHp = magicDoll.getEffect().getHpr();
			int 절대회복피틱 = magicDoll.getEffect().getHprTime();
			if (절대회복피틱 != 0 && regenHp != 0) {
				os.writeC(39);
				os.writeS(String.format("HP절대 회복 +"+regenHp+"("+절대회복피틱+"초)"));
			} else {
				int regenHp2 = magicDoll.getEffect().getHpr();
				if (regenHp2 != 0) {
					os.writeAddHPPrecovery(regenHp2);
				}
			}
			int regenMp = magicDoll.getEffect().getMpr();
			int 절대회복엠틱 = magicDoll.getEffect().getMprTime();
			if (절대회복엠틱 != 0 && regenMp != 0) {
				os.writeC(39);
				os.writeS(String.format("MP절대 회복 +"+regenMp+"("+절대회복엠틱+"초)"));
			} else {
				int regenMp2 = magicDoll.getEffect().getMpr();
				if (regenMp2 != 0) {
					os.writeAddMPPrecovery(regenMp2);
				}
			}
			int maxHp = magicDoll.getEffect().getHp();
			if (maxHp != 0) {
				os.writeAddMaxHP(maxHp);
			}
			int maxMp = magicDoll.getEffect().getMp();
			if (maxMp != 0) {
				os.writeMaxMP(maxMp);
			}
			
			if (magicDoll.getEffect().get_magic_hit() != 0) {
				os.writeC(39);
				os.writeS("마법 적중 +" + magicDoll.getEffect().get_magic_hit());
			}
			// pvp 추가 데미지
			int pvpDmg = magicDoll.getEffect().getPvPDamage();
			if (pvpDmg != 0) {
				os.writeC(39);
				os.writeS(String.format("PVP 추가 대미지 +%d", pvpDmg));
			}
			// pvp 대미지 감소 
			int pvpDmgReduc = magicDoll.getEffect().getPvpReduction();
			if (pvpDmgReduc != 0) {
				os.writeC(39);
				os.writeS(String.format("PVP 대미지 감소 +%d", pvpDmgReduc));
			}		
			// 전체 적중
			int hit_All = magicDoll.getEffect().get_hit_All();
			if (hit_All != 0) {
				os.writeC(39);
				os.writeS(String.format("적중: 전체 +%d", hit_All));
			}		
			// 전체 내성
			int regist_all = magicDoll.getEffect().get_regist_All();
			if (regist_all != 0) {
				os.writeC(39);
				os.writeS(String.format("내성: 전체 +%d", regist_all));
			}
			//스턴내성
			int regist_technology = magicDoll.getEffect().get_regist_technology();
			if (regist_technology != 0) {
				os.writeC(39);
				os.writeS(String.format("스턴 내성 +%d", regist_technology));
			}
			//스턴적중
			int technique_hit = magicDoll.getEffect().get_technique_hit();
			if (technique_hit != 0) {
				os.writeC(39);
				os.writeS(String.format("스턴 적중 +%d", technique_hit));
			}
			//정령내성
			int regist_spirit = magicDoll.getEffect().get_regist_spirit();
			if (regist_spirit != 0) {
				os.writeC(39);
				os.writeS(String.format("정령 내성 +%d", regist_spirit));
			}
			//정령적중
			int spirit_hit = magicDoll.getEffect().get_spirit_hit();
			if (spirit_hit != 0) {
				os.writeC(39);
				os.writeS(String.format("정령 적중 +%d", spirit_hit));
			}
			//마법적중

			int reduction = magicDoll.getEffect().getDmgReduction();
			if (reduction != 0) {
				os.writeC(39);
				os.writeS(String.format("대미지 감소 +%d", reduction));
			}
			int weight = magicDoll.getEffect().getWeightReduction();
			if (weight != 0) {
				os.writeC(39);
				os.writeS(String.format("소지 무게 증가 +%d", weight));
			}
			int str = magicDoll.getEffect().getStr();
			if (str != 0) {
				os.writeaStr(str);
			}
			int dex = magicDoll.getEffect().getDex();
			if (dex != 0) {
				os.writeaDex(dex);
			}
			int con = magicDoll.getEffect().getCon();
			if (con != 0) {
				os.writeaCon(con);
			}
			int intel = magicDoll.getEffect().getInt();
			if (intel != 0) {
				os.writeaInt(intel);
			}
			int wis = magicDoll.getEffect().getWis();
			if (wis != 0) {
				os.writeaWis(wis);
			}
			int cha = magicDoll.getEffect().getCha();
			if (cha != 0) {
				os.writeaCha(cha);
			}
			int exp = magicDoll.getEffect().getExpBonus();
			if (exp != 0) {
				os.writeC(39);
				os.writeS(String.format("EXP +%d", exp));
			}
			int skillId = magicDoll.getEffect().getSkillId();
			if (skillId > 0) {
				os.writeC(39);
				os.writeS("발동: "+ magicDoll.getEffect().getSkillNameId());
			}		
			int Adena = magicDoll.getEffect().getAdenaBonus();
			if (Adena > 0) {
				os.writeC(39);
				os.writeS("아데나 드랍율 " + Adena +"%");
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
}